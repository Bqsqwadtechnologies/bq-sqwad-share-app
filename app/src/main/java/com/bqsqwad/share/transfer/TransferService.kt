package com.bqsqwad.share.transfer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bqsqwad.share.R

class TransferService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.channel_status), NotificationManager.IMPORTANCE_LOW))
            manager.createNotificationChannel(NotificationChannel(TIPS, getString(R.string.channel_tips), NotificationManager.IMPORTANCE_DEFAULT))
        }
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: getString(R.string.app_name)
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: getString(R.string.notif_done)
        val max = intent?.getIntExtra(EXTRA_MAX, 0) ?: 0
        val progress = intent?.getIntExtra(EXTRA_PROGRESS, 0) ?: 0
        val complete = intent?.getBooleanExtra(EXTRA_COMPLETE, false) ?: false
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(!complete)
            .setProgress(max, progress.coerceIn(0, max.coerceAtLeast(0)), max <= 0)
        if (complete) builder.setProgress(0, 0, false)
        startForeground(NOTIFICATION_ID, builder.build())
        if (complete) {
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        const val CHANNEL = "transfers"
        const val TIPS = "tips"
        private const val NOTIFICATION_ID = 42
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_MAX = "max"
        private const val EXTRA_PROGRESS = "progress"
        private const val EXTRA_COMPLETE = "complete"
        private const val EXTRA_SHOW_COMPLETE = "show_complete"

        fun update(
            context: Context,
            text: String,
            transferred: Long,
            total: Long,
            complete: Boolean = false,
            showCompletionNotification: Boolean = true,
        ) {
            val safeTotal = total.coerceAtLeast(0L)
            val max = if (safeTotal > Int.MAX_VALUE) 1000 else safeTotal.toInt()
            val progress = if (safeTotal <= 0L) 0 else if (safeTotal > Int.MAX_VALUE) {
                ((transferred.coerceAtLeast(0L).toDouble() / safeTotal) * 1000).toInt()
            } else transferred.coerceIn(0L, safeTotal).toInt()
            val intent = Intent(context, TransferService::class.java)
                .putExtra(EXTRA_TITLE, context.getString(R.string.app_name))
                .putExtra(EXTRA_TEXT, text)
                .putExtra(EXTRA_MAX, max)
                .putExtra(EXTRA_PROGRESS, progress)
                .putExtra(EXTRA_COMPLETE, complete)
                .putExtra(EXTRA_SHOW_COMPLETE, showCompletionNotification)
            if (Build.VERSION.SDK_INT >= 26) ContextCompat.startForegroundService(context, intent)
            else context.startService(intent)
        }
    }
}
