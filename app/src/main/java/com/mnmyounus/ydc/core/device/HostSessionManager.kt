package com.mnmyounus.ydc.core.device

import android.content.Context
import com.mnmyounus.ydc.core.network.DeviceDiscovery
import com.mnmyounus.ydc.core.network.LocalControlServer
import java.io.File

/**
 * Ties every piece together into "host mode": the state a TV / car head unit
 * / whiteboard is in while it's available to be controlled. Call start()
 * only after confirming both PermissionManager.hasAllRuntimePermissions()
 * and PermissionManager.isAccessibilityServiceEnabled() are true.
 *
 * onFileOffer currently auto-accepts every incoming file — that's a
 * placeholder, not a security decision. Wire it to a real accept/reject
 * prompt (and the pairing check from blueprint §4) before this goes near
 * a device you don't fully control yourself. See §9 of the blueprint.
 */
class HostSessionManager(private val context: Context, private val deviceName: String) {

    private val discovery = DeviceDiscovery(context)
    private val systemActions = SystemActionController(context)
    private var server: LocalControlServer? = null

    // 53317 is LocalSend's own default port; pick a different one for YDC
    // so the two apps can coexist on the same network without clashing.
    fun start(port: Int = 57174) {
        RemoteControlBridge.attach(systemActions)

        val localServer = LocalControlServer(
            port = port,
            onCommand = { json -> RemoteControlBridge.handle(json) },
            onFileOffer = { _, _ -> true }, // TODO: replace with a real accept/reject prompt
            downloadDir = File(context.filesDir, "incoming"),
        )
        localServer.start()
        server = localServer

        discovery.advertise(deviceName, port)
    }

    fun stop() {
        discovery.stopAdvertising()
        server?.stop()
        server = null
    }
}
