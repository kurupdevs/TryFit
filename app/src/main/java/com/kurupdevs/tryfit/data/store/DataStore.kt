package com.kurupdevs.tryfit.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/** Single shared preferences DataStore for all local TryFit state. */
val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "tryfit_prefs"
)
