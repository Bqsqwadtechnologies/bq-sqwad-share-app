package com.bqsqwad.share.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import android.graphics.drawable.toBitmap
import com.bqsqwad.share.ShareViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FilesBrowser(nav: NavHostController, vm: ShareViewModel) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val history by vm.history.collectAsState()
    val tabs = listOf("Apps", "Photos", "Videos", "Music", "Documents", "Files", "Received", "Sent")
    var tab by remember { mutableStateOf("Apps") }
    var systemApps by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }
    var media by remember { mutableStateOf(emptyList<ShareMediaItem>()) }
    var apps by remember { mutableStateOf(emptyList<ShareAppItem>()) }
    val selected = remember { mutableStateMapOf<String, Long>() }
    val selectedUris = remember { mutableStateMapOf<String, Uri>() }
    var shareDialog by remember { mutableStateOf(false) }
    val requestMedia = rememberMediaPermissionRequest { refresh++ }
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

    BackHandler(enabled = selected.isNotEmpty()) { selected.clear(); selectedUris.clear() }

    val mediaPermissionNeeded = tab in listOf("Photos", "Videos", "Music", "Documents") && !MediaPermissions.hasMedia(context)
    val allFilesNeeded = tab == "Files" && !MediaPermissions.hasFull(context)

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tabs.forEach { label -> FilterChip(selected = tab == label, onClick = { tab = label }, label = { Text(label) }) }
        }
        if (tab == "Apps") {
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !systemApps, onClick = { systemApps = false }, label = { Text("Installed") })
                FilterChip(selected = systemApps, onClick = { systemApps = true }, label = { Text("System") })
            }
        }
        when {
            mediaPermissionNeeded -> Column(Modifier.padding(20.dp)) {
                Text("Allow media access to browse $tab.")
                Button(onClick = requestMedia) { Text("Grant access") }
            }
            allFilesNeeded -> Column(Modifier.padding(20.dp)) {
                Text("Full Files browsing needs broader storage access. Photos, Videos, Music, Apps, Received and Sent remain available without it.")
                Button(onClick = { MediaPermissions.openAllFilesSettings(context) }) { Text("Open storage access settings") }
            }
            else -> LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                if (tab == "Apps") {
                    items(apps, key = { it.packageName }) { app ->
                        val isSelected = selected.containsKey(app.packageName)
                        val appIcon = remember(app.packageName) {
                            runCatching { context.packageManager.getApplicationIcon(app.packageName).toBitmap().asImageBitmap() }.getOrNull()
                        }
                        Card(Modifier.fillMaxWidth().padding(vertical = 3.dp).combinedClickable(
                            onClick = { select(app.packageName, 0L) },
                            onLongClick = { if (!isSelected) select(app.packageName, 0L) },
                        )) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (appIcon != null) Image(appIcon, contentDescription = app.label, modifier = Modifier.size(40.dp))
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
                            onClick = { select(key, item.bytes) },
                            onLongClick = { if (!isSelected) select(key, item.bytes) },
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
                    items(media, key = { it.key }) { item ->
                        val isSelected = selected.containsKey(item.key)
                        Card(Modifier.fillMaxWidth().padding(vertical = 3.dp).combinedClickable(
                            onClick = {
                                if (selected.isNotEmpty() || selected.containsKey(item.key)) {
                                    val wasSelected = selected.containsKey(item.key)
                                    select(item.key, item.sizeBytes)
                                    if (wasSelected) selectedUris.remove(item.key) else selectedUris[item.key] = item.uri
                                } else runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(item.uri, item.mimeType)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                                }.onFailure { Toast.makeText(context, "Unable to open this item", Toast.LENGTH_SHORT).show() }
                            },
                            onLongClick = { if (!isSelected) { select(item.key, item.sizeBytes); selectedUris[item.key] = item.uri } },
                        )) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, style = MaterialTheme.typography.titleSmall)
                                    Text(MediaRepository.formatBytes(item.sizeBytes) + " · " + item.mimeType, style = MaterialTheme.typography.bodySmall)
                                }
                                if (isSelected) Text("✓")
                            }
                        }
                    }
                    if (media.isEmpty()) item { Text("No items found in $tab.", Modifier.padding(16.dp)) }
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
                    TextButton(onClick = { selected.clear(); selectedUris.clear() }) { Text("Clear") }
                    Button(onClick = { shareDialog = true }) { Text("Share") }
                }
            }
        }
    }

    if (shareDialog) {
        AlertDialog(
            onDismissRequest = { shareDialog = false },
            title = { Text("Share selected items") },
            text = { Text("Choose where to continue with ${selected.size} selected item(s).") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { vm.queueFiles(selectedUris.values.toList()); shareDialog = false; nav.navigate("send") }) { Text("Send") }
                    TextButton(onClick = { shareDialog = false; nav.navigate("receive") }) { Text("Receive") }
                    TextButton(onClick = { shareDialog = false; nav.navigate("computer") }) { Text("Computer/iPhone") }
                }
            },
            dismissButton = { TextButton(onClick = { shareDialog = false }) { Text("Cancel") } },
        )
    }
}
