package com.mnmyounus.ydc.core.network

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import java.io.File

/**
 * NOTE ON VERSIONS: Ktor reorganized several of these package paths between
 * 1.x / 2.x / 3.x. This is written against the current (2.x/3.x-era) layout;
 * if something doesn't resolve, check Ktor's docs for your pinned version —
 * the shape below (embeddedServer + routing + webSocket block) has been
 * stable across all of them even when import paths moved.
 *
 * The embedded server that runs on the "host" (controlled) device.
 *   - WS  /control        remote-control JSON commands (blueprint §4)
 *   - POST /offer         file-transfer metadata -> accept/reject
 *   - PUT  /transfer/{id} the actual file bytes
 *
 * LAN-only by design: bind to the Wi-Fi interface address, never a public
 * one, and gate both routes behind the pairing check from blueprint §4
 * before this goes anywhere near production — this sample does not include
 * that gate yet (see YDC_Architecture_Blueprint.md §9).
 */
class LocalControlServer(
    private val port: Int,
    private val onCommand: suspend (String) -> Unit,
    private val onFileOffer: suspend (name: String, size: Long) -> Boolean,
    private val downloadDir: File,
) {
    private val server = embeddedServer(CIO, port = port) {
        install(WebSockets)
        routing {
            webSocket("/control") {
                for (frame in incoming) {
                    if (frame is Frame.Text) onCommand(frame.readText())
                }
            }

            post("/offer") {
                val name = call.request.queryParameters["name"]
                    ?: return@post call.respondText("missing name", status = HttpStatusCode.BadRequest)
                val size = call.request.queryParameters["size"]?.toLongOrNull() ?: 0L
                val accepted = onFileOffer(name, size)
                call.respondText(if (accepted) "accepted" else "rejected")
            }

            put("/transfer/{id}") {
                val id = call.parameters["id"]
                    ?: return@put call.respondText("missing id", status = HttpStatusCode.BadRequest)
                if (!downloadDir.exists()) downloadDir.mkdirs()
                val target = File(downloadDir, id)
                call.receiveStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                call.respondText("ok")
            }
        }
    }

    fun start() = server.start(wait = false)
    fun stop() = server.stop(gracePeriodMillis = 1000, timeoutMillis = 2000)
}
