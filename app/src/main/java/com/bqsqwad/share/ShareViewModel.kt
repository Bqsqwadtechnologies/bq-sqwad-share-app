package com.bqsqwad.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.content.pm.PackageManager
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bqsqwad.share.common.FileKind
import com.bqsqwad.share.common.SessionCrypto
import com.bqsqwad.share.common.TransferProtocol
import com.bqsqwad.share.common.ResumableFileReceiver
import com.bqsqwad.share.common.folderFor
import com.bqsqwad.share.common.kindForMime
import com.bqsqwad.share.data.NearbyTransport
import com.bqsqwad.share.data.SettingsStore
import com.bqsqwad.share.data.TransferDao
import com.bqsqwad.share.files.FileUtilities
import com.bqsqwad.share.data.TransferEntity
import com.bqsqwad.share.data.WebShareServer
import com.bqsqwad.share.domain.Peer
import com.bqsqwad.share.transfer.TransferService
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject

private data class OutgoingShareFile(val file: File, val mime: String, val bytes: Long, val displayName: String)

@HiltViewModel
class ShareViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: TransferDao,
    private val store: SettingsStore,
    val nearby: NearbyTransport,
) : ViewModel() {
    val settings = store.settings
    val history = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val favorites = dao.observeFavorites().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val moved = dao.observeMovedBytes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    val trustedDeviceHistory = store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val sessionKey = SessionCrypto.newKey()
    var sessionCode: String = SessionCrypto.sixDigitCode()
        private set
    val receiveExpiresAt = MutableStateFlow(0L)
    val incomingTransferMessage = MutableStateFlow("")
    val pendingUris = MutableStateFlow<List<Uri>>(emptyList())
    val pendingLocalFiles = MutableStateFlow<List<File>>(emptyList())
    val autoSendPending = MutableStateFlow(false)
    val scannedReceiveCode = MutableStateFlow("")
    val verification = SessionCrypto.fingerprint(sessionKey)
    val keyText = SessionCrypto.keyToString(sessionKey)
    private val outgoingPayloadRecords = java.util.concurrent.ConcurrentHashMap<Long, TransferEntity>()
    private val outgoingPayloadIds = java.util.concurrent.ConcurrentHashMap<Long, Long>()
    private val pendingOutgoingQueue = java.util.concurrent.ConcurrentLinkedQueue<TransferEntity>()
    private val pumpingQueue = java.util.concurrent.atomic.AtomicBoolean(false)
    private val maxConcurrentPayloads = 3
    private val activeResumableTransfers = java.util.concurrent.ConcurrentHashMap<Long, Job>()
    private val incomingTransferMimes = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val resumableReceiver by lazy { ResumableFileReceiver(File(context.filesDir, "resumable-incoming")) }

    init {
        // A process restart cannot resume a Nearby payload in place. Mark unfinished
        // rows as recoverable so the user can retry instead of leaving them stuck.
        viewModelScope.launch {
            val transferRows = dao.observeAll().first()
            FileUtilities.cleanupOrphanedOutgoingFiles(
                context.cacheDir,
                transferRows.mapNotNull { it.localPath }.toSet(),
            )
            transferRows
                .filter { it.direction.equals("SEND", true) && it.status in setOf("SENDING", "QUEUED") }
                .forEach { dao.update(it.copy(status = "WAITING_FOR_DEVICE")) }
        }
        viewModelScope.launch {
            nearby.connected.collect { connected ->
                if (connected.isNotEmpty()) {
                    val waiting = dao.observeAll().first().filter {
                        it.direction.equals("SEND", true) && it.status == "WAITING_FOR_DEVICE" && it.peerName in connected
                    }
                    waiting.forEach { row ->
                        if (activeResumableTransfers[row.id] == null && pendingOutgoingQueue.none { it.id == row.id }) {
                            val queued = row.copy(status = "QUEUED")
                            dao.update(queued)
                            pendingOutgoingQueue.add(queued)
                        }
                    }
                }
                pumpOutgoingQueue()
            }
        }
        val activePayloadIds = mutableSetOf<Long>()
        viewModelScope.launch {
            nearby.progress.collect { progress ->
                if (progress.status == PayloadTransferUpdate.Status.IN_PROGRESS) {
                    activePayloadIds.add(progress.payloadId)
                    outgoingPayloadRecords[progress.payloadId]?.let { outgoing ->
                        viewModelScope.launch {
                            dao.update(
                                outgoing.copy(
                                    status = "SENDING",
                                    bytesDone = progress.bytes.coerceIn(0L, outgoing.bytes),
                                ),
                            )
                        }
                    }
                } else {
                    activePayloadIds.remove(progress.payloadId)
                    val outgoing = outgoingPayloadRecords.remove(progress.payloadId)
                    if (outgoing != null) {
                        outgoingPayloadIds.remove(outgoing.id)

                        val succeeded = progress.status == PayloadTransferUpdate.Status.SUCCESS
                        viewModelScope.launch {
                            dao.update(
                                outgoing.copy(
                                    status = when (progress.status) {
                                        PayloadTransferUpdate.Status.SUCCESS -> "DONE"
                                        PayloadTransferUpdate.Status.CANCELED -> "CANCELLED"
                                        else -> "FAILED"
                                    },
                                    bytesDone = if (succeeded) outgoing.bytes else progress.bytes.coerceAtLeast(0L),
                                ),
                            )
                        }
                        if (succeeded) {
                            outgoing.localPath?.let { path ->
                                val file = File(path)
                                if (file.name.startsWith("outgoing-")) runCatching { file.delete() }
                            }
                        }
                    }
                    pumpOutgoingQueue()
                }
                val complete = progress.status != PayloadTransferUpdate.Status.IN_PROGRESS && activePayloadIds.isEmpty()
                val text = if (complete) {
                    if (progress.status == 1) "Transfer completed" else "Transfer ended"
                } else {
                    "Transferring ${progress.bytes} of ${progress.total} bytes"
                }
                TransferService.update(context, text, progress.bytes, progress.total, complete)
            }
        }
        // Use (endpoint, payload ID) as the key: multiple peers can independently
        // allocate the same numeric Nearby payload ID.
        val pendingIncoming = java.util.concurrent.ConcurrentHashMap<Pair<String, Long>, File>()
        val incomingManifests = java.util.concurrent.ConcurrentHashMap<Pair<String, Long>, TransferProtocol.FileManifest>()
        val completedIncoming = java.util.concurrent.ConcurrentHashMap.newKeySet<Pair<String, Long>>()
        val nonFilePayloads = java.util.concurrent.ConcurrentHashMap.newKeySet<Pair<String, Long>>()

        fun scheduleMissingManifestTimeout(key: Pair<String, Long>) {
            viewModelScope.launch {
                delay(15_000)
                if (completedIncoming.contains(key) && !incomingManifests.containsKey(key)) {
                    pendingIncoming.remove(key)?.let { file -> withContext(Dispatchers.IO) { runCatching { file.delete() } } }
                    completedIncoming.remove(key)
                    incomingTransferMessage.value =
                        "A received file was not saved because sender verification data was missing. Update both devices and retry."
                }
            }
        }

        fun persistCompletedPayload(key: Pair<String, Long>) {
            val incomingFile = pendingIncoming.remove(key) ?: return
            val manifest = incomingManifests.remove(key) ?: run {
                pendingIncoming[key] = incomingFile
                return
            }
            completedIncoming.remove(key)
            val endpointId = key.first
            viewModelScope.launch {
                try {
                    val receivedName = withContext(Dispatchers.IO) {
                        require(incomingFile.length() == manifest.size) {
                            "The received file size does not match the sender's manifest."
                        }
                        val expectedHash = manifest.sha256.joinToString("") { "%02x".format(it.toInt() and 0xff) }
                        require(FileUtilities.sha256(incomingFile).equals(expectedHash, ignoreCase = true)) {
                            "The received file failed the sender's SHA-256 integrity check."
                        }
                        val name = FileUtilities.safeFileName(manifest.name)
                        val mime = manifest.mime
                        val kind = kindForMime(mime, name)
                        val directory = File(context.getExternalFilesDir(null), folderFor(kind))
                        val destination = FileUtilities.copyToUniqueDestination(incomingFile, directory, name)
                        record(destination.name, mime, destination.length(), "RECEIVE", endpointId, destination.absolutePath)
                        destination.name
                    }
                    incomingTransferMessage.value = "Received and verified: $receivedName"
                } catch (failure: Exception) {
                    incomingTransferMessage.value = failure.message?.takeIf { it.contains("integrity", true) || it.contains("manifest", true) }
                        ?: "The transfer finished, but this file could not be saved. Check available storage and try again."
                } finally {
                    // Nearby's temporary payload file is no longer needed after the app-owned copy.
                    withContext(Dispatchers.IO) { runCatching { incomingFile.delete() } }
                }
            }
        }

        viewModelScope.launch {
            nearby.payloads.collect { event ->
                val payload = event.payload
                val key = event.endpointId to payload.id
                when (payload.type) {
                    Payload.Type.BYTES -> {
                        nonFilePayloads.add(key)
                        val bytes = payload.asBytes()
                        if (bytes != null && handleResumableFrame(event.endpointId, bytes)) return@collect
                        val manifest = bytes?.let { data ->
                            runCatching { TransferProtocol.parseFileManifest(data) }.getOrNull()
                        }
                        if (manifest != null) {
                            val manifestKey = event.endpointId to manifest.filePayloadId
                            incomingManifests[manifestKey] = manifest
                            if (pendingIncoming.containsKey(manifestKey) && completedIncoming.contains(manifestKey)) {
                                persistCompletedPayload(manifestKey)
                            }
                        }
                    }
                    Payload.Type.FILE -> {
                        val incomingFile = runCatching { payload.asFile()?.asJavaFile() }.getOrNull() ?: return@collect
                        pendingIncoming[key] = incomingFile
                        if (completedIncoming.contains(key)) {
                            if (incomingManifests.containsKey(key)) persistCompletedPayload(key)
                            else scheduleMissingManifestTimeout(key)
                        }
                    }
                    else -> nonFilePayloads.add(key)
                }
            }
        }

        viewModelScope.launch {
            nearby.progress.collect { event ->
                val key = event.endpointId to event.payloadId
                if (event.status == PayloadTransferUpdate.Status.SUCCESS) {
                    if (nonFilePayloads.remove(key)) return@collect
                    if (pendingIncoming.containsKey(key) && incomingManifests.containsKey(key)) {
                        persistCompletedPayload(key)
                    } else {
                        completedIncoming.add(key)
                        scheduleMissingManifestTimeout(key)
                    }
                } else if (event.status != PayloadTransferUpdate.Status.IN_PROGRESS) {
                    pendingIncoming.remove(key)?.let { file -> runCatching { file.delete() } }
                    incomingManifests.remove(key)
                    completedIncoming.remove(key)
                }
            }
        }
    }

    var web: WebShareServer? = null
        private set

    /** Creates a fresh receive code each time the Receive screen is opened or regenerated. */
    fun dismissIncomingTransferMessage() {
        incomingTransferMessage.value = ""
    }

    fun startReceiveSession() {
        incomingTransferMessage.value = ""
        val previous = sessionCode
        var next = SessionCrypto.sixDigitCode()
        while (next == previous) next = SessionCrypto.sixDigitCode()
        sessionCode = next
        nearby.stopAdvertising()
        nearby.setExpectedReceiveCode(next)
        receiveExpiresAt.value = System.currentTimeMillis() + 10 * 60 * 1000L
        nearby.startAdvertising("BQ Sqwad Share")
    }

    fun expireReceiveSessionIfNeeded(now: Long = System.currentTimeMillis()) {
        if (receiveExpiresAt.value > 0L && now >= receiveExpiresAt.value) {
            sessionCode = ""
            nearby.setExpectedReceiveCode("")
            nearby.stopAdvertising()
        }
    }

    fun stopReceiveSession() {
        nearby.setExpectedReceiveCode("")
        nearby.stopAdvertising()
        sessionCode = ""
        receiveExpiresAt.value = 0L
    }

    fun confirmAge() = viewModelScope.launch { store.setAgeConfirmed() }
    fun theme(mode: String) = viewModelScope.launch { store.setTheme(mode) }
    fun accent(name: String) = viewModelScope.launch { store.setAccent(name) }
    fun haptics(on: Boolean) = viewModelScope.launch { store.setHaptics(on) }
    fun motion(on: Boolean) = viewModelScope.launch { store.setReduceMotion(on) }
    fun notif(on: Boolean) = viewModelScope.launch { store.setNotifications(on) }
    fun rewardAdFreeProgress() = viewModelScope.launch {
        if (store.addAdFreeReward() >= 5) store.grantAdFree(System.currentTimeMillis() + 86_400_000L)
    }
    fun premiumDay() = viewModelScope.launch { store.grantPremium(System.currentTimeMillis() + 86_400_000L) }
    fun block(name: String) = viewModelScope.launch { store.block(name) }
    fun trustDevice(endpointId: String) = viewModelScope.launch { nearby.trustDevice(endpointId) }
    fun untrustDevice(identity: String) = viewModelScope.launch { nearby.untrustDevice(identity) }
    fun nickname(id: String, name: String) = viewModelScope.launch { store.setNickname(id, name) }
    fun clearHistory() = viewModelScope.launch { dao.clear() }
    fun moveToDeleted(item: TransferEntity) = viewModelScope.launch { dao.update(item.copy(status = "DELETED")) }
    fun restoreFromDeleted(item: TransferEntity) = viewModelScope.launch { dao.update(item.copy(status = "DONE")) }
    fun permanentlyDelete(item: TransferEntity) = viewModelScope.launch {
        item.localPath?.let { path -> runCatching { File(path).takeIf { it.isFile }?.delete() } }
        dao.delete(item.id)
    }

    fun storage(): Pair<Long, Long> {
        val stat = StatFs(context.filesDir.absolutePath)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        return free to total
    }

    fun record(name: String, mime: String, bytes: Long, direction: String, peer: String, path: String?) {
        viewModelScope.launch {
            val kind = kindForMime(mime, name)
            val dir = File(context.getExternalFilesDir(null), folderFor(kind))
            dir.mkdirs()
            dao.insert(
                TransferEntity(
                    direction = direction,
                    displayName = name,
                    mime = mime,
                    bytes = bytes,
                    bytesDone = bytes,
                    status = "DONE",
                    peerName = peer,
                    localPath = path ?: File(dir, name).absolutePath,
                    createdAt = System.currentTimeMillis(),
                    favorite = false,
                ),
            )
        }
    }

    fun toggleFavorite(item: TransferEntity) = viewModelScope.launch {
        dao.update(item.copy(favorite = !item.favorite))
    }

    fun installedApps(): List<Pair<String, String>> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { it.loadLabel(context.packageManager).toString() to it.activityInfo.packageName }
            .sortedBy { it.first.lowercase() }
    }

    fun ownApk(): File = File(context.applicationInfo.sourceDir)

    fun startWeb(files: List<File>, pin: String): String {
        web?.stop()
        val inbox = File(context.getExternalFilesDir(null), "FromComputer")
        val server = WebShareServer(8787, pin, inbox, files)
        server.start()
        web = server
        return server.lanAddress()
    }

    fun stopWeb() { web?.stop(); web = null }

    fun peers(): List<Peer> = nearby.peers.value

    fun setScannedReceiveCode(code: String) { scannedReceiveCode.value = code.filter(Char::isDigit).take(6) }

    fun queueFiles(uris: List<Uri>, autoSend: Boolean = false) {
        pendingUris.value = (pendingUris.value + uris).distinct()
        if (autoSend) autoSendPending.value = true
    }

    fun queueLocalFiles(files: List<File>, autoSend: Boolean = false) {
        pendingLocalFiles.value = (pendingLocalFiles.value + files.filter { it.isFile }).distinctBy { it.absolutePath }
        if (autoSend) autoSendPending.value = true
    }

    fun clearQueuedFiles() { pendingUris.value = emptyList(); pendingLocalFiles.value = emptyList(); autoSendPending.value = false }

    /** Copies picked content URIs into app-private cache, then sends real Nearby file payloads. */
    fun sendQueuedFiles(endpointId: String) = viewModelScope.launch {
        autoSendPending.value = false
        val queued = pendingUris.value
        if (queued.isEmpty() && pendingLocalFiles.value.isEmpty()) return@launch
        val staging = withContext(Dispatchers.IO) {
            val prepared = mutableListOf<Pair<Uri, OutgoingShareFile>>()
            val failed = mutableListOf<Uri>()
            queued.forEach { uri ->
                val result = runCatching {
                    val resolver = context.contentResolver
                    var name = uri.lastPathSegment?.substringAfterLast('/') ?: "shared-file"
                    var size = 0L
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                        }
                    }
                    val mime = resolver.getType(uri) ?: "application/octet-stream"
                    val target = File(context.cacheDir, "outgoing-${System.currentTimeMillis()}-${name.replace(Regex("[^A-Za-z0-9._-]"), "_")}")
                    resolver.openInputStream(uri)?.use { input -> target.outputStream().use(input::copyTo) }
                        ?: error("Unable to read selected file")
                    OutgoingShareFile(target, mime, size.takeIf { it > 0L } ?: target.length(), name)
                }
                result.onSuccess { prepared += uri to it }.onFailure { failed += uri }
            }
            Triple(prepared, failed, pendingLocalFiles.value.filter { it.isFile }.map { file ->
                OutgoingShareFile(file, MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream", file.length(), file.name)
            })
        }
        val outgoing = staging.first.map { it.second } + staging.third
        pendingUris.value = staging.second
        if (outgoing.isEmpty()) return@launch
        outgoing.forEach { (file, mime, bytes, displayName) ->
            val row = TransferEntity(
                direction = "SEND",
                displayName = displayName,
                mime = mime,
                bytes = bytes,
                bytesDone = 0L,
                status = "QUEUED",
                peerName = endpointId,
                localPath = file.absolutePath,
                createdAt = System.currentTimeMillis(),
                favorite = false,
            )
            val rowId = dao.insert(row)
            pendingOutgoingQueue.add(row.copy(id = rowId))
        }
        pumpOutgoingQueue()
        pendingLocalFiles.value = emptyList()
        autoSendPending.value = false
    }

    fun sendQueuedFilesToAll() = viewModelScope.launch {
        val endpoints = nearby.connected.value.toList()
        if (endpoints.isEmpty()) return@launch
        val uris = pendingUris.value
        val files = pendingLocalFiles.value
        if (uris.isEmpty() && files.isEmpty()) return@launch
        autoSendPending.value = false
        endpoints.forEach { endpointId ->
            pendingUris.value = uris
            pendingLocalFiles.value = files
            sendQueuedFiles(endpointId).join()
        }
        pendingUris.value = emptyList()
        pendingLocalFiles.value = emptyList()
        autoSendPending.value = false
    }

    /** Sends the same selected files only to the explicitly chosen live recipients. */
    fun sendQueuedFilesToRecipients(endpointIds: List<String>) = viewModelScope.launch {
        val endpoints = endpointIds.distinct().filter { it in nearby.connected.value }
        if (endpoints.isEmpty()) return@launch
        val uris = pendingUris.value
        val files = pendingLocalFiles.value
        if (uris.isEmpty() && files.isEmpty()) return@launch
        autoSendPending.value = false
        endpoints.forEach { endpointId ->
            pendingUris.value = uris
            pendingLocalFiles.value = files
            sendQueuedFiles(endpointId).join()
        }
        pendingUris.value = emptyList()
        pendingLocalFiles.value = emptyList()
        autoSendPending.value = false
    }

    /**
     * Keeps a small number of Nearby file payloads active at once. This allows
     * multi-select batches to progress concurrently without flooding the
     * connection with hundreds of simultaneous payloads.
     */
    private fun pumpOutgoingQueue() {
        if (!pumpingQueue.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                while (activeResumableTransfers.size < maxConcurrentPayloads) {
                    val connectedNow = nearby.connected.value
                    val next = pendingOutgoingQueue.firstOrNull { it.peerName in connectedNow } ?: break
                    if (!pendingOutgoingQueue.remove(next)) continue
                    val source = next.localPath?.let(::File)
                    if (source == null || !source.isFile || !source.canRead()) {
                        dao.update(next.copy(status = "FAILED_SOURCE_MISSING"))
                        continue
                    }
                    val job = viewModelScope.launch(start = CoroutineStart.LAZY) { sendResumableFile(next, source) }
                    activeResumableTransfers[next.id] = job
                    job.invokeOnCompletion {
                        activeResumableTransfers.remove(next.id, job)
                        pumpOutgoingQueue()
                    }
                    job.start()
                }
            } finally {
                pumpingQueue.set(false)
                if (activeResumableTransfers.size < maxConcurrentPayloads &&
                    pendingOutgoingQueue.any { it.peerName in nearby.connected.value }
                ) pumpOutgoingQueue()
            }
        }
    }

    private suspend fun sendResumableFile(row: TransferEntity, source: File) {
        val endpointId = row.peerName
        var acknowledgedBytes = row.bytesDone
        try {
            val digest = withContext(Dispatchers.IO) {
                FileUtilities.sha256(source).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            }
            val senderId = nearby.localDeviceIdentity().replace("-", "").take(32)
            val transferId = "bq" + senderId + "_" + row.id
            val offer = TransferProtocol.ResumableOffer(transferId, row.displayName, row.mime, source.length(), digest)
            dao.update(row.copy(status = "SENDING", bytesDone = row.bytesDone.coerceAtMost(source.length())))
            val accepted = sendAndAwait(
                endpointId,
                TransferProtocol.frame(TransferProtocol.TYPE_OFFER, TransferProtocol.resumableOfferBody(offer)),
                TransferProtocol.TYPE_ACCEPT,
                transferId,
            )
            var offset = TransferProtocol.parseTransferOffset(accepted.body).nextOffset
            require(offset in 0..source.length()) { "Receiver returned an invalid resume offset." }
            acknowledgedBytes = offset
            dao.update(row.copy(status = "SENDING", bytesDone = offset))
            RandomAccessFile(source, "r").use { input ->
                while (offset < source.length()) {
                    if (endpointId !in nearby.connected.value) throw IllegalStateException("Device disconnected.")
                    val count = minOf(16_000L, source.length() - offset).toInt()
                    val bytes = ByteArray(count)
                    input.seek(offset)
                    input.readFully(bytes)
                    val ack = sendAndAwait(
                        endpointId,
                        TransferProtocol.frame(TransferProtocol.TYPE_CHUNK, TransferProtocol.resumableChunkBody(transferId, offset, bytes)),
                        TransferProtocol.TYPE_ACK,
                        transferId,
                        offset + count,
                    )
                    val nextOffset = TransferProtocol.parseTransferOffset(ack.body).nextOffset
                    require(nextOffset == offset + count) { "Receiver acknowledged an unexpected byte offset." }
                    offset = nextOffset
                    acknowledgedBytes = offset
                    dao.update(row.copy(status = "SENDING", bytesDone = offset))
                }
            }
            val finalAck = sendAndAwait(
                endpointId,
                TransferProtocol.frame(TransferProtocol.TYPE_COMPLETE, TransferProtocol.transferIdBody(transferId)),
                TransferProtocol.TYPE_ACK,
                transferId,
                source.length(),
            )
            require(TransferProtocol.parseTransferOffset(finalAck.body).nextOffset == source.length()) {
                "Receiver did not confirm the complete file."
            }
            dao.update(row.copy(status = "DONE", bytesDone = source.length()))
            if (source.name.startsWith("outgoing-")) withContext(Dispatchers.IO) { source.delete() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            dao.update(row.copy(status = "WAITING_FOR_DEVICE", bytesDone = acknowledgedBytes))
            incomingTransferMessage.value = "Transfer paused. Reconnect to resume from the last verified checkpoint."
        }
    }

    private suspend fun sendAndAwait(
        endpointId: String,
        bytes: ByteArray,
        expectedType: Byte,
        transferId: String,
        expectedOffset: Long? = null,
    ): TransferProtocol.ParsedFrame = coroutineScope {
        val waiter = async(start = CoroutineStart.UNDISPATCHED) {
            awaitTransferControl(endpointId, expectedType, transferId, expectedOffset)
        }
        nearby.sendBytes(endpointId, bytes)
        waiter.await()
    }

    private suspend fun awaitTransferControl(
        endpointId: String,
        expectedType: Byte,
        transferId: String,
        expectedOffset: Long? = null,
    ): TransferProtocol.ParsedFrame = withTimeout(45_000L) {
        val event = nearby.payloads.first { incoming ->
            if (incoming.endpointId != endpointId || incoming.payload.type != Payload.Type.BYTES) return@first false
            val bytes = incoming.payload.asBytes() ?: return@first false
            val parsed = runCatching { TransferProtocol.parse(bytes) }.getOrNull() ?: return@first false
            if (parsed.type != expectedType) return@first false
            val receivedId = runCatching {
                if (expectedType == TransferProtocol.TYPE_ACCEPT || expectedType == TransferProtocol.TYPE_ACK) {
                    TransferProtocol.parseTransferOffset(parsed.body).transferId
                } else {
                    TransferProtocol.parseTransferId(parsed.body)
                }
            }.getOrNull()
            val receivedOffset = if (expectedType == TransferProtocol.TYPE_ACCEPT || expectedType == TransferProtocol.TYPE_ACK) {
                runCatching { TransferProtocol.parseTransferOffset(parsed.body).nextOffset }.getOrNull()
            } else null
            receivedId == transferId && (expectedOffset == null || receivedOffset == expectedOffset)
        }
        TransferProtocol.parse(event.payload.asBytes()!!)
    }

    private suspend fun handleResumableFrame(endpointId: String, bytes: ByteArray): Boolean {
        val frame = runCatching { TransferProtocol.parse(bytes) }.getOrNull() ?: return false
        return try {
            when (frame.type) {
                TransferProtocol.TYPE_OFFER -> {
                    val offer = TransferProtocol.parseResumableOffer(frame.body)
                    incomingTransferMimes[offer.transferId] = offer.mime
                    val offset = withContext(Dispatchers.IO) {
                        resumableReceiver.begin(ResumableFileReceiver.Offer(
                            transferId = offer.transferId,
                            displayName = offer.displayName,
                            totalBytes = offer.totalBytes,
                            sha256 = offer.sha256,
                        ))
                    }
                    nearby.sendBytes(endpointId, TransferProtocol.frame(
                        TransferProtocol.TYPE_ACCEPT,
                        TransferProtocol.transferOffsetBody(offer.transferId, offset),
                    ))
                    true
                }
                TransferProtocol.TYPE_CHUNK -> {
                    val chunk = TransferProtocol.parseResumableChunk(frame.body)
                    val nextOffset = withContext(Dispatchers.IO) {
                        resumableReceiver.appendChunk(chunk.transferId, chunk.offset, chunk.data, chunk.sha256)
                    }
                    nearby.sendBytes(endpointId, TransferProtocol.frame(
                        TransferProtocol.TYPE_ACK,
                        TransferProtocol.transferOffsetBody(chunk.transferId, nextOffset),
                    ))
                    true
                }
                TransferProtocol.TYPE_COMPLETE -> {
                    val transferId = TransferProtocol.parseTransferId(frame.body)
                    val completed = withContext(Dispatchers.IO) { resumableReceiver.complete(transferId) }
                    try {
                        val name = FileUtilities.safeFileName(completed.name)
                        val mime = incomingTransferMimes.remove(transferId)
                            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(completed.extension.lowercase())
                            ?: "application/octet-stream"
                        val kind = kindForMime(mime, name)
                        val directory = File(context.getExternalFilesDir(null), folderFor(kind))
                        val destination = withContext(Dispatchers.IO) {
                            FileUtilities.copyToUniqueDestination(completed, directory, name)
                        }
                        record(destination.name, mime, destination.length(), "RECEIVE", endpointId, destination.absolutePath)
                        incomingTransferMessage.value = "Received and verified: " + destination.name
                        nearby.sendBytes(endpointId, TransferProtocol.frame(
                            TransferProtocol.TYPE_ACK,
                            TransferProtocol.transferOffsetBody(transferId, completed.length()),
                        ))
                    } finally {
                        withContext(Dispatchers.IO) { completed.delete() }
                    }
                    true
                }
                TransferProtocol.TYPE_CANCEL -> {
                    val transferId = TransferProtocol.parseTransferId(frame.body)
                    withContext(Dispatchers.IO) { resumableReceiver.discard(transferId) }
                    incomingTransferMimes.remove(transferId)
                    true
                }
                else -> false
            }
        } catch (failure: Exception) {
            incomingTransferMessage.value = failure.message?.take(120) ?: "Could not process resumable transfer."
            true
        }
    }

    fun cancelTransfer(item: TransferEntity) = viewModelScope.launch {
        val active = activeResumableTransfers.remove(item.id)
        if (active != null) {
            active.cancel()
            if (item.peerName in nearby.connected.value) {
                val senderId = nearby.localDeviceIdentity().replace("-", "").take(32)
                val transferId = "bq" + senderId + "_" + item.id
                nearby.sendBytes(item.peerName, TransferProtocol.frame(
                    TransferProtocol.TYPE_CANCEL,
                    TransferProtocol.transferIdBody(transferId),
                ))
            }
            dao.update(item.copy(status = "CANCELLED"))
        } else if (pendingOutgoingQueue.removeIf { it.id == item.id }) {
            if (item.peerName in nearby.connected.value) {
                val senderId = nearby.localDeviceIdentity().replace("-", "").take(32)
                val transferId = "bq" + senderId + "_" + item.id
                nearby.sendBytes(item.peerName, TransferProtocol.frame(
                    TransferProtocol.TYPE_CANCEL,
                    TransferProtocol.transferIdBody(transferId),
                ))
            }
            dao.update(item.copy(status = "CANCELLED"))
            item.localPath?.let { path ->
                val file = File(path)
                if (file.name.startsWith("outgoing-")) runCatching { file.delete() }
            }
        }
        pumpOutgoingQueue()
    }

    fun retryTransfer(item: TransferEntity) = retryTransfer(item, item.peerName)

    fun retryTransfer(item: TransferEntity, endpointId: String) = viewModelScope.launch {
        if (!item.direction.equals("SEND", ignoreCase = true)) return@launch
        if (endpointId !in nearby.connected.value) {
            dao.update(item.copy(status = "WAITING_FOR_DEVICE"))
            return@launch
        }
        val source = item.localPath?.let(::File)
        if (source == null || !source.isFile || !source.canRead()) {
            dao.update(item.copy(status = "FAILED_SOURCE_MISSING", bytesDone = 0L))
            return@launch
        }
        val alreadyQueued = pendingOutgoingQueue.any { it.id == item.id }
        val alreadyActive = activeResumableTransfers.containsKey(item.id)
        if (alreadyQueued || alreadyActive) return@launch
        val retryRow = item.copy(
            peerName = endpointId,
            status = "QUEUED",
            createdAt = System.currentTimeMillis(),
        )
        dao.update(retryRow)
        pendingOutgoingQueue.add(retryRow)
        pumpOutgoingQueue()
    }

    fun sendText(endpointId: String, text: String) {
        val secret = SessionCrypto.encrypt(sessionKey, text.toByteArray())
        nearby.sendBytes(endpointId, secret)
        record("clipboard.txt", "text/plain", text.length.toLong(), "SEND", endpointId, null)
    }
}
