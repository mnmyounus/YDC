# YDC — Your Device Controller
### Architecture Blueprint & Implementation Guide
**Author:** MNM YOUNUS

---

## Before you build: 3 spec corrections worth knowing up front

**1. "Android Auto" and "AAOS" are not the same target — only one fits this app.**
Android Auto is phone-projection: your phone renders a car screen using Google's templates, and only specific app categories are certified for it (media, navigation, messaging, EV-charging/parking, a few others). A general remote-control/file-transfer app doesn't fit any current template and won't run through Android Auto. Android Automotive OS (AAOS) is different — a full standalone Android build running natively on the head unit, where a regular app like YDC installs directly (ADB sideload, or an OEM/enterprise app channel). **This blueprint targets AAOS, not Android Auto projection.**

**2. Don't wire `DevicePolicyManager.wipeData()` into "Uninstall."**
`wipeData()` triggers a full factory reset of the *entire device*, not "clear this one app's data." For self-uninstall you want the much smaller, safer operation: delete YDC's own files/cache/prefs, which needs no special privilege at all — that's how `UninstallManager.kt` is built below. A genuine remote-wipe-a-lost-device feature would be a separate, far more heavily-guarded feature (strong auth, explicit confirmation) — don't merge it into the everyday uninstall path.

**3. Accessibility Service = real remote control, and Android makes sure it's consensual.**
`canPerformGestures` + `performGlobalAction` genuinely let YDC drive the host device's UI — which is exactly why Android won't let an app enable this for itself. The user must go to **Settings → Accessibility → YDC** and flip it on manually, after reading a system-generated warning describing exactly what the service can see and do. Design onboarding around that (deep-link to the settings screen, but expect a manual step). Separately, Google Play's Accessibility API policy restricts this permission to accessibility and a handful of approved device-management use cases — a general-purpose remote-control app is likely to be Play-rejected, so plan on distributing signed/debug APKs via GitHub Releases (see §8) rather than the Play Store.

---

## 1. Tech Stack

| Layer | Choice | Why |
|---|---|---|
| Language | Kotlin + Coroutines/Flow | Standard modern Android |
| UI (mobile/tablet/whiteboards) | Jetpack Compose (Material 3) + `WindowSizeClass` | One codebase, responsive breakpoints |
| UI (TV) | Compose for TV (`androidx.tv:tv-material`, `tv-foundation`) | Proper D-pad focus handling, Leanback look |
| UI (AAOS) | Compose, styled to Android's Driver Distraction Guidelines | Large targets, high contrast |
| Discovery (Wi-Fi) | `NsdManager` (mDNS/DNS-SD) | Built-in, no extra infra — same approach LocalSend uses |
| Discovery (no Wi-Fi) | BLE advertise/scan | Low-power presence broadcast |
| Direct device-to-device | `WifiP2pManager` (Wi-Fi Direct) | Works with no shared router |
| Control channel | WebSocket (Ktor Server, CIO engine, embedded) | Low-latency bidirectional commands |
| File transfer | HTTP (same embedded Ktor server) | Chunked/resumable, LocalSend-style |
| Fallback control channel | Bluetooth Classic RFCOMM socket | Works when Wi-Fi is off |
| Local persistence | Room + DataStore | Paired devices, transfer history, prefs |
| DI | Hilt | Consistent across modules |
| Background execution | Foreground Service + persistent notification | Required by Android for an always-on local server — also means the "controller" state is never silently invisible to the device owner |
| Build | Gradle Kotlin DSL + version catalogs | Multi-module hygiene |
| CI/CD | GitHub Actions | Per spec — see `release-debug.yml` |

**Security note:** "offline" shouldn't mean "unencrypted." Pair devices via an on-screen PIN or QR handshake before any control/transfer session opens, and use TLS (self-signed cert, pinned at pairing time) for the local HTTP/WS traffic — otherwise anything else on the LAN could talk to your open socket.

## 2. Project Structure

```
YDC/
├── app-mobile/          # Phone/tablet + interactive-whiteboard entry point
├── app-tv/               # Android TV (Leanback launcher, Compose for TV)
├── app-automotive/       # AAOS entry point
├── core/
│   ├── core-ui/            # Adaptive design system, WindowSizeClass layouts
│   ├── core-network/       # NSD discovery, Ktor server/client, BLE + RFCOMM, Wi-Fi Direct
│   ├── core-device/        # AccessibilityService, DeviceAdminReceiver, uninstall, volume/app-launch control
│   ├── core-filetransfer/  # Chunked/resumable transfer protocol
│   ├── core-data/          # Room DB: paired devices, transfer/session history
│   └── core-common/        # DI modules, dispatchers, logging
├── feature/
│   ├── feature-pairing/    # QR/PIN pairing UX
│   ├── feature-remote/     # Remote-control screen
│   ├── feature-transfer/   # File-share UI
│   └── feature-settings/   # Settings + uninstall entry point
├── .github/workflows/release-debug.yml
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/libs.versions.toml
```

Each `app-*` module is a thin shell: its own `AndroidManifest.xml` (with the right `<uses-feature>` flags) and a launcher `Activity` that assembles screens from `core-ui` + `feature-*`. That's what lets a single `core-network`/`core-device` implementation serve all four form factors.

## 3. Form-Factor Adaptation

- **Mobile / Tablet / Interactive whiteboards** (`app-mobile`): no distinct Android device category or manifest flag exists for boards like ViewSonic/BenQ — they run standard Android, often under the venue's kiosk/EMM lockdown. Target them with `WindowSizeClass.Expanded` layouts (big touch targets, simplified nav) rather than inventing a fake "whiteboard" flag.
- **Android TV** (`app-tv`): manifest needs `<uses-feature android:name="android.software.leanback" android:required="true"/>` and a `LEANBACK_LAUNCHER` intent filter. Build with Compose for TV so D-pad focus order is handled for you.
- **AAOS** (`app-automotive`): manifest needs `<uses-feature android:name="android.hardware.type.automotive" android:required="true"/>`. Follow Android's Driver Distraction Guidelines even though this is a utility app, not media/nav.

## 4. Local Networking & File Transfer

**Discovery:** each device advertises itself via `NsdManager.registerService()` (service type e.g. `_ydc._tcp`) carrying a friendly name + device-class (phone/TV/car/board). Peers browse via `NsdManager.discoverServices()`. When Wi-Fi isn't available or devices aren't on the same LAN, fall back to BLE advertising/scanning for presence, then Wi-Fi Direct or Bluetooth RFCOMM to actually connect.

**Pairing:** first contact between two devices always requires an explicit confirm — PIN shown on both screens, or a QR code scanned by the controller — before either device accepts commands or file offers from the other. Store the resulting keypair/fingerprint in `core-data` (Room) so future sessions can skip re-pairing but still show "connected to \<device\>" in the persistent notification.

**Control channel:** the "host" device runs an embedded Ktor WebSocket server (started from the Foreground Service). The "controller" device connects and sends small JSON command messages, e.g.:
```json
{"cmd":"tap","x":540,"y":1200}
{"cmd":"volume","stream":"media","delta":1}
{"cmd":"launch","package":"com.example.app"}
{"cmd":"global","action":"HOME"}
```
`core-device` receives these and executes them (§5).

**File transfer:** the same embedded server exposes a small HTTP API — `POST /offer` (filename, size, checksum) triggers an accept/reject prompt on the receiving device, then the sender streams bytes to `PUT /transfer/{id}` with HTTP Range support for resume. Same shape as LocalSend: no cloud relay, just HTTP directly between two devices on the LAN.

## 5. Accessibility Service (Remote Input)

**Manifest:**
```xml
<service
    android:name=".core.device.YdcAccessibilityService"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:exported="false">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>
```

**`res/xml/accessibility_service_config.xml`:**
```xml
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:canPerformGestures="true"
    android:canRetrieveWindowContent="true"
    android:notificationTimeout="100"
    android:description="@string/ydc_accessibility_description" />
```

Write `@string/ydc_accessibility_description` plainly — e.g. "Lets YDC simulate taps and button presses so it can be controlled remotely from another paired device" — rather than generic boilerplate; Play reviewers and end users both read it.

See `YdcAccessibilityService.kt` (separate file) for the implementation — it exposes `simulateTap`, `simulateSwipe`, and `triggerGlobalAction`, which is what `core-network`'s command handler calls when a WebSocket command arrives.

To help the user enable it, deep-link rather than trying to auto-enable:
```kotlin
context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
```

## 6. Device Admin

Used narrowly — just enough to make self-uninstall well-behaved, not for remote wipe (see correction #2 above).

**Manifest:**
```xml
<receiver
    android:name=".core.device.YdcDeviceAdminReceiver"
    android:permission="android.permission.BIND_DEVICE_ADMIN"
    android:exported="true">
    <meta-data android:name="android.app.device_admin" android:resource="@xml/device_admin" />
    <intent-filter>
        <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
    </intent-filter>
</receiver>
```

**`res/xml/device_admin.xml`:**
```xml
<device-admin xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-policies>
        <limit-password />
        <force-lock />
        <!-- No wipe-data policy tag: deliberately out of scope, see UninstallManager.kt -->
    </uses-policies>
</device-admin>
```

Request activation the standard way:
```kotlin
val adminComponent = ComponentName(context, YdcDeviceAdminReceiver::class.java)
val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Needed so YDC can uninstall itself cleanly.")
}
context.startActivity(intent)
```

See `YdcDeviceAdminReceiver.kt` (separate file) for the intentionally minimal receiver.

## 7. Uninstall Flow

`UninstallManager.kt` (separate file) does, in order: revoke device admin → delete YDC's own files/cache/prefs → launch `Intent.ACTION_DELETE` for YDC's own package. Android still shows its native uninstall confirmation at the last step — there's no API to bypass that, by design, and it's the user's reasonable final checkpoint.

## 8. CI/CD

`release-debug.yml` (separate file): triggers on push to `main` or manual `workflow_dispatch`, runs `assembleDebug` across all app modules, and publishes every resulting debug APK as an asset on a new GitHub Release. No signing secrets needed — debug builds use Gradle's auto-generated debug keystore.

## 9. Implementation Status

**Implemented as real code (not just described):**
- [x] NSD-based LAN discovery — `DeviceDiscovery.kt`
- [x] Bluetooth RFCOMM fallback transport — `BluetoothTransport.kt`
- [x] Embedded WebSocket + HTTP control/file server — `LocalControlServer.kt`
- [x] Command parsing + dispatch to Accessibility/system actions — `RemoteControlBridge.kt`
- [x] Volume control, app launch, best-effort app close — `SystemActionController.kt`
- [x] Chunked/resumable file sending — `FileTransferManager.kt`
- [x] Host-mode orchestration tying the above together — `HostSessionManager.kt`
- [x] Accessibility Service, Device Admin, uninstall, runtime permissions (previous pass)
- [x] CI/CD

**New Gradle dependencies these files need** (check current release numbers before pinning):
```kotlin
plugins {
    kotlin("plugin.serialization") version "<matches your Kotlin version>"
}
dependencies {
    implementation("io.ktor:ktor-server-cio:<latest>")
    implementation("io.ktor:ktor-server-websockets:<latest>")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:<latest>")
}
```

**Still genuinely open work — flagged, not shortcut:**
- Per-platform UI (mobile/TV/AAOS/whiteboard): the actual screens, navigation, and WindowSizeClass/Compose-for-TV/driver-distraction work from §3
- Pairing UX (PIN/QR confirmation): `HostSessionManager` currently auto-accepts every file offer and has no pairing gate — that's a placeholder, not a security decision
- TLS/pinning for the local HTTP/WS traffic mentioned in §1 — not yet wired in
- Wi-Fi Direct path for devices with no shared access point
- Real device/emulator testing — none of this has been run on actual Android hardware in this environment; treat it as a reviewed reference implementation, not a compiled build

---

*Package name used in the snippets (`com.mnmyounus.ydc.core.device`, `com.mnmyounus.ydc.core.network`, `com.mnmyounus.ydc.core.filetransfer`) is a placeholder — update it to match your actual applicationId before wiring up the manifests.*
