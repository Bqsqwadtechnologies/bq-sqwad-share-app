package com.bqsqwad.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
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
    val verification = SessionCrypto.fingerprint(sessionKey)
    val keyText = SessionCrypto.keyToString(sessionKey)

    init {
        viewModelScope.launch {
            nearby.progress.collect { event ->
                val complete = event.status != 2 // Nearby PayloadTransferUpdate.Status.IN_PROGRESS
                val text = if (complete) {
                    if (event.status == 1) "Transfer completed" else "Transfer ended"
                } else {
                    "Transferring ${event.bytes} of ${event.total} bytes"
                }
                TransferService.update(context, text, event.bytes, event.total, complete)
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
        receiveExpiresAt.value = System.currentTimeMillis() + 10 * 60 * 1000L
        nearby.startAdvertising("BQ Sqwad Share")
    }

    fun expireReceiveSessionIfNeeded(now: Long = System.currentTimeMillis()) {
        if (receiveExpiresAt.value > 0L && now >= receiveExpiresAt.value) {
            sessionCode = ""
            nearby.stopAdvertising()
        }
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

    fun queueFiles(uris: List<Uri>) {
        pendingUris.value = (pendingUris.value + uris).distinct()
    }

    fun clearQueuedFiles() { pendingUris.value = emptyList() }

    /** Copies picked content URIs into app-private cache, then sends real Nearby file payloads. */
    fun sendQueuedFiles(endpointId: String) = viewModelScope.launch {
        val queued = pendingUris.value
        if (queued.isEmpty()) return@launch
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
                    Triple(target, mime, size.takeIf { it > 0L } ?: target.length())
                }.getOrNull()
            }
        }
        if (outgoing.isEmpty()) return@launch
        outgoing.forEach { (file, mime, bytes) ->
            nearby.sendFile(endpointId, file)
            record(file.name.removePrefix(Regex("outgoing-[0-9]+-").toString()), mime, bytes, "SEND", endpointId, file.absolutePath)
        }
        pendingUris.value = emptyList()
    }

    fun sendText(endpointId: String, text: String) {
        val secret = SessionCrypto.encrypt(sessionKey, text.toByteArray())
        nearby.sendBytes(endpointId, secret)
        record("clipboard.txt", "text/plain", text.length.toLong(), "SEND", endpointId, null)
    }
}
