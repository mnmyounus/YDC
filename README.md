# YDC — Milestone 2: every feature compiled into one app

Everything previously split off into `future-not-yet-wired/` is now part of
the actual build. Nothing is held back.

## What's in the app now
- **Setup**: runtime permissions, Accessibility Settings deep-link, Device
  Admin request, self-uninstall
- **Host Mode**: `HostSessionManager` starts/stops LAN advertising (NSD) and
  the embedded Ktor control/file server; toggled from the UI
- **Discover Nearby Devices**: live NSD scan via `DeviceDiscovery`, results
  streamed straight into the UI
- **Send a File**: system document picker → `FileTransferManager` sends the
  picked file to the first discovered device over HTTP
- **Accessibility-driven remote control**: `YdcAccessibilityService` is wired
  to `RemoteControlBridge`, which routes incoming WebSocket commands to taps/
  swipes/global actions or to `SystemActionController` (volume, launch,
  best-effort close)
- **Bluetooth fallback** (`BluetoothTransport`): compiled in and ready to use;
  not yet wired to a UI control in this pass — the LAN path is what's driven
  from the screen right now

## What changed to make room for all of this
- Added dependencies: Ktor server (CIO + WebSockets), kotlinx.serialization,
  kotlinx-coroutines-android, AndroidX lifecycle/activity-ktx, Material
  Components — see `app/build.gradle.kts` for exact pinned versions and why
  they're older-but-stable rather than bleeding edge.
- Added `INTERNET` and `ACCESS_NETWORK_STATE` permissions (needed now that
  networking code is actually reachable).
- Redesigned `activity_main.xml`: dark theme, Material card sections, real
  color palette — replacing the four bare buttons from Milestone 1.

## Still true from before
I can't compile this myself — no access to Google's Maven, Maven Central, or
Gradle's servers from where this was built (verified, not assumed). More
dependencies than Milestone 1 means more surface area for a first-build
issue. If GitHub Actions fails on a specific artifact/version, that error
message will name it directly — send it over and it's a targeted fix, not a
guess.

## To deploy
1. Push everything in this zip to a new repo's `main` branch (keep
   `.github/workflows/release-debug.yml` at that path).
2. The workflow runs automatically, or trigger it manually from the Actions
   tab.
3. Check the repo's **Releases** page for the APK once it finishes.
