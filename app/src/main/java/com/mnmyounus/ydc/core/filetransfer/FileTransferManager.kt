package com.mnmyounus.ydc.core.filetransfer

import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sender-side half of the LocalSend-style transfer described in blueprint
 * §4. Receiver-side lives in LocalControlServer's /offer and /transfer/{id}
 * routes. Chunked via a buffered stream so a dropped Wi-Fi connection can
 * resume from a byte offset instead of restarting a large file from zero —
 * pass a non-zero startByte to resume.
 */
class FileTransferManager {

    fun sendFile(
        targetHost: String,
        targetPort: Int,
        file: File,
        transferId: String,
        startByte: Long = 0,
    ) {
        // 1. Offer — the receiver's UI decides accept/reject.
        val offerUrl = URL("http://$targetHost:$targetPort/offer?name=${file.name}&size=${file.length()}")
        val offerConn = (offerUrl.openConnection() as HttpURLConnection).apply { requestMethod = "POST" }
        val accepted = offerConn.inputStream.bufferedReader().readText().trim() == "accepted"
        offerConn.disconnect()
        if (!accepted) return

        // 2. Upload, optionally resuming from startByte.
        val uploadUrl = URL("http://$targetHost:$targetPort/transfer/$transferId")
        val remaining = file.length() - startByte
        val conn = (uploadUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            doOutput = true
            setFixedLengthStreamingMode(remaining)
            if (startByte > 0) setRequestProperty("Range", "bytes=$startByte-")
        }
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(startByte)
            conn.outputStream.use { out ->
                val buffer = ByteArray(64 * 1024)
                var read: Int
                while (raf.read(buffer).also { read = it } != -1) {
                    out.write(buffer, 0, read)
                }
            }
        }
        conn.responseCode // triggers the request
        conn.disconnect()
    }
}
