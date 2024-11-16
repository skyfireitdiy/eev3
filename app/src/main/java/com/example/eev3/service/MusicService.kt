package com.example.eev3.service

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.eev3.MainActivity
import android.content.Context
import android.os.Build
import android.graphics.drawable.Icon
import android.content.pm.ServiceInfo
import com.example.eev3.R

class MusicService : Service() {
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "music_playback_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 创建通知
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "音乐播放",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "用于音乐播放控制"
                setShowBadge(false)  // 不显示角标
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        // 创建打开应用的 PendingIntent
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 创建通知
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("正在播放音乐")
            .setContentText("点击返回应用")
            .setSmallIcon(android.R.drawable.ic_media_play)  // 使用系统自带的播放图标
            .setContentIntent(pendingIntent)
            .setOngoing(true)  // 设置为持续通知
            .setPriority(NotificationCompat.PRIORITY_LOW)  // 设置为低优先级
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)  // 在锁屏界面可见
            .setCategory(NotificationCompat.CATEGORY_SERVICE)  // 设置为服务类型通知

        return builder.build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(true)
    }
} 