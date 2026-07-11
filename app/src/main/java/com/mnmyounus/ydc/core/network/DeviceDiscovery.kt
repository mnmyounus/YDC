package com.mnmyounus.ydc.core.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val SERVICE_TYPE = "_ydc._tcp"

data class DiscoveredDevice(
    val name: String,
    val host: String,
    val port: Int,
)

/**
 * LAN discovery via Android's built-in mDNS/DNS-SD wrapper — the same
 * general approach LocalSend uses for finding nearby devices (blueprint §4).
 * Requires ACCESS_FINE_LOCATION on many OEM builds even though this is Wi-Fi,
 * not GPS; PermissionManager already covers that.
 */
class DeviceDiscovery(private val context: Context) {

    private val nsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    private var registrationListener: NsdManager.RegistrationListener? = null

    /** Advertises this device on the LAN so controllers can find it. */
    fun advertise(deviceName: String, port: Int) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = deviceName
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        registrationListener = listener
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stopAdvertising() {
        registrationListener?.let { runCatching { nsdManager.unregisterService(it) } }
        registrationListener = null
    }

    /** Emits devices as they're found on the LAN. Cancel the Flow (e.g. viewModelScope) to stop browsing. */
    fun discover(): Flow<DiscoveredDevice> = callbackFlow {
        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                close()
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onServiceFound(service: NsdServiceInfo) {
                nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val address = info.host?.hostAddress ?: return
                        trySend(DiscoveredDevice(info.serviceName, address, info.port))
                    }
                })
            }

            override fun onServiceLost(service: NsdServiceInfo) = Unit
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        awaitClose { runCatching { nsdManager.stopServiceDiscovery(discoveryListener) } }
    }
}
