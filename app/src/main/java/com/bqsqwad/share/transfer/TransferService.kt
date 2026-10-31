
package com.bqsqwad.share.transfer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.bqsqwad.share.R

class TransferService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL, getString(R.string.channel_status), NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)
        val tips = NotificationChannel(TIPS, getString(R.string.channel_tips), NotificationManager.IMPORTANCE_DEFAULT)
        manager.createNotificationChannel(tips)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(intent?.getStringExtra("text") ?: getString(R.string.notif_done))
            .setOngoing(false)
            .build()
        startForeground(42, notification)
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
        return START_NOT_STICKY
    }

    companion object {
        const val CHANNEL = "transfers"
        const val TIPS = "tips"
    }
}
