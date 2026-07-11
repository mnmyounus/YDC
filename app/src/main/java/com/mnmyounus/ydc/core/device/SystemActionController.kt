package com.mnmyounus.ydc.core.device

import android.content.Context
import android.content.Intent
import android.media.AudioManager

/**
 * Handles the "remote control" actions that don't need Accessibility at all:
 * volume and launching an app are both plain public APIs. Closing an
 * arbitrary OTHER app is the one item here that's more limited than it
 * sounds — see closeApp() below.
 */
class SystemActionController(private val context: Context) {

    private val audioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    fun adjustVolume(streamName: String, delta: Int) {
        val stream = when (streamName.lowercase()) {
            "media" -> AudioManager.STREAM_MUSIC
            "ring" -> AudioManager.STREAM_RING
            "alarm" -> AudioManager.STREAM_ALARM
            "call" -> AudioManager.STREAM_VOICE_CALL
            else -> AudioManager.STREAM_MUSIC
        }
        val direction = if (delta > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audioManager.adjustStreamVolume(stream, direction, AudioManager.FLAG_SHOW_UI)
    }

    fun launchApp(packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Android gives no ordinary app an API to force-stop another app — that's
     * intentionally locked down (killBackgroundProcesses only touches
     * background/cached processes, and the old restartPackage() call was
     * removed years ago). The realistic way YDC can "close" another app is
     * the same way a human would: open Recents and swipe the task away,
     * which needs the Accessibility Service. Treat this as a starting point,
     * not a guaranteed close — the exact swipe gesture is OEM-recents-layout
     * dependent and isn't implemented here.
     */
    fun closeApp(packageName: String) {
        RemoteControlBridge.handle("""{"cmd":"global","action":"RECENTS"}""")
    }
}
