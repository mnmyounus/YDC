// Still pinned to an older, thoroughly stable AGP/Kotlin combination rather
// than current bleeding edge (AGP 9.x's DSL overhaul, Kotlin 2.x/Ktor 3.x)
// — see README.md for why. Now also declares the serialization plugin,
// needed by RemoteControlBridge's command parsing.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24" apply false
}
