package com.bqsqwad.share.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

object MediaPermissions {
    fun list(category: String = "all"): Array<String> =
        if (Build.VERSION.SDK_INT >= 33) {
            when (category.lowercase()) {
                "photos" -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES).let { permissions ->
                    if (Build.VERSION.SDK_INT >= 34) permissions + Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED else permissions
                }
                "videos" -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO).let { permissions ->
                    if (Build.VERSION.SDK_INT >= 34) permissions + Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED else permissions
                }
                "music" -> arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
                else -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    fun hasMedia(ctx: Context, category: String = "all"): Boolean {
        val required = when (category.lowercase()) {
            "photos" -> listOf(Manifest.permission.READ_MEDIA_IMAGES)
            "videos" -> listOf(Manifest.permission.READ_MEDIA_VIDEO)
            "music" -> listOf(Manifest.permission.READ_MEDIA_AUDIO)
            else -> list(category).toList()
        }
        if (Build.VERSION.SDK_INT < 33) return ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        val granted = required.any { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
        val selectedVisual = Build.VERSION.SDK_INT >= 34 &&
            category.lowercase() in listOf("photos", "videos") &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
        return granted || selectedVisual
    }

    fun hasFull(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager() else hasMedia(ctx)

    fun openAllFilesSettings(ctx: Context) {
        val intent = if (Build.VERSION.SDK_INT >= 30) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
        }
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
fun rememberMediaPermissionRequest(category: String = "all", onGranted: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val latestOnGranted by rememberUpdatedState(onGranted)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (MediaPermissions.hasMedia(context, category)) latestOnGranted()
    }
    return remember(category) {
        {
            if (MediaPermissions.hasMedia(context, category)) latestOnGranted()
            else launcher.launch(MediaPermissions.list(category))
        }
    }
}
