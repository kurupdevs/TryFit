package com.kurupdevs.tryfit

import android.app.Application
import com.kurupdevs.tryfit.di.AppContainer
import com.kurupdevs.tryfit.worker.pricedrop.PriceDropNotifier
import com.kurupdevs.tryfit.worker.pricedrop.PriceDropWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * TryFit application. Holds the manual [AppContainer] (no Hilt — lean DI).
 */
class TryFitApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Raw-upload auto-delete (research §3 trust playbook): purge uploads
        // older than the user's auto-delete window (default 7 days).
        appScope.launch {
            try {
                container.runPhotoJanitor()
            } catch (_: Exception) {
                // best-effort; janitor failures must never crash startup
            }
        }
        // Best-effort wardrobe sync so the grid reflects other devices.
        appScope.launch {
            try {
                container.wardrobeRepository.syncFromServer()
            } catch (_: Exception) {
                // best-effort; sync failures must never crash startup
            }
        }
        // Price-drop alerts (Worker F): notification channel + 24h periodic check.
        PriceDropNotifier.createChannel(this)
        PriceDropWorker.schedule(this)
    }
}
