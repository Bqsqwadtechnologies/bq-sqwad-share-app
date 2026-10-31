package com.bqsqwad.share

import android.content.Context
import android.content.Intent
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
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    val sessionCode = SessionCrypto.sixDigitCode()
    val verification = SessionCrypto.fingerprint(sessionKey)
    val keyText = SessionCrypto.keyToString(sessionKey)

    var web: WebShareServer? = null
        private set

    fun confirmAge() = viewModelScope.launch { store.setAgeConfirmed() }
    fun theme(mode: String) = viewModelScope.launch { store.setTheme(mode) }
    fun accent(name: String) = viewModelScope.launch { store.setAccent(name) }
    fun haptics(on: Boolean) = viewModelScope.launch { store.setHaptics(on) }
    fun motion(on: Boolean) = viewModelScope.launch { store.setReduceMotion(on) }
    fun notif(on: Boolean) = viewModelScope.launch { store.setNotifications(on) }
    fun adFreeDay() = viewModelScope.launch { store.grantAdFree(System.currentTimeMillis() + 86_400_000L) }
    fun premiumDay() = viewModelScope.launch { store.grantPremium(System.currentTimeMillis() + 86_400_000L) }
    fun block(name: String) = viewModelScope.launch { store.block(name) }
    fun nickname(id: String, name: String) = viewModelScope.launch { store.setNickname(id, name) }
    fun clearHistory() = viewModelScope.launch { dao.clear() }

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

    fun sendText(endpointId: String, text: String) {
        val secret = SessionCrypto.encrypt(sessionKey, text.toByteArray())
        nearby.sendBytes(endpointId, secret)
        record("clipboard.txt", "text/plain", text.length.toLong(), "SEND", endpointId, null)
    }
}
