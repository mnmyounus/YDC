package com.mnmyounus.ydc.core.device

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Clean, user-confirmed removal of YDC from this device.
 *
 * Steps:
 *  1. Revoke Device Admin, if active (an active admin blocks uninstallation).
 *  2. Best-effort delete of YDC's OWN app data only — never touches
 *     anything outside YDC's sandbox, and is NOT a device-wide wipe.
 *  3. Hand off to the system uninstaller. Android still shows its own
 *     confirmation dialog and the user must tap "Uninstall" themselves.
 */
object UninstallManager {

    private const val PREFS_NAME = "ydc_prefs"

    fun requestSelfUninstall(context: Context) {
        revokeDeviceAdmin(context)
        wipeOwnAppData(context)
        launchSystemUninstall(context)
    }

    private fun revokeDeviceAdmin(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, YdcDeviceAdminReceiver::class.java)
        if (dpm.isAdminActive(admin)) {
            dpm.removeActiveAdmin(admin)
        }
    }

    private fun wipeOwnAppData(context: Context) {
        runCatching { context.filesDir.deleteRecursively() }
        runCatching { context.cacheDir.deleteRecursively() }
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        }
        runCatching { context.getExternalFilesDir(null)?.deleteRecursively() }
    }

    private fun launchSystemUninstall(context: Context) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:${context.packageName}")
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
