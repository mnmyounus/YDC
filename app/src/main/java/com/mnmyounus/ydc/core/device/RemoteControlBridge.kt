package com.mnmyounus.ydc.core.device

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class RemoteCommand(
    val cmd: String,
    val x: Float? = null,
    val y: Float? = null,
    val x2: Float? = null,
    val y2: Float? = null,
    val stream: String? = null,
    val delta: Int? = null,
    val packageName: String? = null,
    val action: String? = null,
)

/**
 * Single place that turns a parsed RemoteCommand into a real action on this
 * device. LocalControlServer and BluetoothTransport both just hand raw JSON
 * text to handle() — this is where it becomes a tap, a volume change, etc.
 * Requires the kotlinx-serialization-json dependency (see blueprint §9).
 */
object RemoteControlBridge {

    private var accessibilityService: YdcAccessibilityService? = null
    private var systemActions: SystemActionController? = null

    fun attach(service: YdcAccessibilityService) {
        accessibilityService = service
    }

    fun attach(controller: SystemActionController) {
        systemActions = controller
    }

    fun handle(rawJson: String) {
        val command = runCatching { Json.decodeFromString<RemoteCommand>(rawJson) }.getOrNull() ?: return
        when (command.cmd) {
            "tap" -> {
                val x = command.x ?: return
                val y = command.y ?: return
                accessibilityService?.simulateTap(x, y)
            }
            "swipe" -> {
                val x1 = command.x ?: return
                val y1 = command.y ?: return
                val x2 = command.x2 ?: return
                val y2 = command.y2 ?: return
                accessibilityService?.simulateSwipe(x1, y1, x2, y2)
            }
            "global" -> {
                val globalAction = mapGlobalAction(command.action) ?: return
                accessibilityService?.triggerGlobalAction(globalAction)
            }
            "volume" -> {
                val stream = command.stream ?: return
                val delta = command.delta ?: return
                systemActions?.adjustVolume(stream, delta)
            }
            "launch" -> {
                val pkg = command.packageName ?: return
                systemActions?.launchApp(pkg)
            }
            "close" -> {
                val pkg = command.packageName ?: return
                systemActions?.closeApp(pkg)
            }
        }
    }

    private fun mapGlobalAction(name: String?): Int? = when (name) {
        "HOME" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
        "BACK" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
        "RECENTS" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
        "NOTIFICATIONS" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
        else -> null
    }
}
