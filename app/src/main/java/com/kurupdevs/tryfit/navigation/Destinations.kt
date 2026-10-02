package com.kurupdevs.tryfit.navigation

/**
 * All navigation routes for TryFit. String routes (no codegen) keep Phase 0
 * dependency-light; type-safe routes can be adopted in a later phase.
 */
object TryFitRoutes {
    const val ONBOARDING = "onboarding"

    // Bottom-nav tabs
    const val HOME = "home"
    const val TRY_ON = "tryon"
    const val ASSISTANT = "assistant" // center AI orb
    const val WARDROBE = "wardrobe"
    const val SETTINGS = "settings"

    // Push routes (floating nav hidden)
    const val PRODUCT_DETAIL = "product/{productId}"
    // Try-on flow entry contract (Worker E): optional pre-selected catalog
    // product, screenshot-garment entry, or notification result restore.
    const val TRY_ON_FLOW =
        "tryonFlow?productId={productId}&screenshot={screenshot}&resultSessionId={resultSessionId}"
    const val NOTIFICATIONS = "notifications"
    const val BODY_PROFILE = "bodyProfile"
    // Notification deep-link target: restores a finished try-on session.
    // NavHost route for NotificationsRepository deep-links "tryonResult/<sessionId>".
    const val TRY_ON_RESULT = "tryonResult/{sessionId}"

    fun productDetail(productId: String): String = "product/$productId"

    fun tryOnResult(sessionId: String): String = "tryonResult/$sessionId"

    fun tryOnFlow(
        productId: String? = null,
        screenshot: Boolean = false,
        resultSessionId: String? = null
    ): String {
        val params = buildList {
            if (!productId.isNullOrBlank()) add("productId=$productId")
            if (screenshot) add("screenshot=true")
            if (!resultSessionId.isNullOrBlank()) add("resultSessionId=$resultSessionId")
        }
        return if (params.isEmpty()) "tryonFlow" else "tryonFlow?" + params.joinToString("&")
    }

    /**
     * Multi-garment deep link (stylist looks, "Styled for You", "Style with my
     * stuff"). The flow pre-selects the hero piece; remaining pieces are added
     * at the garment-confirm step.
     */
    fun tryOnLook(productIds: List<String>): String =
        tryOnFlow(productId = productIds.firstOrNull())
}

/** Routes that show the floating bottom nav. */
val TabRoutes: Set<String> = setOf(
    TryFitRoutes.HOME,
    TryFitRoutes.TRY_ON,
    TryFitRoutes.ASSISTANT,
    TryFitRoutes.WARDROBE,
    TryFitRoutes.SETTINGS
)

/** Ordered bottom-nav tabs. */
enum class BottomTab(val route: String) {
    HOME(TryFitRoutes.HOME),
    TRY_ON(TryFitRoutes.TRY_ON),
    ASSISTANT(TryFitRoutes.ASSISTANT), // rendered as the center AI orb
    WARDROBE(TryFitRoutes.WARDROBE),
    SETTINGS(TryFitRoutes.SETTINGS);

    companion object {
        fun fromRoute(route: String?): BottomTab =
            entries.firstOrNull { it.route == route } ?: HOME
    }
}
