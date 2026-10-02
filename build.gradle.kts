// Root build file for TryFit (com.kurupdevs.tryfit).
// Plugin versions are declared once here (apply false) and applied in modules.

plugins {
    id("com.android.application") version "8.6.1" apply false
    kotlin("android") version "2.1.21" apply false
    kotlin("plugin.compose") version "2.1.21" apply false
    kotlin("plugin.serialization") version "2.1.21" apply false
    kotlin("jvm") version "2.1.21" apply false
}
