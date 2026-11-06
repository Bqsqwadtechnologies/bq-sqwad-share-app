package com.bqsqwad.share.ui

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.os.Environment
import java.util.Locale

data class ShareMediaItem(
    val key: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val uri: Uri,
    val category: String,
)

object MediaRepository {
    private val projection = arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.MIME_TYPE,
        MediaStore.MediaColumns.SIZE,
    )

    fun load(context: Context, category: String): List<ShareMediaItem> {
        val resolver = context.contentResolver
        val (collection, selection, args) = when (category.lowercase(Locale.ROOT)) {
            "photos" -> Triple(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null, null)
            "videos" -> Triple(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, null, null)
            "music" -> Triple(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null, null)
            "documents" -> Triple(
                MediaStore.Files.getContentUri("external"),
                "${MediaStore.Files.FileColumns.MIME_TYPE} IN (?, ?, ?, ?, ?)",
                arrayOf("application/pdf", "text/plain", "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            )
            "files" -> {
                if (android.os.Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) return emptyList()
                Triple(MediaStore.Files.getContentUri("external"), null, null)
            }
            else -> return emptyList()
        }

        val result = mutableListOf<ShareMediaItem>()
        resolver.query(collection, projection, selection, args, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "Unnamed file"
                val mime = cursor.getString(mimeColumn) ?: "application/octet-stream"
                val size = cursor.getLong(sizeColumn).coerceAtLeast(0L)
                val uri = Uri.withAppendedPath(collection, id.toString())
                result += ShareMediaItem(uri.toString(), name, mime, size, uri, category)
            }
        }
        return result
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
