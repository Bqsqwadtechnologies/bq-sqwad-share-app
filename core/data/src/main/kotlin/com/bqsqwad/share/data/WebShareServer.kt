package com.bqsqwad.share.data

import java.io.File
import java.io.InputStream
import java.io.OutputStream
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
    @Volatile private var socket: ServerSocket? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        inbox.mkdirs()
        Thread({
            try {
                ServerSocket(port).use { server ->
                    socket = server
                    while (running.get()) {
                        try {
                            val client = server.accept()
                            client.soTimeout = 15_000
                            Thread({ handle(client) }, "bq-share-http-client").start()
                        } catch (_: Exception) {
                            if (running.get()) continue else break
                        }
                    }
                }
            } finally {
                socket = null
                running.set(false)
            }
        }, "bq-share-http-server").start()
    }

    fun stop() {
        running.set(false)
        try { socket?.close() } catch (_: Exception) { }
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
            val input = sock.getInputStream()
            val request = readAsciiLine(input, MAX_HEADER_LINE) ?: return
            val headers = mutableMapOf<String, String>()
            var headerBytes = request.length
            while (true) {
                val line = readAsciiLine(input, MAX_HEADER_LINE) ?: return
                headerBytes += line.length
                if (headerBytes > MAX_HEADER_BYTES) {
                    write(sock.getOutputStream(), 413, "text/plain", "Request headers too large.")
                    return
                }
                if (line.isEmpty()) break
                val idx = line.indexOf(':')
                if (idx > 0) headers[line.substring(0, idx).lowercase()] = line.substring(idx + 1).trim()
            }
            val parts = request.split(' ')
            if (parts.size < 2) return
            val method = parts[0]
            val target = parts[1]
            val path = target.substringBefore('?')
            val query = target.substringAfter('?', "")
            val queryPin = query.split('&').firstNotNullOfOrNull { part ->
                if (part.startsWith("pin=")) runCatching {
                    URLDecoder.decode(part.substringAfter('='), StandardCharsets.UTF_8)
                }.getOrNull() else null
            }
            val pinOk = constantTimeEquals(queryPin.orEmpty(), pin) ||
                constantTimeEquals(headers["x-pin"].orEmpty(), pin)
            val out = sock.getOutputStream()
            if (!pinOk && path != "/health") {
                write(out, 401, "text/plain", "PIN required. Open the link from the phone.")
                return
            }
            when {
                method == "GET" && path == "/" -> write(out, 200, "text/html", page())
                method == "GET" && path.startsWith("/file/") -> {
                    val name = runCatching {
                        URLDecoder.decode(path.removePrefix("/file/"), StandardCharsets.UTF_8)
                    }.getOrNull()
                    val file = name?.let { requested -> shared.firstOrNull { it.name == requested } }
                    if (file == null || !file.isFile) write(out, 404, "text/plain", "Missing")
                    else {
                        val safeName = file.name.replace("\\", "_").replace("\"", "_").replace("\r", "_").replace("\n", "_")
                        val header = "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: ${file.length()}\r\nContent-Disposition: attachment; filename=\"$safeName\"\r\nConnection: close\r\n\r\n"
                        out.write(header.toByteArray(StandardCharsets.US_ASCII))
                        file.inputStream().use { it.copyTo(out) }
                    }
                }
                method == "POST" && path == "/upload" -> receiveUpload(input, out, headers)
                else -> write(out, 404, "text/plain", "Not found")
            }
        }
    }

    /**
     * The request headers are read directly from the socket, one byte at a time.
     * A BufferedReader can read ahead into the binary body and silently consume
     * the first bytes of an upload, so the body must use the same raw stream.
     */
    private fun receiveUpload(input: InputStream, out: OutputStream, headers: Map<String, String>) {
        val length = headers["content-length"]?.toLongOrNull()
        if (length == null || length < 0L) {
            write(out, 411, "text/plain", "Content-Length is required.")
            return
        }
        if (length > MAX_UPLOAD_BYTES) {
            write(out, 413, "text/plain", "File exceeds the local sharing size limit.")
            return
        }
        val requestedName = headers["x-file-name"]?.let { encoded ->
            runCatching { URLDecoder.decode(encoded, StandardCharsets.UTF_8) }.getOrNull()
        }.orEmpty()
        val leafName = requestedName.substringAfterLast('/').substringAfterLast('\\')
        val safeName = leafName.filter { it.isLetterOrDigit() || it in " ._()-[]" }.trim().trim('.').take(180)
            .ifBlank { "received-${System.currentTimeMillis()}.bin" }
        val base = safeName.substringBeforeLast('.', safeName)
        val extension = safeName.substringAfterLast('.', "").takeIf { safeName.contains('.') }?.let { ".$it" }.orEmpty()
        var destination = File(inbox, safeName)
        var suffix = 1
        while (destination.exists()) { destination = File(inbox, "$base ($suffix)$extension"); suffix++ }
        val temporary = File(inbox, destination.name + ".part")
        try {
            temporary.outputStream().use { fileOut ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var remaining = length
                while (remaining > 0L) {
                    val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (count < 0) throw java.io.EOFException("Upload ended before Content-Length bytes arrived.")
                    fileOut.write(buffer, 0, count)
                    remaining -= count
                }
                fileOut.flush()
            }
            check(temporary.renameTo(destination)) { "Could not finish saving the uploaded file." }
            write(out, 200, "text/plain", "Saved ${destination.name}")
        } catch (_: Exception) {
            temporary.delete()
            destination.delete()
            write(out, 400, "text/plain", "Upload was incomplete and was not saved.")
        }
    }

    private fun readAsciiLine(input: InputStream, maxLength: Int): String? {
        val bytes = java.io.ByteArrayOutputStream()
        while (bytes.size() <= maxLength) {
            val value = input.read()
            if (value < 0) return if (bytes.size() == 0) null else bytes.toString(StandardCharsets.US_ASCII.name())
            if (value == 10) {
                val line = bytes.toByteArray()
                val length = if (line.isNotEmpty() && line.last() == 13.toByte()) line.size - 1 else line.size
                return String(line, 0, length, StandardCharsets.US_ASCII)
            }
            bytes.write(value)
        }
        throw IllegalArgumentException("HTTP header line too long.")
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        val left = a.toByteArray(StandardCharsets.UTF_8)
        val right = b.toByteArray(StandardCharsets.UTF_8)
        return java.security.MessageDigest.isEqual(left, right)
    }

    private fun page(): String {
        val links = shared.filter { it.isFile }.joinToString("") { f ->
            "<li><a href=\"/file/${urlEncode(f.name)}?pin=${urlEncode(pin)}\">${htmlEscape(f.name)}</a> (${f.length()} bytes)</li>"
        }
        return """
            <!doctype html><html><head><meta name=viewport content="width=device-width,initial-scale=1">
            <title>BQ Sqwad Share</title>
            <style>body{font-family:sans-serif;background:#f4f7ff;color:#0b1b4a;padding:24px}a{color:#0a4dff}</style>
            </head><body><h1>BQ Sqwad Share</h1><p>Files stay on this phone. This page only works on the same Wi-Fi.</p>
            <ul>$links</ul>
            <form method="post" action="/upload?pin=${htmlEscape(urlEncode(pin))}" enctype="application/octet-stream">
            <p>Uploads from the Windows companion are saved to the phone's receive folder.</p>
            </form></body></html>
        """.trimIndent()
    }

    private fun urlEncode(value: String) = java.net.URLEncoder.encode(value, StandardCharsets.UTF_8)
    private fun htmlEscape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

    private fun write(out: OutputStream, code: Int, type: String, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val msg = when (code) { 200 -> "OK"; 400 -> "Bad Request"; 401 -> "Unauthorized"; 411 -> "Length Required"; 413 -> "Payload Too Large"; else -> "Error" }
        val header = "HTTP/1.1 $code $msg\r\nContent-Type: $type; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        out.write(header.toByteArray(StandardCharsets.US_ASCII))
        out.write(bytes)
    }

    companion object {
        private const val MAX_HEADER_LINE = 8 * 1024
        private const val MAX_HEADER_BYTES = 16 * 1024
        private const val MAX_UPLOAD_BYTES = 1024L * 1024L * 1024L
    }
}
