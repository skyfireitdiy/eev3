package com.example.eev3.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.eev3.MainActivity
import com.example.eev3.R
import com.example.eev3.data.PlayerData

class MusicService : Service() {
    companion object {
        private const val CHANNEL_ID = "music_playback_channel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_SHOW_PLAYER = "com.example.eev3.action.SHOW_PLAYER"
        const val EXTRA_PLAYER_DATA = "player_data"
    }

    private var currentPlayerData: PlayerData? = null

    override fun onCreate() {
        super.onCreate()
        println("MusicService: onCreate called")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        println("MusicService: onStartCommand called")
        
        // 获取播放数据
        currentPlayerData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_PLAYER_DATA, PlayerData::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_PLAYER_DATA)
        }
        
        println("MusicService: 当前播放: ${currentPlayerData?.title}")

        // 更新通知
        startForeground(NOTIFICATION_ID, createNotification())
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                println("MusicService: creating notification channel")
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "音乐播放",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "显示正在播放的音乐"
                    setShowBadge(false)
                }
                
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager.createNotificationChannel(channel)
                println("MusicService: notification channel created successfully")
            } catch (e: Exception) {
                println("MusicService: Error creating notification channel: ${e.message}")
                e.printStackTrace()
                throw e
            }
        }
    }

    private fun createNotification(): Notification {
        println("MusicService: creating notification")
        try {
            // 创建打开播放器的Intent
            val intent = Intent(this, MainActivity::class.java).apply {
                action = ACTION_SHOW_PLAYER
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            println("MusicService: creating notification builder")
            // 创建通知
            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(currentPlayerData?.title ?: "易听音乐")
                .setContentText(if (currentPlayerData != null) "点击返回播放器" else "点击打开应用")
                .setSmallIcon(R.drawable.ic_music_note)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)

            println("MusicService: building notification with title: ${currentPlayerData?.title ?: "易听音乐"}")
            val notification = builder.build()
            println("MusicService: notification built successfully")
            return notification
        } catch (e: Exception) {
            println("MusicService: Error creating notification: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        println("MusicService: onDestroy called")
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
} 