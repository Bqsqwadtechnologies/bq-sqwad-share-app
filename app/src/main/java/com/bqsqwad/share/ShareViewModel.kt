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
import com.bqsqwad.share.common.folderFor
import com.bqsqwad.share.common.kindForMime
import com.bqsqwad.share.data.NearbyTransport
import com.bqsqwad.share.data.SettingsStore
import com.bqsqwad.share.data.TransferDao
import com.bqsqwad.share.data.TransferEntity
import com.bqsqwad.share.data.WebShareServer
import com.bqsqwad.share.domain.Peer
import com.bqsqwad.share.transfer.TransferService
import com.google.android.gms.nearby.connection.Payload
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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

    val sessionKey = SessionCrypto.newKey()
    var sessionCode: String = SessionCrypto.sixDigitCode()
        private set
    val receiveExpiresAt = MutableStateFlow(0L)
    val pendingUris = MutableStateFlow<List<Uri>>(emptyList())
    val pendingLocalFiles = MutableStateFlow<List<File>>(emptyList())
    val autoSendPending = MutableStateFlow(false)
    val scannedReceiveCode = MutableStateFlow("")
    val verification = SessionCrypto.fingerprint(sessionKey)
    val keyText = SessionCrypto.keyToString(sessionKey)
    private val outgoingPayloadRecords = java.util.concurrent.ConcurrentHashMap<Long, TransferEntity>()
    private val outgoingPayloadIds = java.util.concurrent.ConcurrentHashMap<Long, Long>()

    init {
        val activePayloadIds = mutableSetOf<Long>()
        viewModelScope.launch {
            nearby.progress.collect { progress ->
                if (progress.status == 2) {
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
                    if (outgoing != null) {\n                        outgoingPayloadIds.remove(outgoing.id)\n
                        val succeeded = progress.status == 1
                        viewModelScope.launch {
                            dao.update(
                                outgoing.copy(
                                    status = if (succeeded) "DONE" else "FAILED",
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
                }
                val complete = progress.status != 2 && activePayloadIds.isEmpty()
                val text = if (complete) {
                    if (progress.status == 1) "Transfer completed" else "Transfer ended"
                } else {
                    "Transferring ${progress.bytes} of ${progress.total} bytes"
                }
                TransferService.update(context, text, progress.bytes, progress.total, complete)
            }
        }
        val pendingIncoming = java.util.concurrent.ConcurrentHashMap<Long, Pair<String, File>>()
        val completedIncoming = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

        fun persistCompletedPayload(payloadId: Long) {
            val pending = pendingIncoming.remove(payloadId) ?: return
            completedIncoming.remove(payloadId)
            val (endpointId, incomingFile) = pending
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val name = incomingFile.name.ifBlank { "received-file" }
                        val extension = name.substringAfterLast('.', "").lowercase()
                        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
                        val kind = kindForMime(mime, name)
                        val directory = File(context.getExternalFilesDir(null), folderFor(kind)).apply { mkdirs() }
                        val destination = File(directory, name).let { file ->
                            if (!file.exists()) file else File(directory, "${System.currentTimeMillis()}-$name")
                        }
                        incomingFile.copyTo(destination, overwrite = true)
                        record(name, mime, destination.length(), "RECEIVE", endpointId, destination.absolutePath)
                    }
                }
            }
        }

        viewModelScope.launch {
            nearby.payloads.collect { event ->
                val payload = event.payload
                if (payload.type == Payload.Type.FILE) {
                    val incomingFile = runCatching { payload.asFile()?.asJavaFile() }.getOrNull() ?: return@collect
                    pendingIncoming[payload.id] = event.endpointId to incomingFile
                    if (completedIncoming.remove(payload.id)) persistCompletedPayload(payload.id)
                }
            }
        }

        viewModelScope.launch {
            nearby.progress.collect { event ->
                if (event.status == 1) {
                    if (pendingIncoming.containsKey(event.payloadId)) {
                        persistCompletedPayload(event.payloadId)
                    } else {
                        completedIncoming.add(event.payloadId)
                    }
                } else if (event.status != 2) {
                    pendingIncoming.remove(event.payloadId)?.second?.let { file ->
                        runCatching { file.delete() }
                    }
                    completedIncoming.remove(event.payloadId)
                }
            }
        }
    }

    var web: WebShareServer? = null
        private set

    /** Creates a fresh receive code each time the Receive screen is opened or regenerated. */
    fun startReceiveSession() {
        val previous = sessionCode
        var next = SessionCrypto.sixDigitCode()
        while (next == previous) next = SessionCrypto.sixDigitCode()
        sessionCode = next
        nearby.stopAdvertising()
        nearby.setExpectedReceiveCode(next)
        receiveExpiresAt.value = System.currentTimeMillis() + 10 * 60 * 1000L
        nearby.startAdvertising("BQ Sqwad Share $next")
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
        val outgoing = withContext(Dispatchers.IO) {
            queued.mapNotNull { uri ->
                runCatching {
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
                }.getOrNull()
            } + pendingLocalFiles.value.filter { it.isFile }.map { file ->
                OutgoingShareFile(file, MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream", file.length(), file.name)
            }
        }
        if (outgoing.isEmpty()) return@launch
        outgoing.forEach { (file, mime, bytes, displayName) ->
            val row = TransferEntity(
                direction = "SEND",
                displayName = displayName,
                mime = mime,
                bytes = bytes,
                bytesDone = 0L,
                status = "SENDING",
                peerName = endpointId,
                localPath = file.absolutePath,
                createdAt = System.currentTimeMillis(),
                favorite = false,
            )
            val rowId = dao.insert(row)
            val payloadId = try {
                nearby.sendFile(endpointId, file)
            } catch (_: Exception) {
                dao.update(row.copy(id = rowId, status = "FAILED"))
                if (file.name.startsWith("outgoing-")) runCatching { file.delete() }
                return@forEach
            }
            outgoingPayloadRecords[payloadId] = row.copy(id = rowId)
            outgoingPayloadIds[rowId] = payloadId
        }
        pendingUris.value = emptyList()
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

    fun cancelTransfer(item: TransferEntity) = viewModelScope.launch {
        val payloadId = outgoingPayloadIds.remove(item.id) ?: return@launch
        outgoingPayloadRecords.remove(payloadId)
        nearby.cancelPayload(payloadId)
        dao.update(item.copy(status = "CANCELLED"))
    }

    fun retryTransfer(item: TransferEntity) = viewModelScope.launch {
        if (!item.direction.equals("SEND", ignoreCase = true)) return@launch
        val connected = nearby.connected.value
        val endpointId = item.peerName.takeIf { it in connected } ?: connected.firstOrNull()
        if (endpointId == null) {
            dao.update(item.copy(status = "WAITING_FOR_DEVICE", bytesDone = 0L))
            return@launch
        }
        val source = item.localPath?.let(::File)
        if (source == null || !source.isFile || !source.canRead()) {
            dao.update(item.copy(status = "FAILED_SOURCE_MISSING", bytesDone = 0L))
            return@launch
        }
        try {
            val payloadId = nearby.sendFile(endpointId, source)
            val retryRow = item.copy(status = "SENDING", bytesDone = 0L, createdAt = System.currentTimeMillis())
            dao.update(retryRow)
            outgoingPayloadRecords[payloadId] = retryRow
            outgoingPayloadIds[item.id] = payloadId
        } catch (_: Exception) {
            dao.update(item.copy(status = "FAILED", bytesDone = 0L))
        }
    }

    fun sendText(endpointId: String, text: String) {
        val secret = SessionCrypto.encrypt(sessionKey, text.toByteArray())
        nearby.sendBytes(endpointId, secret)
        record("clipboard.txt", "text/plain", text.length.toLong(), "SEND", endpointId, null)
    }
}
