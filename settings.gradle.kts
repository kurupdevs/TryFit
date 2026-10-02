pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "TryFit"

include(":app")
include(":tryon-engine")

// The try-on engine lives outside the default project layout so it can be
// shared/kept lean as a pure-JVM module. NOTE: the path is resolved against
// rootDir (this file lives at the repo root), so it is "modules/tryon-engine",
// not "../modules/tryon-engine" — the latter would point outside the repo.
project(":tryon-engine").projectDir = File(rootDir, "modules/tryon-engine")
