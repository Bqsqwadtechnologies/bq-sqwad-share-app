package com.bqsqwad.share.ui

import android.os.StatFs
import android.os.Environment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.File

private data class StorageArea(val label: String, val bytes: Long)

@Composable
fun StorageIntelligenceScreen(onOpenCleaner: () -> Unit, compact: Boolean = false) {
    val context = LocalContext.current
    var areas by remember { mutableStateOf<List<StorageArea>>(emptyList()) }
    var largest by remember { mutableStateOf<List<File>>(emptyList()) }
    var available by remember { mutableStateOf(0L) }
    var total by remember { mutableStateOf(0L) }
    var scanning by remember { mutableStateOf(true) }

    LaunchedEffect(context, compact) {
        scanning = true
        if (compact) {
            val values = withContext(Dispatchers.IO) {
                runCatching {
                    val root = Environment.getExternalStorageDirectory()
                    val stat = StatFs(if (root.exists()) root.absolutePath else Environment.getDataDirectory().absolutePath)
                    longArrayOf(stat.availableBlocksLong * stat.blockSizeLong, stat.blockCountLong * stat.blockSizeLong)
                }.getOrDefault(longArrayOf(0L, 0L))
            }
            available = values[0]
            total = values[1]
            scanning = false
            return@LaunchedEffect
        }
        val result = withContext(Dispatchers.IO) {
            val stat = runCatching { StatFs(context.filesDir.absolutePath) }.getOrNull()
            val appRoots = listOf(
                "App files" to context.filesDir,
                "App cache" to context.cacheDir,
            ) + listOfNotNull(
                context.getExternalFilesDir(null)?.let { "External app files" to it },
                context.externalCacheDir?.let { "External app cache" to it },
            ).distinctBy { runCatching { it.second.canonicalPath }.getOrDefault(it.second.absolutePath) }
            val files = appRoots.flatMap { (_, root) ->
                runCatching { if (root.exists()) root.walkTopDown().filter { it.isFile && it.canRead() }.toList() else emptyList() }.getOrDefault(emptyList())
            }
            Triple(
                appRoots.map { (label, root) ->
                    StorageArea(label, runCatching { if (root.exists()) root.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L }.getOrDefault(0L))
                },
                files.sortedByDescending { it.length() }.take(12),
                longArrayOf(
                    stat?.let { it.availableBlocksLong * it.blockSizeLong } ?: 0L,
                    stat?.let { it.blockCountLong * it.blockSizeLong } ?: 0L,
                ),
            )
        }
        areas = result.first
        largest = result.second
        available = result.third[0]
        total = result.third[1]
        scanning = false
    }

    suspend fun refreshDeviceStorage() {
        val values = withContext(Dispatchers.IO) {
            runCatching {
                val root = Environment.getExternalStorageDirectory()
                val stat = StatFs(if (root.exists()) root.absolutePath else Environment.getDataDirectory().absolutePath)
                longArrayOf(stat.availableBlocksLong * stat.blockSizeLong, stat.blockCountLong * stat.blockSizeLong)
            }.getOrDefault(longArrayOf(0L, 0L))
        }
        available = values[0]
        total = values[1]
    }

    LaunchedEffect(context) {
        while (true) {
            refreshDeviceStorage()
            delay(2_000L)
        }
    }

    if (compact) {
        val used = (total - available).coerceIn(0L, total.coerceAtLeast(0L))
        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Phone storage", style = MaterialTheme.typography.titleLarge)
                Text(MediaRepository.formatBytes(available), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                Text("available of ${MediaRepository.formatBytes(total)}")
                if (total > 0L) LinearProgressIndicator(
                    progress = { (used.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Used ${MediaRepository.formatBytes(used)}", style = MaterialTheme.typography.bodySmall)
                    Text("Free ${MediaRepository.formatBytes(available)}", style = MaterialTheme.typography.bodySmall)
                }
                Text("Updates automatically", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                OutlinedButton(onClick = onOpenCleaner, modifier = Modifier.fillMaxWidth()) { Text("Storage tools") }
            }
        }
        return
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Storage Intelligence", style = MaterialTheme.typography.headlineSmall)
        Text("Understand storage use before deciding what to clean. This scan reads only BQ Sqwad Share's app-owned folders.")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Device storage", style = MaterialTheme.typography.titleMedium)
                Text("${MediaRepository.formatBytes(available)} available of ${MediaRepository.formatBytes(total)}", style = MaterialTheme.typography.titleLarge)
                if (total > 0L) LinearProgressIndicator(
                    progress = { ((total - available).toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Phone storage is read from Android shared storage. App-owned folders below are scanned separately.")
            }
        }
        Text("BQ Sqwad Share storage", style = MaterialTheme.typography.titleMedium)
        if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
        areas.forEach { area ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(area.label)
                Text(MediaRepository.formatBytes(area.bytes), color = MaterialTheme.colorScheme.primary)
            }
        }
        Text("Largest app-owned files", style = MaterialTheme.typography.titleMedium)
        if (largest.isEmpty() && !scanning) Text("No app-owned files were found.")
        largest.forEach { file ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(file.name, modifier = Modifier.weight(1f), maxLines = 1)
                Text(MediaRepository.formatBytes(file.length()))
            }
        }
        Text("No files are deleted by this screen. Review the existing Deep Clean tool to clear temporary app cache after confirmation.")
        OutlinedButton(onClick = onOpenCleaner, modifier = Modifier.fillMaxWidth()) {
            Text("Review safe cache cleanup")
        }
    }
}
