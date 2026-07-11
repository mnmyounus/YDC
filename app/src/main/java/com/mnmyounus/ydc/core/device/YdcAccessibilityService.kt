package com.mnmyounus.ydc.core.device

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

/**
 * Runs on the "host" device being controlled. The user must manually enable
 * this under Settings > Accessibility > YDC (Android does not allow an app
 * to enable it for itself). onServiceConnected attaches to
 * RemoteControlBridge so incoming network commands can call the gesture
 * methods below.
 */
class YdcAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        RemoteControlBridge.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Only needed if YDC also mirrors/reads screen state.
    }

    override fun onInterrupt() = Unit

    fun simulateTap(x: Float, y: Float, durationMs: Long = 50) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    fun simulateSwipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 200) {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    fun triggerGlobalAction(action: Int) {
        performGlobalAction(action)
    }
}
