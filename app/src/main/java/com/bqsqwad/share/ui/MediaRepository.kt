package com.bqsqwad.share.ui

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.os.Environment
import java.util.Locale

data class ShareAppItem(val label: String, val packageName: String, val apkPaths: List<String>)

data class ShareMediaItem(
    val key: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val uri: Uri,
    val category: String,
    val durationMs: Long = 0L,
)

object MediaRepository {
    fun loadApps(context: Context, systemOnly: Boolean): List<ShareAppItem> {
        val pm = context.packageManager
        val launcher = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, 0).mapNotNull { info ->
            val appInfo = info.activityInfo?.applicationInfo ?: return@mapNotNull null
            val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            if (isSystem != systemOnly) return@mapNotNull null
            ShareAppItem(info.loadLabel(pm).toString(), appInfo.packageName, (listOfNotNull(appInfo.sourceDir) + appInfo.splitSourceDirs.orEmpty()).distinct())
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase(Locale.ROOT) }
    }

    private val projection = arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.MIME_TYPE,
        MediaStore.MediaColumns.SIZE,
        MediaStore.MediaColumns.DURATION,
    )

    fun load(context: Context, category: String): List<ShareMediaItem> {
        val resolver = context.contentResolver
        val (collection, selection, args) = when (category.lowercase(Locale.ROOT)) {
            "photos" -> Triple(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null, null)
            "videos" -> Triple(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, null, null)
            "music" -> Triple(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null, null)
            "documents" -> Triple(
                MediaStore.Files.getContentUri("external"),
                "${MediaStore.Files.FileColumns.MIME_TYPE} IN (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf(
                    "application/pdf",
                    "text/plain",
                    "text/csv",
                    "application/json",
                    "text/markdown",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                ),
            )
            "files" -> {
                if (android.os.Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) return emptyList()
                Triple(MediaStore.Files.getContentUri("external"), null, null)
            }
            else -> return emptyList()
        }

        val result = mutableListOf<ShareMediaItem>()
        val cursorResult = runCatching {
            resolver.query(collection, projection, selection, args, "${MediaStore.MediaColumns.DATE_ADDED} DESC")
        }.getOrNull()
        cursorResult?.use { cursor ->
            val idColumn = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val sizeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val durationColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)
            if (idColumn < 0 || nameColumn < 0 || mimeColumn < 0 || sizeColumn < 0) return@use
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "Unnamed file"
                val mime = cursor.getString(mimeColumn) ?: "application/octet-stream"
                val size = cursor.getLong(sizeColumn).coerceAtLeast(0L)
                val duration = if (durationColumn >= 0) cursor.getLong(durationColumn).coerceAtLeast(0L) else 0L
                val uri = Uri.withAppendedPath(collection, id.toString())
                result += ShareMediaItem(uri.toString(), name, mime, size, uri, category, duration)
            }
        }
        return result
    }

    fun formatDuration(durationMs: Long): String {
        val seconds = (durationMs / 1000L).coerceAtLeast(0L)
        val hours = seconds / 3600L
        val minutes = (seconds % 3600L) / 60L
        val remainder = seconds % 60L
        return if (hours > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, remainder)
        else String.format(Locale.getDefault(), "%d:%02d", minutes, remainder)
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var index = -1
        do {
            value /= 1024.0
            index++
        } while (value >= 1024.0 && index < units.lastIndex)
        return String.format(Locale.getDefault(), "%.1f %s", value, units[index])
    }
}
