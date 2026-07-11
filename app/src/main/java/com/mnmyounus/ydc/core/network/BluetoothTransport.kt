package com.mnmyounus.ydc.core.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Bluetooth Classic RFCOMM fallback for the control channel only — small
 * JSON command messages when Wi-Fi isn't available (blueprint §4). File
 * transfer always prefers Wi-Fi (see LocalControlServer); Bluetooth's
 * throughput isn't a good fit for that.
 *
 * Caller must confirm PermissionManager has BLUETOOTH_CONNECT/SCAN granted
 * before calling these — that's why MissingPermission is suppressed here
 * rather than re-checked.
 */
class BluetoothTransport {

    companion object {
        // Generate your own UUID for the real app (`uuidgen` on any Unix
        // shell); this is a placeholder so the sample compiles.
        val YDC_UUID: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
    }

    private var serverSocket: BluetoothServerSocket? = null
    private var activeSocket: BluetoothSocket? = null

    @SuppressLint("MissingPermission")
    fun startListening(adapter: BluetoothAdapter, onConnected: (InputStream, OutputStream) -> Unit) {
        val server = adapter.listenUsingRfcommWithServiceRecord("YDC", YDC_UUID)
        serverSocket = server
        val socket = server.accept() ?: return // blocks until a controller connects
        activeSocket = socket
        onConnected(socket.inputStream, socket.outputStream)
    }

    @SuppressLint("MissingPermission")
    fun connectTo(device: BluetoothDevice, onConnected: (InputStream, OutputStream) -> Unit) {
        val socket = device.createRfcommSocketToServiceRecord(YDC_UUID)
        socket.connect()
        activeSocket = socket
        onConnected(socket.inputStream, socket.outputStream)
    }

    fun close() {
        runCatching { activeSocket?.close() }
        runCatching { serverSocket?.close() }
        activeSocket = null
        serverSocket = null
    }
}
