import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
    kotlin("plugin.serialization")
    // Room schema codegen (Worker E: wardrobe/notifications/history persistence).
    // KSP version MUST pair with the Kotlin version (2.1.21 -> 2.1.21-2.0.2);
    // a mismatched KSP is refused by the build.
    id("com.google.devtools.ksp") version "2.1.21-2.0.2"
}

// Supabase credentials come from local.properties (never hardcoded, never committed).
// Copy local.properties.example -> local.properties and fill in your project values.
val localProperties = Properties().also { props ->
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(props::load)
}
val supabaseUrl: String =
    localProperties.getProperty("supabase.url", System.getenv("SUPABASE_URL") ?: "")
val supabaseAnonKey: String =
    localProperties.getProperty("supabase.anonKey", System.getenv("SUPABASE_ANON_KEY") ?: "")

android {
    namespace = "com.kurupdevs.tryfit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kurupdevs.tryfit"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        vectorDrawables {
            useSupportLibrary = true
        }

        // Injected from local.properties; empty until the developer configures it.
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Compose UI
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Navigation (5-tab graph + nested flows)
    implementation("androidx.navigation:navigation-compose:2.10.2")

    // Images — Coil 3 (SPEC §5: 25% mem cache + 250MB disk configured in AppContainer)
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")

    // CameraX (try-on source picker, Phase 3)
    implementation("androidx.camera:camera-core:1.6.2")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")

    // Supabase (BOM pins module versions together).
    // NOTE: supabase-kt 3.x renamed gotrue-kt -> auth-kt; there is no gotrue-kt
    // artifact in the 3.x line, so auth-kt is used here.
    // NOTE: BOM pinned to 3.1.4 (not latest 3.8.0): 3.8.0's Kotlin metadata
    // (2.4.0) is unreadable by the Kotlin 2.1 compiler mandated for this
    // project. Re-evaluate when the toolchain moves to Kotlin 2.4+.
    implementation(platform("io.github.jan-tennert.supabase:bom:3.1.4"))
    implementation("io.github.jan-tennert.supabase:supabase-kt") // core: createSupabaseClient DSL
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:functions-kt")
    // HTTP engine required by supabase-kt on Android (pinned to the ktor
    // version supabase-kt 3.1.4 was built against — avoid minor-version mix).
    implementation("io.ktor:ktor-client-okhttp:3.1.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // Local prefs (auth session cache, onboarding flag, settings)
    implementation("androidx.datastore:datastore-preferences:1.2.0")

    // Room — offline mirror: wardrobe_items, tryon_history, notifications
    // (data/db/TryFitDatabase.kt). KSP processor; Kotlin codegen.
    // Single version pin (2.8.5, latest stable) — do not add a second block.
    val roomVersion = "2.8.5"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // WorkManager: 24h periodic price-drop checks (Worker F)
    implementation("androidx.work:work-runtime-ktx:2.10.2")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Baseline Profiles runtime (rules shipped in src/main/baselineProfiles/)
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    // In-house try-on engine module (Worker C fills the implementation)
    implementation(project(":tryon-engine"))

    // Tests
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
