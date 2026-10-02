plugins {
    kotlin("jvm")
}

// Pure-Kotlin/JVM try-on engine module.
// Platform-agnostic orchestration (photo-quality checks, pipeline state
// machine, quota bookkeeping) lives here so it stays unit-testable without
// the Android framework. Worker C fills the implementation.

group = "com.kurupdevs.tryfit"
version = "0.1.0"

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    testImplementation(kotlin("test"))
}

tasks.withType<Test> {
    useJUnitPlatform()
}
