package com.bqsqwad.share.data

import java.io.File
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

class WebShareServer(
    private val port: Int,
    private val pin: String,
    private val inbox: File,
    private val shared: List<File>,
) {
    private val running = AtomicBoolean(false)
    private var socket: ServerSocket? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        inbox.mkdirs()
        Thread {
            val server = ServerSocket(port)
            socket = server
            while (running.get()) {
                try {
                    val client = server.accept()
                    client.soTimeout = 15_000
                    Thread { handle(client) }.start()
                } catch (_: Exception) {
                    break
                }
            }
        }.start()
    }

    fun stop() {
        running.set(false)
        try { socket?.close() } catch (_: Exception) {}
    }

    fun lanAddress(): String {
        val host = NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { !it.isLoopbackAddress && it is java.net.Inet4Address }
            ?.hostAddress ?: "127.0.0.1"
        return "http://$host:$port"
    }

    private fun handle(client: java.net.Socket) {
        client.use { sock ->
            val input = sock.getInputStream().bufferedReader()
            val request = input.readLine() ?: return
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
                val idx = line.indexOf(':')
                if (idx > 0) headers[line.substring(0, idx).lowercase()] = line.substring(idx + 1).trim()
            }
            val parts = request.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val path = parts[1].substringBefore('?')
            val query = parts[1].substringAfter('?', "")
            val pinOk = query.split('&').any { it == "pin=$pin" } || headers["x-pin"] == pin
            val out = sock.getOutputStream()
            if (!pinOk && path != "/health") {
                write(out, 401, "text/plain", "PIN required. Open the link from the phone.")
                return
            }
            when {
                method == "GET" && path == "/" -> write(out, 200, "text/html", page())
                method == "GET" && path.startsWith("/file/") -> {
                    val name = URLDecoder.decode(path.removePrefix("/file/"), StandardCharsets.UTF_8)
                    val file = shared.firstOrNull { it.name == name }
                    if (file == null || !file.exists()) write(out, 404, "text/plain", "Missing")
                    else {
                        val header = "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: ${file.length()}\r\nContent-Disposition: attachment; filename=\"${file.name}\"\r\nConnection: close\r\n\r\n"
                        out.write(header.toByteArray())
                        file.inputStream().use { it.copyTo(out) }
                    }
                }
                method == "POST" && path == "/upload" -> {
                    val len = headers["content-length"]?.toIntOrNull() ?: 0
                    val body = ByteArray(len)
                    var read = 0
                    val raw = sock.getInputStream()
                    while (read < len) {
                        val n = raw.read(body, read, len - read)
                        if (n < 0) break
                        read += n
                    }
                    val dest = File(inbox, "upload-${System.currentTimeMillis()}.bin")
                    dest.writeBytes(body)
                    write(out, 200, "text/plain", "Saved ${dest.name}")
                }
                else -> write(out, 404, "text/plain", "Not found")
            }
        }
    }

    private fun page(): String {
        val links = shared.joinToString("") { f ->
            "<li><a href=\"/file/${f.name}?pin=$pin\">${f.name}</a> (${f.length()} bytes)</li>"
        }
        return """
            <!doctype html><html><head><meta name=viewport content="width=device-width,initial-scale=1">
            <title>BQ Sqwad Share</title>
            <style>body{font-family:sans-serif;background:#f4f7ff;color:#0b1b4a;padding:24px}a{color:#0a4dff}</style>
            </head><body><h1>BQ Sqwad Share</h1><p>Files stay on this phone. This page only works on the same Wi-Fi.</p>
            <ul>$links</ul>
            <form method="post" action="/upload?pin=$pin" enctype="application/octet-stream">
            <p>Upload from this browser is sent as the raw body by the app link. Use the phone to pick shared files.</p>
            </form></body></html>
        """.trimIndent()
    }

    private fun write(out: java.io.OutputStream, code: Int, type: String, body: String) {
        val bytes = body.toByteArray()
        val msg = if (code == 200) "OK" else if (code == 401) "Unauthorized" else "Error"
        val header = "HTTP/1.1 $code $msg\r\nContent-Type: $type; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        out.write(header.toByteArray())
        out.write(bytes)
    }
}
