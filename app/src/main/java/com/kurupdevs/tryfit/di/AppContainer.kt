package com.kurupdevs.tryfit.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import com.kurupdevs.tryfit.BuildConfig
import com.kurupdevs.tryfit.data.AvatarStore
import com.kurupdevs.tryfit.data.BodyProfileStore
import com.kurupdevs.tryfit.data.NotificationsRepository
import com.kurupdevs.tryfit.data.PhotoJanitor
import com.kurupdevs.tryfit.data.PushTokenRegistrar
import com.kurupdevs.tryfit.data.SettingsStore
import com.kurupdevs.tryfit.data.SharedLooksRepository
import com.kurupdevs.tryfit.data.TryOnHistoryRepository
import com.kurupdevs.tryfit.data.WardrobeRepository
import com.kurupdevs.tryfit.data.catalog.CatalogStore
import com.kurupdevs.tryfit.data.catalog.ProductRepository
import com.kurupdevs.tryfit.data.closet.ClosetRepository
import com.kurupdevs.tryfit.data.db.TryFitDatabase
import com.kurupdevs.tryfit.data.favorites.PriceAlertStore
import com.kurupdevs.tryfit.data.stylist.ChatStore
import com.kurupdevs.tryfit.data.stylist.StylistBrain
import com.kurupdevs.tryfit.data.wear.WearStore
import com.kurupdevs.tryfit.data.NoOpPushTokenRegistrar
import com.kurupdevs.tryfit.data.SupabaseClientFactory
import com.kurupdevs.tryfit.tryon.AndroidDemoCompositor
import com.kurupdevs.tryfit.tryon.AndroidOutfitCompositor
import com.kurupdevs.tryfit.tryon.DemoTryOnEngine
import com.kurupdevs.tryfit.tryon.GarmentSlot
import com.kurupdevs.tryfit.tryon.PhotoAnalyzer
import com.kurupdevs.tryfit.tryon.StubPhotoAnalyzer
import com.kurupdevs.tryfit.tryon.loadGarmentBytes
import com.kurupdevs.tryfit.video.MediaCodecTransitionVideoGenerator
import com.kurupdevs.tryfit.video.TransitionVideoGenerator
import com.kurupdevs.tryfit.data.store.appDataStore
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import java.util.concurrent.atomic.AtomicReference

/**
 * Manual dependency injection container (no Hilt — lean).
 *
 * Owned by [com.kurupdevs.tryfit.TryFitApplication]; screens receive what
 * they need via constructor/ViewModel parameters, never via statics.
 */
class AppContainer(private val context: Context) {

    /** App preferences (onboarding flag, auth session cache, settings). */
    val prefs: DataStore<Preferences> get() = context.appDataStore

    // ------------------------------------------------------------------
    // Backend
    // ------------------------------------------------------------------

    /**
     * Supabase client. Lazily built from BuildConfig values injected from
     * local.properties. Throws a clear error if the developer has not
     * configured them yet — backend calls are a Phase 2+ concern.
     */
    val supabase: SupabaseClient by lazy {
        SupabaseClientFactory.create(
            url = BuildConfig.SUPABASE_URL,
            anonKey = BuildConfig.SUPABASE_ANON_KEY
        )
    }

    /**
     * Null-safe Supabase accessor for offline-first repositories. Null when
     * the backend is unconfigured OR any call throws — repositories treat
     * null as "offline" and keep the local cache authoritative.
     */
    val supabaseOrNull: SupabaseClient? by lazy {
        runCatching { supabase }.getOrNull()
    }

    /** Current auth UID, or null for guests / unconfigured backend. */
    val currentUserId: suspend () -> String? = {
        runCatching { supabaseOrNull?.auth?.currentUserOrNull()?.id }.getOrNull()
    }

    // ------------------------------------------------------------------
    // Local persistence
    // ------------------------------------------------------------------

    val database: TryFitDatabase by lazy {
        Room.databaseBuilder(context, TryFitDatabase::class.java, "tryfit.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    val settingsStore: SettingsStore by lazy { SettingsStore(prefs) }
    val avatarStore: AvatarStore by lazy { AvatarStore(context, prefs) }
    val bodyProfileStore: BodyProfileStore by lazy {
        BodyProfileStore(prefs, ::supabaseOrNull, currentUserId)
    }
    val wardrobeRepository: WardrobeRepository by lazy {
        WardrobeRepository(database, ::supabaseOrNull, currentUserId)
    }
    val historyRepository: TryOnHistoryRepository by lazy {
        TryOnHistoryRepository(database)
    }
    val notificationsRepository: NotificationsRepository by lazy {
        NotificationsRepository(database)
    }
    val sharedLooks: SharedLooksRepository by lazy {
        SharedLooksRepository(::supabaseOrNull, currentUserId)
    }

    // ------------------------------------------------------------------
    // Catalog + try-on engine
    // ------------------------------------------------------------------

    val products: ProductRepository by lazy { ProductRepository(context) }

    /** Reactive storefront facade: category Flow, search, pull-to-refresh. */
    val catalogStore: CatalogStore by lazy { CatalogStore(products) }

    // ------------------------------------------------------------------
    // Worker F: AI stylist + growth (chat, price alerts, closet, wear)
    // ------------------------------------------------------------------

    /** Rule-based local stylist brain (no network). */
    val stylist: StylistBrain by lazy { StylistBrain(products = { products.all() }) }

    /** AI chat history (local mirror of the ai_chats table). */
    val chatStore: ChatStore by lazy { ChatStore(context) }

    /** Favorites + "ping me under ₹X" price targets. */
    val priceAlerts: PriceAlertStore by lazy { PriceAlertStore(context) }

    /** User's own digitized garments (app-private photos). */
    val closet: ClosetRepository by lazy { ClosetRepository(context) }

    /** Cost-per-wear + worn-date calendar. */
    val wear: WearStore by lazy { WearStore(context) }

    /**
     * On-device photo analysis. [StubPhotoAnalyzer] until the ML Kit
     * implementation lands — see PhotoAnalyzer.kt for the seam.
     */
    val photoAnalyzer: PhotoAnalyzer by lazy { StubPhotoAnalyzer() }

    /**
     * Pending multi-garment outfit for the NEXT session started through
     * [tryOnEngine]. The demo compositor below consumes-and-clears it, so a
     * full outfit (top + bottom + shoes, research v1.5 item 20) renders in
     * one pipeline run. Single-garment sessions leave it null and take the
     * normal path. (Demo-only seam; the commercial path will send N garment
     * URLs to a multi-garment provider.)
     */
    val pendingOutfit: AtomicReference<List<Pair<ByteArray, GarmentSlot>>?> =
        AtomicReference(null)

    /**
     * EngineProvider: builds the demo engine wired to the Android bitmap
     * compositor + drawable-backed garment provider.
     */
    val tryOnEngine: DemoTryOnEngine by lazy {
        DemoTryOnEngine(
            demoCompositor = { photo, garment ->
                val outfit = pendingOutfit.getAndSet(null)
                if (outfit != null && outfit.size > 1) {
                    AndroidOutfitCompositor(photo, outfit)
                } else {
                    AndroidDemoCompositor(photo, garment)
                }
            },
            garmentProvider = { productId ->
                loadGarmentBytes(context, products, productId)
            },
        )
    }

    /**
     * On-device transition-video generator (FEATURE-RESEARCH §0 — the viral
     * hook). Real MediaCodec implementation; [shareVideo] shares the MP4.
     */
    val transitionVideo: TransitionVideoGenerator by lazy {
        MediaCodecTransitionVideoGenerator()
    }

    /** Push registration seam (FCM wiring lands with google-services). */
    val pushRegistrar: PushTokenRegistrar by lazy { NoOpPushTokenRegistrar() }

    // ------------------------------------------------------------------
    // Images
    // ------------------------------------------------------------------

    /**
     * Shared Coil 3 image loader — SPEC §5: 25% app-memory cache + 250MB
     * disk cache, explicit per-view .size() at call sites (never here).
     */
    val imageLoader: ImageLoader by lazy {
        val loader = ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .build()
        SingletonImageLoader.setSafe { loader }
        loader
    }

    /** Runs the raw-upload auto-delete janitor (research §3). Call on start. */
    suspend fun runPhotoJanitor() {
        val days = runCatching { settingsStore.current().autoDeleteDays }.getOrDefault(7)
        PhotoJanitor.runCleanup(context, days)
    }
}
