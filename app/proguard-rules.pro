# TryFit ProGuard / R8 rules (release, full mode)

# --- kotlinx.serialization (used by supabase-kt DTOs) ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers @kotlinx.serialization.Serializable class * {
    <fields>;
    *** Companion;
}
-keepnames @kotlinx.serialization.Serializable class *

# --- supabase-kt: keep module entry points referenced via reflection-ish installs ---
-keep class io.github.jan-tennert.supabase.** { *; }
-dontwarn io.github.jan-tennert.supabase.**

# --- Coil 3 ---
-dontwarn coil3.**

# --- CameraX ---
-keep class androidx.camera.** { *; }

# --- Baseline Profiles installer ---
-keep class androidx.profileinstaller.** { *; }

# --- DataStore / protobuf ---
-keep class androidx.datastore.** { *; }
