package com.kurupdevs.tryfit.worker.pricedrop

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kurupdevs.tryfit.data.catalog.ProductRepository
import com.kurupdevs.tryfit.data.favorites.PriceAlertStore
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * 24h periodic price check: compares catalog prices against each favorite's
 * target ("ping me under ₹999") and posts a local notification on a drop.
 * Fires once per drop (notifiedAt guard) — no repeat pings.
 */
class PriceDropWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val repo = ProductRepository(applicationContext)
            val store = PriceAlertStore(applicationContext)
            val products = repo.all().associateBy { it.id }
            val favorites = store.favorites.first()

            for (fav in favorites) {
                val target = fav.targetInr ?: continue
                val product = products[fav.productId] ?: continue
                if (product.priceInr < target && fav.notifiedAtInr != product.priceInr) {
                    PriceDropNotifier.notifyDrop(
                        applicationContext,
                        product.id,
                        "${product.brand} ${product.name}",
                        product.priceInr,
                        target
                    )
                    store.markNotified(product.id, product.priceInr)
                }
            }
            Result.success()
        } catch (e: Exception) {
            // Transient failure (e.g. assets read) — retry on next 24h tick.
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "tryfit_price_drop_check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PriceDropWorker>(24, TimeUnit.HOURS)
                .addTag("price-drops")
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
