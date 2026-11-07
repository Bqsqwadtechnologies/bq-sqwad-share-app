package com.bqsqwad.share.data

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

class WebShareServer(
    private val port: Int,
    private val pin: String,
    private val inbox: File,
    private val shared: List<File>,
    private val onUpload: (File) -> Unit = {},
) {
    private val running = AtomicBoolean(false)
    private val clientSlots = Semaphore(MAX_CLIENTS)
    private val failedPinAttempts = ConcurrentHashMap<String, Pair<Long, Int>>()
    @Volatile private var socket: ServerSocket? = null
    @Volatile private var boundAddress: InetAddress? = null

    fun start() {
        if (running.get()) return
        require(pin.matches(Regex("[0-9]{6}"))) { "A six-digit sharing PIN is required." }
        val address = findLocalWifiAddress()
            ?: throw IllegalStateException("Connect this phone to Wi-Fi or enable its hotspot before sharing with a computer.")
        check(inbox.exists() || inbox.mkdirs()) { "Could not create the computer receive folder." }

        val server = ServerSocket()
        try {
            server.reuseAddress = true
            server.bind(InetSocketAddress(address, port), MAX_CLIENTS)
        } catch (failure: Exception) {
            runCatching { server.close() }
            throw IllegalStateException("Could not start local sharing on this Wi-Fi network.", failure)
        }
        if (!running.compareAndSet(false, true)) {
            server.close()
            return
        }
        socket = server
        boundAddress = address
        Thread({
            try {
                while (running.get()) {
                    val client = try {
                        server.accept()
                    } catch (_: Exception) {
                        if (running.get()) continue else break
                    }
                    if (!clientSlots.tryAcquire()) {
                        runCatching { client.close() }
                        continue
                    }
                    Thread({
                        try {
                            client.soTimeout = 120_000
                            handle(client)
                        } finally {
                            clientSlots.release()
                        }
                    }, "bq-share-http-client").apply { isDaemon = true }.start()
                }
            } finally {
                socket = null
                boundAddress = null
                running.set(false)
                runCatching { server.close() }
            }
        }, "bq-share-http-server").apply { isDaemon = true }.start()
    }

    fun stop() {
        running.set(false)
        try { socket?.close() } catch (_: Exception) { }
    }

    fun lanAddress(): String {
        val address = boundAddress
            ?: throw IllegalStateException("The local sharing server is not running.")
        return "http://${address.hostAddress}:$port"
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
            val clientAddress = sock.inetAddress.hostAddress.orEmpty()
            val pinOk = constantTimeEquals(queryPin.orEmpty(), pin) ||
                constantTimeEquals(headers["x-pin"].orEmpty(), pin)
            val out = sock.getOutputStream()
            if (path != "/health" && isPinRateLimited(clientAddress)) {
                write(out, 429, "text/plain", "Too many incorrect PIN attempts. Wait one minute and try again.")
                return
            }
            if (!pinOk && path != "/health") {
                noteFailedPinAttempt(clientAddress)
                write(out, 401, "text/plain", "PIN required. Open the link from the phone.")
                return
            }
            if (pinOk) failedPinAttempts.remove(clientAddress)

            when {
                method == "GET" && path == "/health" -> write(out, 200, "text/plain", "BQ Sqwad Share is ready.")
                method == "GET" && path == "/" -> write(out, 200, "text/html", page())
                method == "GET" && path.startsWith("/file/") -> {
                    val name = runCatching {
                        URLDecoder.decode(path.removePrefix("/file/"), StandardCharsets.UTF_8)
                    }.getOrNull()
                    val file = name?.let { requested -> shared.firstOrNull { it.name == requested } }
                    if (file == null || !file.isFile) write(out, 404, "text/plain", "Missing")
                    else {
                        val safeName = file.name.replace("\\", "_").replace("\"", "_").replace("\r", "_").replace("\n", "_")
                        val encodedName = urlEncode(file.name).replace("+", "%20")
                        val header = "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: ${file.length()}\r\nContent-Disposition: attachment; filename=\"$safeName\"; filename*=UTF-8''$encodedName\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n"
                        out.write(header.toByteArray(StandardCharsets.US_ASCII))
                        file.inputStream().use { it.copyTo(out) }
                    }
                }
                method == "POST" && path == "/upload" -> receiveUpload(input, out, headers)
                else -> write(out, 404, "text/plain", "Not found")
            }
        }
    }

    private fun isPinRateLimited(address: String): Boolean {
        val attempts = failedPinAttempts[address] ?: return false
        val now = System.currentTimeMillis()
        if (now - attempts.first >= PIN_FAILURE_WINDOW_MS) {
            failedPinAttempts.remove(address, attempts)
            return false
        }
        return attempts.second >= MAX_PIN_FAILURES
    }

    private fun noteFailedPinAttempt(address: String) {
        val now = System.currentTimeMillis()
        failedPinAttempts.compute(address) { _, previous ->
            if (previous == null || now - previous.first >= PIN_FAILURE_WINDOW_MS) now to 1
            else previous.first to (previous.second + 1)
        }
    }

    private fun findLocalWifiAddress(): InetAddress? = runCatching {
        val wifiInterfacePrefixes = listOf("wlan", "ap", "swlan", "wifi")
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { network ->
                network.isUp && !network.isLoopback &&
                    wifiInterfacePrefixes.any { prefix -> network.name.lowercase().startsWith(prefix) }
            }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress && !it.isLoopbackAddress && !it.isLinkLocalAddress }
    }.getOrNull()

    /**
     * Headers are read directly from the socket so parsing cannot consume bytes
     * from the following binary upload body.
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
        if (length > inbox.usableSpace - MIN_FREE_SPACE_BYTES) {
            write(out, 507, "text/plain", "Not enough free storage on the phone.")
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
            runCatching { onUpload(destination) }
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
        val links = shared.filter { it.isFile }.joinToString("") { file ->
            "<li><a href=\"/file/${urlEncode(file.name)}?pin=${urlEncode(pin)}\">${htmlEscape(file.name)}</a> (${file.length()} bytes)</li>"
        }
        val safePin = htmlEscape(pin)
        return """
            <!doctype html><html lang="en"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <meta name="referrer" content="no-referrer">
            <title>BQ Sqwad Share</title>
            <style>
              body{font-family:system-ui,sans-serif;background:#f4f7ff;color:#0b1b4a;padding:24px;max-width:760px;margin:auto}
              a{color:#0a4dff}button{padding:10px 16px;border:0;border-radius:8px;background:#1759d1;color:white}
              input{display:block;margin:14px 0}li{margin:10px 0}.note{color:#52627c;font-size:14px}
            </style></head><body><h1>BQ Sqwad Share</h1>
            <p>Files selected on your phone are available below. Files uploaded here are saved in the phone's FromComputer folder.</p>
            <h2>Download from phone</h2><ul>$links</ul>
            <h2>Send files to phone</h2>
            <input id="files" type="file" multiple>
            <button id="upload" type="button">Upload selected files</button>
            <p id="status" role="status" aria-live="polite"></p>
            <p class="note">Use only a trusted private Wi-Fi network or your own hotspot. This local browser connection uses HTTP and is not suitable for public Wi-Fi.</p>
            <script>
              const pin = "$safePin";
              const picker = document.getElementById("files");
              const status = document.getElementById("status");
              document.getElementById("upload").addEventListener("click", async () => {
                if (!picker.files || picker.files.length === 0) { status.textContent = "Choose at least one file."; return; }
                for (const file of picker.files) {
                  status.textContent = "Uploading " + file.name + "…";
                  try {
                    const response = await fetch("/upload", {
                      method: "POST",
                      headers: { "X-PIN": pin, "X-File-Name": encodeURIComponent(file.name) },
                      body: file
                    });
                    const message = await response.text();
                    if (!response.ok) throw new Error(message);
                    status.textContent = message;
                  } catch (error) {
                    status.textContent = "Upload failed: " + (error && error.message ? error.message : "connection error");
                    return;
                  }
                }
                picker.value = "";
                status.textContent = "All selected uploads finished.";
              });
            </script></body></html>
        """.trimIndent()
    }

    private fun urlEncode(value: String) = java.net.URLEncoder.encode(value, StandardCharsets.UTF_8)
    private fun htmlEscape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

    private fun write(out: OutputStream, code: Int, type: String, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val msg = when (code) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            411 -> "Length Required"
            413 -> "Payload Too Large"
            429 -> "Too Many Requests"
            507 -> "Insufficient Storage"
            else -> "Error"
        }
        val contentSecurityPolicy = if (type == "text/html") {
            "Content-Security-Policy: default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'\\r\\n"
        } else ""
        val header = "HTTP/1.1 $code $msg\r\nContent-Type: $type; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\nX-Frame-Options: DENY\r\n${contentSecurityPolicy}Connection: close\r\n\r\n"
        out.write(header.toByteArray(StandardCharsets.US_ASCII))
        out.write(bytes)
    }

    companion object {
        private const val MAX_HEADER_LINE = 8 * 1024
        private const val MAX_HEADER_BYTES = 16 * 1024
        private const val MAX_UPLOAD_BYTES = 1024L * 1024L * 1024L
        private const val MIN_FREE_SPACE_BYTES = 128L * 1024L * 1024L
        private const val MAX_CLIENTS = 4
        private const val MAX_PIN_FAILURES = 8
        private const val PIN_FAILURE_WINDOW_MS = 60_000L
    }
}
