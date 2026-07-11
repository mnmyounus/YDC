package com.mnmyounus.ydc.core.device

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Deliberately minimal. YDC only uses Device Admin to make the uninstall
 * flow well-behaved — an active admin blocks uninstallation until it's
 * revoked (see UninstallManager.kt). It intentionally does NOT request
 * wipe-data policies: DevicePolicyManager.wipeData() performs a full
 * factory reset of the entire device, a different and much higher-risk
 * capability than "let YDC uninstall itself cleanly."
 */
class YdcDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "YDC device admin disabled", Toast.LENGTH_SHORT).show()
    }
}
