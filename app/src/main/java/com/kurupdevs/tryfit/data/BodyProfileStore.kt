package com.kurupdevs.tryfit.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

private val KEY_HEIGHT_CM = intPreferencesKey("body_height_cm")
private val KEY_BUILD = stringPreferencesKey("body_build")
private val KEY_CHEST_CM = intPreferencesKey("body_chest_cm")
private val KEY_WAIST_CM = intPreferencesKey("body_waist_cm")
private val KEY_FIT_INTENT = stringPreferencesKey("body_fit_intent")

/** How the user likes their fit (research v1.5 item 25 — kept VISUALLY SEPARATE from the render). */
enum class FitIntent(val label: String) {
    SLIM("Slim"), REGULAR("Regular"), OVERSIZED("Oversized");

    companion object {
        fun from(raw: String?): FitIntent =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: REGULAR
    }
}

/** Body build options. No other attributes are ever asked (bloat-trap #7). */
enum class BodyBuild(val label: String) {
    SLIM("Slim"), ATHLETIC("Athletic"), AVERAGE("Average"), PLUS("Plus");

    companion object {
        fun from(raw: String?): BodyBuild =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: AVERAGE
    }
}

data class BodyProfile(
    val heightCm: Int? = null,
    val build: BodyBuild = BodyBuild.AVERAGE,
    val chestCm: Int? = null,
    val waistCm: Int? = null,
    val fitIntent: FitIntent = FitIntent.REGULAR,
) {
    val isEmpty: Boolean get() = heightCm == null && chestCm == null && waistCm == null
}

/**
 * Body profile store: local DataStore + best-effort write-through to
 * `users.body_profile` (jsonb, SPEC §6).
 *
 * HONESTY RULE (bloat-trap #1): this profile only "helps size suggestions".
 * It is NEVER presented as a fit guarantee, and the UI labels it as such.
 */
class BodyProfileStore(
    private val prefs: DataStore<Preferences>,
    private val supabase: () -> SupabaseClient?,
    private val currentUserId: suspend () -> String?,
) {

    val profile: Flow<BodyProfile> = prefs.data.map { p ->
        BodyProfile(
            heightCm = p[KEY_HEIGHT_CM]?.takeIf { it > 0 },
            build = BodyBuild.from(p[KEY_BUILD]),
            chestCm = p[KEY_CHEST_CM]?.takeIf { it > 0 },
            waistCm = p[KEY_WAIST_CM]?.takeIf { it > 0 },
            fitIntent = FitIntent.from(p[KEY_FIT_INTENT]),
        )
    }

    suspend fun current(): BodyProfile = profile.first()

    suspend fun save(profile: BodyProfile) {
        prefs.edit {
            if (profile.heightCm != null) it[KEY_HEIGHT_CM] = profile.heightCm else it.remove(KEY_HEIGHT_CM)
            it[KEY_BUILD] = profile.build.name
            if (profile.chestCm != null) it[KEY_CHEST_CM] = profile.chestCm else it.remove(KEY_CHEST_CM)
            if (profile.waistCm != null) it[KEY_WAIST_CM] = profile.waistCm else it.remove(KEY_WAIST_CM)
            it[KEY_FIT_INTENT] = profile.fitIntent.name
        }
        syncToServer(profile)
    }

    suspend fun clear() {
        prefs.edit {
            it.remove(KEY_HEIGHT_CM); it.remove(KEY_BUILD)
            it.remove(KEY_CHEST_CM); it.remove(KEY_WAIST_CM); it.remove(KEY_FIT_INTENT)
        }
    }

    private suspend fun syncToServer(profile: BodyProfile) {
        val client = supabase() ?: return
        val uid = currentUserId() ?: return
        try {
            client.from("users").upsert(
                BodyProfileRow(
                    id = uid,
                    body_profile = BodyProfilePayload(
                        heightCm = profile.heightCm,
                        build = profile.build.name,
                        chestCm = profile.chestCm,
                        waistCm = profile.waistCm,
                        fitIntent = profile.fitIntent.name,
                    ),
                )
            )
        } catch (_: Exception) {
            // Offline-first: local copy is the source of truth until sync.
        }
    }

    @Serializable
    private data class BodyProfileRow(val id: String, val body_profile: BodyProfilePayload)

    @Serializable
    private data class BodyProfilePayload(
        val heightCm: Int?,
        val build: String,
        val chestCm: Int?,
        val waistCm: Int?,
        val fitIntent: String,
    )
}
