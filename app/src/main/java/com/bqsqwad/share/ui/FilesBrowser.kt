package com.bqsqwad.share.ui

import android.content.Intent
import android.net.Uri
import java.io.File
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.OpenableColumns
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.core.graphics.drawable.toBitmap
import com.bqsqwad.share.ShareViewModel
import com.bqsqwad.share.files.FileUtilities
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilesBrowser(nav: NavHostController, vm: ShareViewModel, initialTab: String = "apps") {
    val context = LocalContext.current
    val videoImageLoader = remember(context) { ImageLoader.Builder(context).components { add(VideoFrameDecoder.Factory()) }.build() }
    val haptic = LocalHapticFeedback.current
    val history by vm.history.collectAsState()
    val tabs = listOf("Apps", "Photos", "Videos", "Music", "Documents", "Files", "Received", "Sent")
    var tab by remember(initialTab) { mutableStateOf(initialTab.lowercase().let { key -> when (key) { "photos", "pictures" -> "Photos"; "videos" -> "Videos"; "music" -> "Music"; "documents" -> "Documents"; "files" -> "Files"; "received" -> "Received"; "sent" -> "Sent"; else -> "Apps" } }) }
    var systemApps by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }
    var media by remember { mutableStateOf(emptyList<ShareMediaItem>()) }
    var apps by remember { mutableStateOf(emptyList<ShareAppItem>()) }
    val selected = remember { mutableStateMapOf<String, Long>() }
    val selectedUris = remember { mutableStateMapOf<String, Uri>() }
    val selectedFiles = remember { mutableStateMapOf<String, List<File>>() }
    var shareDialog by remember { mutableStateOf(false) }
    var previewItem by remember { mutableStateOf<ShareMediaItem?>(null) }
    var appInfo by remember { mutableStateOf<ShareAppItem?>(null) }
    var archiving by remember { mutableStateOf(false) }
    var extracting by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var sortBySize by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pickDeviceFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            val key = uri.toString()
            var size = 0L
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) size = cursor.getLong(sizeColumn)
                    }
                }
            }
            selected[key] = size
            selectedUris[key] = uri
        }
    }
    val requestMedia = rememberMediaPermissionRequest(tab) { refresh++ }
    val visibleApps = remember(apps, searchText) { apps.filter { it.label.contains(searchText.trim(), true) || it.packageName.contains(searchText.trim(), true) } }
    val visibleMedia = remember(media, searchText, sortBySize) {
        media.filter { it.name.contains(searchText.trim(), ignoreCase = true) }
            .let { rows -> if (sortBySize) rows.sortedByDescending { it.sizeBytes } else rows.sortedBy { it.name.lowercase() } }
    }
    val received = when (tab) {
        "Received" -> history.filter { it.direction.equals("RECEIVE", true) && it.status != "DELETED" }
        "Sent" -> history.filter { it.direction.equals("SEND", true) && it.status != "DELETED" }
        else -> emptyList()
    }

    LaunchedEffect(tab, systemApps, refresh) {
        when (tab) {
            "Apps" -> apps = withContext(Dispatchers.IO) { MediaRepository.loadApps(context, systemApps) }
            "Received", "Sent" -> media = emptyList()
            else -> media = withContext(Dispatchers.IO) { MediaRepository.load(context, tab) }
        }
    }

    fun select(key: String, size: Long) {
        if (selected.containsKey(key)) selected.remove(key) else selected[key] = size
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun selectLocal(key: String, files: List<File>, size: Long) {
        val wasSelected = selected.containsKey(key)
        select(key, size)
        if (wasSelected) selectedFiles.remove(key) else selectedFiles[key] = files.filter { it.isFile }
    }

    BackHandler(enabled = selected.isNotEmpty()) { selected.clear(); selectedUris.clear(); selectedFiles.clear() }

    val mediaPermissionNeeded = tab in listOf("Photos", "Videos", "Music") && !MediaPermissions.hasMedia(context, tab)
    val allFilesNeeded = tab in listOf("Files", "Documents") && !MediaPermissions.hasFull(context)

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tabs.forEach { label ->
                FilterChip(
                    selected = tab == label,
                    onClick = { tab = label; selected.clear(); selectedUris.clear(); selectedFiles.clear() },
                    label = { Text(label) },
                    leadingIcon = {
                        val icon = when (label) {
                            "Apps" -> androidx.compose.material.icons.Icons.Filled.Apps
                            "Photos" -> androidx.compose.material.icons.Icons.Filled.PhotoLibrary
                            "Videos" -> androidx.compose.material.icons.Icons.Filled.VideoLibrary
                            "Music" -> androidx.compose.material.icons.Icons.Filled.MusicNote
                            "Received" -> androidx.compose.material.icons.Icons.Filled.Download
                            "Sent" -> androidx.compose.material.icons.Icons.Filled.Send
                            else -> androidx.compose.material.icons.Icons.Filled.FolderOpen
                        }
                        androidx.compose.material3.Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                )
            }
        }
        if (tab != "Received" && tab != "Sent") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Search ${tab.lowercase()}") },
                )
                FilterChip(
                    selected = sortBySize,
                    onClick = { sortBySize = !sortBySize },
                    label = { Text(if (sortBySize) "Largest" else "A–Z") },
                )
            }
        }
        if (tab == "Apps") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !systemApps, onClick = { systemApps = false; selected.clear(); selectedFiles.clear() }, label = { Text("Installed apps") })
                    FilterChip(selected = systemApps, onClick = { systemApps = true; selected.clear(); selectedFiles.clear() }, label = { Text("System apps") })
                }
                Text(
                    if (selected.isEmpty()) "Hold an app to select it. Then tap other apps to add them."
                    else "Tap more apps to select them, then choose Send below.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        when {
            mediaPermissionNeeded -> Column(Modifier.padding(20.dp)) {
                Text("Allow media access to browse $tab.")
                Button(onClick = requestMedia) { Text("Grant access") }
            }
            allFilesNeeded -> Column(Modifier.padding(20.dp)) {
                Text("Documents and full Files browsing are not enabled because this version uses scoped storage. Photos, Videos, Music, Apps, Received and Sent remain available.")
            }
            else -> LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                if (tab !in listOf("Received", "Sent") && (visibleMedia.isNotEmpty() || visibleApps.isNotEmpty())) {
                    item(key = "bulk-selection") {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                selected.size.toString() + " selected · " + MediaRepository.formatBytes(selected.values.sum()),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TextButton(onClick = {
                                if (tab == "Apps") {
                                    visibleApps.forEach { app ->
                                        val files = app.apkPaths.map(::File).filter { it.isFile }
                                        selected[app.packageName] = files.sumOf { it.length() }
                                        selectedFiles[app.packageName] = files
                                    }
                                } else {
                                    visibleMedia.forEach { item ->
                                        selected[item.key] = item.sizeBytes
                                        selectedUris[item.key] = item.uri
                                    }
                                }
                            }) { Text("Select all") }
                            TextButton(onClick = { selected.clear(); selectedUris.clear(); selectedFiles.clear() }) { Text("Clear") }
                        }
                    }
                }
                if (tab == "Apps") {
                    items(visibleApps, key = { it.packageName }) { app ->
                        val isSelected = selected.containsKey(app.packageName)
                        val appIcon = remember(app.packageName) {
                            runCatching { context.packageManager.getApplicationIcon(app.packageName).toBitmap().asImageBitmap() }.getOrNull()
                        }
                        Card(Modifier.fillMaxWidth().padding(vertical = 3.dp).combinedClickable(
                            onClick = {
                                val appFiles = app.apkPaths.map(::File)
                                val appSize = app.apkPaths.sumOf { runCatching { File(it).length() }.getOrDefault(0L) }
                                if (selected.isNotEmpty()) selectLocal(app.packageName, appFiles, appSize)
                                else appInfo = app
                            },
                            onLongClick = {
                                if (!isSelected) selectLocal(
                                    app.packageName,
                                    app.apkPaths.map(::File),
                                    app.apkPaths.sumOf { runCatching { File(it).length() }.getOrDefault(0L) },
                                )
                            },
                        )) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (appIcon != null) {
                                    Image(appIcon, contentDescription = app.label, modifier = Modifier.size(48.dp))
                                } else {
                                    androidx.compose.material3.Icon(
                                        androidx.compose.material.icons.Icons.Filled.Apps,
                                        contentDescription = "App icon",
                                        modifier = Modifier.size(48.dp),
                                    )
                                }
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(app.label, style = MaterialTheme.typography.titleSmall)
                                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                                }
                                if (isSelected) Text("✓", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                } else if (tab == "Received" || tab == "Sent") {
                    items(received, key = { it.id }) { item ->
                        val key = "transfer:${item.id}"
                        val isSelected = selected.containsKey(key)
                        Card(Modifier.fillMaxWidth().padding(vertical = 3.dp).combinedClickable(
                            onClick = { selectLocal(key, listOfNotNull(item.localPath?.let(::File)), item.bytes) },
                            onLongClick = { if (!isSelected) selectLocal(key, listOfNotNull(item.localPath?.let(::File)), item.bytes) },
                        )) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.displayName, style = MaterialTheme.typography.titleSmall)
                                    Text(MediaRepository.formatBytes(item.bytes), style = MaterialTheme.typography.bodySmall)
                                }
                                if (isSelected) Text("✓")
                            }
                        }
                    }
                } else {
                    items(visibleMedia, key = { it.key }) { item ->
                        val isSelected = selected.containsKey(item.key)
                        Card(Modifier.fillMaxWidth().padding(vertical = 3.dp).combinedClickable(
                            onClick = {
                                if (selected.isNotEmpty() || selected.containsKey(item.key)) {
                                    val wasSelected = selected.containsKey(item.key)
                                    select(item.key, item.sizeBytes)
                                    if (wasSelected) selectedUris.remove(item.key) else selectedUris[item.key] = item.uri
                                } else if (tab == "Photos") {
                                    previewItem = item
                                } else runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(item.uri, item.mimeType)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                                }.onFailure { Toast.makeText(context, "No compatible app could open this item.", Toast.LENGTH_SHORT).show() }
                            },
                            onLongClick = { if (!isSelected) { select(item.key, item.sizeBytes); selectedUris[item.key] = item.uri } },
                        )) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (tab == "Photos") {
                                    AsyncImage(
                                        model = item.uri,
                                        contentDescription = item.name,
                                        modifier = Modifier.size(88.dp),
                                        contentScale = ContentScale.Crop,
                                    )
                                } else if (tab == "Videos") {
                                    AsyncImage(
                                        model = item.uri,
                                        imageLoader = videoImageLoader,
                                        contentDescription = item.name,
                                        modifier = Modifier.size(88.dp),
                                        contentScale = ContentScale.Crop,
                                    )
                                } else {
                                    androidx.compose.material3.Icon(
                                        imageVector = when (tab) {
                                            "Music" -> androidx.compose.material.icons.Icons.Filled.MusicNote
                                            else -> androidx.compose.material.icons.Icons.Filled.FolderOpen
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(42.dp),
                                    )
                                }
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                                    Text(MediaRepository.formatBytes(item.sizeBytes) + " · " + item.mimeType, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                }
                                if (isSelected) Text("✓", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    if (visibleMedia.isEmpty()) item { Text(if (searchText.isBlank()) "No items found in $tab." else "No matches for your search.", Modifier.padding(16.dp)) }
                }
            }
        }
        if (selected.isNotEmpty()) {
            Card(Modifier.fillMaxWidth().padding(12.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("${selected.size} selected", style = MaterialTheme.typography.titleSmall)
                        Text(MediaRepository.formatBytes(selected.values.sum()), style = MaterialTheme.typography.bodySmall)
                    }
                    val selectedZip = selectedFiles.values.flatten().singleOrNull()?.takeIf {
                        it.isFile && it.extension.equals("zip", ignoreCase = true)
                    }
                    if (selectedZip != null) {
                        TextButton(
                            enabled = !extracting,
                            onClick = {
                                scope.launch {
                                    extracting = true
                                    try {
                                        val extracted = withContext(Dispatchers.IO) {
                                            val destination = File(
                                                File(context.getExternalFilesDir(null), "Extracted"),
                                                "${selectedZip.nameWithoutExtension}-${System.currentTimeMillis()}",
                                            )
                                            FileUtilities.extractZip(selectedZip, destination)
                                        }
                                        Toast.makeText(
                                            context,
                                            "Extracted ${extracted.size} file(s) to the app's Extracted folder.",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    } catch (error: Exception) {
                                        Toast.makeText(context, error.message ?: "Could not extract archive.", Toast.LENGTH_LONG).show()
                                    } finally {
                                        extracting = false
                                    }
                                }
                            },
                        ) { Text(if (extracting) "Extracting…" else "Unzip") }
                    }
                    TextButton(
                        enabled = !archiving && !extracting,
                        onClick = {
                            scope.launch {
                                archiving = true
                                val staged = mutableListOf<File>()
                                try {
                                    val inputs = withContext(Dispatchers.IO) {
                                        val local = selectedFiles.values.flatten().filter { it.isFile }
                                        val copied = selectedUris.values.mapIndexedNotNull { index, uri ->
                                            runCatching {
                                                val name = (uri.lastPathSegment?.substringAfterLast('/') ?: "shared-$index")
                                                    .replace(Regex("[^A-Za-z0-9._-]"), "_")
                                                val target = File(context.cacheDir, "archive-source-${System.currentTimeMillis()}-$index-$name")
                                                context.contentResolver.openInputStream(uri)?.use { input ->
                                                    target.outputStream().use { output -> input.copyTo(output) }
                                                } ?: error("Unable to read selected item")
                                                staged += target
                                                target
                                            }.getOrNull()
                                        }
                                        (local + copied).distinctBy { it.absolutePath }
                                    }
                                    if (inputs.isEmpty()) error("No selected files could be read.")
                                    val archiveDirectory = File(context.getExternalFilesDir(null), "Archives")
                                    val archive = FileUtilities.createZip(
                                        inputs,
                                        File(archiveDirectory, "BQ-Share-${System.currentTimeMillis()}.zip"),
                                    )
                                    selected.clear()
                                    selectedUris.clear()
                                    selectedFiles.clear()
                                    selected[archive.absolutePath] = archive.length()
                                    selectedFiles[archive.absolutePath] = listOf(archive)
                                    Toast.makeText(context, "Archive created. Tap Send to share it.", Toast.LENGTH_LONG).show()
                                } catch (error: Exception) {
                                    Toast.makeText(context, error.message ?: "Could not create archive.", Toast.LENGTH_LONG).show()
                                } finally {
                                    withContext(Dispatchers.IO) { staged.forEach { runCatching { it.delete() } } }
                                    archiving = false
                                }
                            }
                        },
                    ) { Text(if (archiving) "Creating…" else "ZIP") }
                    TextButton(onClick = { selected.clear(); selectedUris.clear(); selectedFiles.clear() }) { Text("Clear") }
                    Button(onClick = { shareDialog = true }) { Text("Send") }
                }
            }
        }
    }

    if (appInfo != null) {
        AlertDialog(
            onDismissRequest = { appInfo = null },
            title = { Text(appInfo?.label.orEmpty()) },
            text = {
                Text("Hold this app in the list to select it. You can select several installed apps and send their app files to another nearby phone. This does not open the Play Store.")
            },
            confirmButton = {
                TextButton(onClick = {
                    appInfo?.let { app ->
                        selectLocal(
                            app.packageName,
                            app.apkPaths.map(::File),
                            app.apkPaths.sumOf { runCatching { File(it).length() }.getOrDefault(0L) },
                        )
                    }
                    appInfo = null
                }) { Text("Select app") }
            },
            dismissButton = { TextButton(onClick = { appInfo = null }) { Text("Close") } },
        )
    }

    if (previewItem != null) {
        AlertDialog(
            onDismissRequest = { previewItem = null },
            title = { Text(previewItem?.name.orEmpty()) },
            text = {
                AsyncImage(
                    model = previewItem?.uri,
                    contentDescription = previewItem?.name,
                    modifier = Modifier.fillMaxWidth().height(380.dp),
                    contentScale = ContentScale.Fit,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    previewItem?.let { item ->
                        select(item.key, item.sizeBytes)
                        selectedUris[item.key] = item.uri
                    }
                    previewItem = null
                }) { Text("Select photo") }
            },
            dismissButton = { TextButton(onClick = { previewItem = null }) { Text("Close") } },
        )
    }

    if (shareDialog) {
        AlertDialog(
            onDismissRequest = { shareDialog = false },
            title = { Text("Send selected items") },
            text = { Text("Continue to nearby devices with ${selected.size} selected item(s).") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = {
                        vm.queueFiles(selectedUris.values.toList(), autoSend = true)
                        vm.queueLocalFiles(selectedFiles.values.flatten(), autoSend = true)
                        shareDialog = false
                        nav.navigate("send")
                    }) { Text("Send selected") }
                }
            },
            dismissButton = { TextButton(onClick = { shareDialog = false }) { Text("Cancel") } },
        )
    }
}
