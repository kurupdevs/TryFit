package com.kurupdevs.tryfit.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

/**
 * Builds the shared Supabase client.
 *
 * Phase 0 skeleton: client construction only, no network calls yet.
 * URL + anon key come from BuildConfig (injected from local.properties at
 * build time) — they are never hardcoded and never committed.
 *
 * Backend work (auth, Postgrest queries, Storage uploads, realtime session
 * subscriptions, Edge Function calls for createTryOnSession etc.) lands in
 * Phase 2/3 via repository classes built on this client.
 */
object SupabaseClientFactory {

    fun create(url: String, anonKey: String): SupabaseClient {
        require(url.isNotBlank() && anonKey.isNotBlank()) {
            "Supabase is not configured. Copy local.properties.example to " +
                "local.properties and set supabase.url / supabase.anonKey."
        }
        return createSupabaseClient(
            supabaseUrl = url,
            supabaseKey = anonKey
        ) {
            install(Auth)       // Google sign-in + anonymous upgrade (Phase 2)
            install(Postgrest)  // products, wardrobe_items, notifications (Phase 1/2)
            install(Storage)    // user-uploads, tryon-results, avatars (Phase 3)
            install(Realtime)   // tryon_sessions status subscription (Phase 3)
            install(Functions)  // createTryOnSession, askStylist, ... (Phase 3/4)
        }
    }
}
