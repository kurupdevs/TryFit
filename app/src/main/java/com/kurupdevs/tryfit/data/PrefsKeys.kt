package com.kurupdevs.tryfit.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

/**
 * DataStore preference keys (single `tryfit_prefs` file, see AppContainer).
 */
object PrefsKeys {
    val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")

    // Delivery address (checkout).
    val ADDRESS_NAME = stringPreferencesKey("address_name")
    val ADDRESS_PHONE = stringPreferencesKey("address_phone")
    val ADDRESS_PINCODE = stringPreferencesKey("address_pincode")
    val ADDRESS_LINE = stringPreferencesKey("address_line")

    /** Last placed order, JSON-encoded. */
    val LAST_ORDER_JSON = stringPreferencesKey("last_order_json")
}
