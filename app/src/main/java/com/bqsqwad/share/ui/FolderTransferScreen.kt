package com.bqsqwad.share.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.NavHostController
import com.bqsqwad.share.ShareViewModel
import com.bqsqwad.share.files.FileUtilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Composable
fun FolderTransferScreen(nav: NavHostController, vm: ShareViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Choose a folder. BQ Sqwad Share will package its contents into one ZIP while preserving subfolders.") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        status = "Preparing folder archive…"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val archive = File(context.cacheDir, "outgoing-folder-${System.currentTimeMillis()}.zip")
                    zipDocumentTree(context, uri, archive)
                    archive
                }
            }
            busy = false
            result.fold(
                { archive ->
                    vm.queueLocalFiles(listOf(archive))
                    status = "Folder packaged (${archive.length()} bytes). Open Send to choose a connected device."
                    nav.navigate("send")
                },
                { error -> status = error.message ?: "Could not package this folder." },
            )
        }
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Folder Transfer", style = MaterialTheme.typography.headlineSmall)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(status)
                Button(
                    onClick = { picker.launch(null) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (busy) "Preparing…" else "Choose folder to share") }
                OutlinedButton(
                    onClick = { nav.navigate("send") },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Open Send") }
            }
        }
        Text("The selected folder is archived locally before sending. No cloud upload is used. The receiving device gets a ZIP file and can extract it using the app's archive tools.")
    }
}

private fun zipDocumentTree(context: Context, treeUri: Uri, destination: File): File {
    val root = DocumentFile.fromTreeUri(context, treeUri)
        ?: error("Android could not open the selected folder.")
    require(root.isDirectory && root.canRead()) { "The selected folder is not readable." }
    val visited = mutableSetOf<String>()
    val usedPaths = mutableSetOf<String>()
    var entries = 0
    var totalBytes = 0L

    fun uniqueName(parent: String, rawName: String, directory: Boolean): String {
        val safe = FileUtilities.safeFileName(rawName)
        val base = if (directory) safe else safe.substringBeforeLast('.', safe)
        val extension = if (!directory && safe.contains('.')) ".${safe.substringAfterLast('.')}" else ""
        var candidate = if (parent.isEmpty()) safe else "$parent/$safe"
        var index = 2
        while (candidate in usedPaths) {
            val renamed = "$base ($index)$extension"
            candidate = if (parent.isEmpty()) renamed else "$parent/$renamed"
            index++
        }
        usedPaths += candidate
        return candidate
    }

    ZipOutputStream(BufferedOutputStream(FileOutputStream(destination))).use { zip ->
        fun walk(folder: DocumentFile, parentPath: String, depth: Int) {
            require(depth <= 64) { "The selected folder is nested too deeply." }
            val folderUri = folder.uri.toString()
            require(visited.add(folderUri)) { "The selected folder contains a repeated directory reference." }
            val children = folder.listFiles()
            for (child in children) {
                entries++
                require(entries <= 100_000) { "The folder contains too many items to archive." }
                val name = uniqueName(parentPath, child.name ?: "item-$entries", child.isDirectory)
                if (child.isDirectory) {
                    zip.putNextEntry(ZipEntry("$name/"))
                    zip.closeEntry()
                    walk(child, name, depth + 1)
                } else if (child.isFile) {
                    zip.putNextEntry(ZipEntry(name))
                    val input = context.contentResolver.openInputStream(child.uri)
                        ?: error("Unable to read ${child.name ?: "a selected file"}.")
                    BufferedInputStream(input).use { source ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = source.read(buffer)
                            if (count < 0) break
                            totalBytes += count
                            require(totalBytes <= 2L * 1024 * 1024 * 1024) {
                                "The selected folder exceeds the 2 GB archive safety limit."
                            }
                            zip.write(buffer, 0, count)
                        }
                    }
                    zip.closeEntry()
                }
            }
        }
        walk(root, "", 0)
    }
    check(destination.isFile && destination.length() > 0L) { "The selected folder was empty or could not be archived." }
    return destination
}
