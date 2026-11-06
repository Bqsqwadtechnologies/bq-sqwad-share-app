package com.bqsqwad.share.ui

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.NavHostController
import com.bqsqwad.share.ShareViewModel
import com.bqsqwad.share.ads.AdManager
import com.bqsqwad.share.data.TransferEntity
import java.io.File

private data class DeviceMedia(val name: String, val uri: Uri, val size: Long, val mime: String)

private fun mediaPermission(category: String): String? = when {
    Build.VERSION.SDK_INT >= 33 && category == "Videos" -> Manifest.permission.READ_MEDIA_VIDEO
    Build.VERSION.SDK_INT >= 33 && category == "Music" -> Manifest.permission.READ_MEDIA_AUDIO
    Build.VERSION.SDK_INT >= 33 && category == "Photos" -> Manifest.permission.READ_MEDIA_IMAGES
    Build.VERSION.SDK_INT < 33 && category in setOf("Videos", "Music", "Photos") -> Manifest.permission.READ_EXTERNAL_STORAGE
    else -> null
}

private fun loadDeviceMedia(context: android.content.Context, category: String): List<DeviceMedia> {
    val collection = when (category) {
        "Videos" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        "Music" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        "Photos" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        else -> return emptyList()
    }
    val columns = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.MIME_TYPE)
    return runCatching {
        context.contentResolver.query(collection, columns, null, null, MediaStore.MediaColumns.DATE_ADDED + " DESC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(collection, id)
                    add(DeviceMedia(cursor.getString(nameCol) ?: "Media", uri, cursor.getLong(sizeCol), cursor.getString(mimeCol) ?: "application/octet-stream"))
                }
            }
        } ?: emptyList()
    }.getOrDefault(emptyList())
}

@Composable
fun HomeLibraryScreen(nav: NavHostController, vm: ShareViewModel, ads: AdManager, adFree: Boolean) {
    val context = LocalContext.current
    val categories = listOf("Apps", "Videos", "Music", "Photos", "Files")
    var category by remember { mutableStateOf("Apps") }
    var media by remember { mutableStateOf(emptyList<DeviceMedia>()) }
    var permissionGranted by remember { mutableStateOf(false) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf(setOf<Uri>()) }
    var selectedApps by remember { mutableStateOf(setOf<String>()) }
    val apps = remember { vm.installedApps() }
    val history by vm.history.collectAsState()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        permissionMessage = if (granted) null else "Allow access to this media category to browse files on your phone."
        if (granted) media = loadDeviceMedia(context, category)
    }
    LaunchedEffect(category) {
        selected = emptySet()
        val permission = mediaPermission(category)
        if (permission == null) {
            permissionGranted = true
            media = emptyList()
        } else if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            permissionGranted = true
            media = loadDeviceMedia(context, category)
        } else {
            permissionGranted = false
            media = emptyList()
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("BQ Sqwad Share", style = MaterialTheme.typography.headlineSmall)
                Text("Share anything, nearby.", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = { nav.navigate("settings") }) { Text("Settings") }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { nav.navigate("send") }, modifier = Modifier.weight(1f).height(48.dp)) { Text("Send") }
            OutlinedButton(onClick = { nav.navigate("receive") }, modifier = Modifier.weight(1f).height(48.dp)) { Text("Receive") }
        }
        Spacer(Modifier.height(14.dp))
        Text("Browse this phone", style = MaterialTheme.typography.titleLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(categories) { item ->
                FilterChip(selected = category == item, onClick = { category = item }, label = { Text(item) })
            }
        }
        when (category) {
            "Apps" -> {
                Text("${apps.size} installed apps", style = MaterialTheme.typography.labelMedium)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    gridItems(apps, key = { it.second }) { app ->
                        val icon = remember(app.second) {
                            runCatching { context.packageManager.getApplicationIcon(app.second).toBitmap().asImageBitmap() }.getOrNull()
                        }
                        val apkSize = remember(app.second) {
                            runCatching {
                                val info = context.packageManager.getApplicationInfo(app.second, 0)
                                File(info.sourceDir).length()
                            }.getOrDefault(0L)
                        }
                        Card(Modifier.padding(4.dp).clickable { selectedApps = if (app.second in selectedApps) selectedApps - app.second else selectedApps + app.second }) {
                            Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                if (icon != null) Image(icon, contentDescription = app.first, modifier = Modifier.size(42.dp))
                                Text(app.first, style = MaterialTheme.typography.labelSmall, maxLines = 2, minLines = 2)
                                Text(if (apkSize > 0) "${apkSize / 1_048_576} MB" else "App", style = MaterialTheme.typography.labelSmall)\n                                if (app.second in selectedApps) Text("Selected", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            "Files" -> {
                val docs = history.filter { it.status != "DELETED" }
                LazyColumn(Modifier.weight(1f)) {
                    items(docs, key = { it.id }) { item -> MediaHistoryRow(item) }
                    if (docs.isEmpty()) item { Text("Your transfer files will appear here.") }
                }
            }
            else -> {
                val permission = mediaPermission(category)
                if (!permissionGranted) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(permissionMessage ?: "Allow access to browse your ${category.lowercase()} on this phone.", style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { if (permission != null) permissionLauncher.launch(permission) }) { Text("Allow access") }
                    }
                } else {
                    Text("${media.size} items", style = MaterialTheme.typography.labelMedium)
                    LazyColumn(Modifier.weight(1f)) {
                        items(media, key = { it.uri.toString() }) { item ->
                            Card(Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable {
                                selected = if (item.uri in selected) selected - item.uri else selected + item.uri
                            }) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                        Text("${item.size / 1_048_576} MB · ${item.mime}", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Text(if (item.uri in selected) "Selected" else "Select", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        if (media.isEmpty()) item { Text("No ${category.lowercase()} found on this phone.") }
                    }
                }
            }
        }
        if (selected.isNotEmpty()) {
            Text("${selected.size} selected")
            Button(onClick = { nav.navigate("send") }, modifier = Modifier.fillMaxWidth()) { Text("Continue to Send") }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButtonCompat("History") { nav.navigate("history") }
            TextButtonCompat("More") { nav.navigate("more") }
        }
    }
}

@Composable
private fun MediaHistoryRow(item: TransferEntity) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(item.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text("${item.bytes / 1_048_576} MB · ${item.status}", style = MaterialTheme.typography.labelSmall)
        }
        Text(item.direction, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun TextButtonCompat(label: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) { Text(label) }
}
