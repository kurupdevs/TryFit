package com.kurupdevs.tryfit.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val KEY_USER_NAME = stringPreferencesKey("user_name")
private val KEY_LANGUAGE = stringPreferencesKey("language") // "en" | "hi"
private val KEY_REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
private val KEY_HAPTICS = booleanPreferencesKey("haptics_enabled")
private val KEY_AUTO_DELETE_DAYS = intPreferencesKey("auto_delete_days") // 7 | 30
private val KEY_CAMERA_CONSENT = booleanPreferencesKey("camera_privacy_consent")

/** All user-facing preferences. Single DataStore, observable, offline-first. */
data class TryFitSettings(
    val userName: String = "",
    /** "en" or "hi". Full Hindi string localization is a later phase; the
     *  setting is stored now so the UI can switch the moment strings land. */
    val language: String = "en",
    val reducedMotion: Boolean = false,
    val hapticsEnabled: Boolean = true,
    /** Raw-upload auto-delete window (research §3: Genlook playbook = 7d). */
    val autoDeleteDays: Int = 7,
    /** Privacy consent sheet accepted before first camera use (research §3). */
    val cameraConsentGiven: Boolean = false,
)

class SettingsStore(private val prefs: DataStore<Preferences>) {

    val settings: Flow<TryFitSettings> = prefs.data.map { p ->
        TryFitSettings(
            userName = p[KEY_USER_NAME].orEmpty(),
            language = p[KEY_LANGUAGE] ?: "en",
            reducedMotion = p[KEY_REDUCED_MOTION] == true,
            hapticsEnabled = p[KEY_HAPTICS] != false,
            autoDeleteDays = (p[KEY_AUTO_DELETE_DAYS] ?: 7).let { if (it == 30) 30 else 7 },
            cameraConsentGiven = p[KEY_CAMERA_CONSENT] == true,
        )
    }

    suspend fun current(): TryFitSettings = settings.first()

    suspend fun setUserName(name: String) {
        prefs.edit { it[KEY_USER_NAME] = name.take(60) }
    }

    suspend fun setLanguage(lang: String) {
        prefs.edit { it[KEY_LANGUAGE] = if (lang == "hi") "hi" else "en" }
    }

    suspend fun setReducedMotion(enabled: Boolean) {
        prefs.edit { it[KEY_REDUCED_MOTION] = enabled }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        prefs.edit { it[KEY_HAPTICS] = enabled }
    }

    suspend fun setAutoDeleteDays(days: Int) {
        prefs.edit { it[KEY_AUTO_DELETE_DAYS] = if (days == 30) 30 else 7 }
    }

    suspend fun setCameraConsent(given: Boolean) {
        prefs.edit { it[KEY_CAMERA_CONSENT] = given }
    }
}
