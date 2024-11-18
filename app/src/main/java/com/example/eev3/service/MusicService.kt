package com.example.eev3.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.eev3.MainActivity
import com.example.eev3.R
import com.example.eev3.data.PlayerData

class MusicService : Service() {
    companion object {
        private const val CHANNEL_ID = "music_playback_channel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_SHOW_PLAYER = "com.example.eev3.action.SHOW_PLAYER"
        const val ACTION_PREVIOUS = "com.example.eev3.action.PREVIOUS"
        const val ACTION_PLAY_PAUSE = "com.example.eev3.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.eev3.action.NEXT"
        const val ACTION_FAVORITE = "com.example.eev3.action.FAVORITE"
        const val EXTRA_PLAYER_DATA = "player_data"

        const val BROADCAST_PREVIOUS = "com.example.eev3.broadcast.PREVIOUS"
        const val BROADCAST_PLAY_PAUSE = "com.example.eev3.broadcast.PLAY_PAUSE"
        const val BROADCAST_NEXT = "com.example.eev3.broadcast.NEXT"
        const val BROADCAST_FAVORITE = "com.example.eev3.broadcast.FAVORITE"
    }

    private var currentPlayerData: PlayerData? = null
    private var isPlaying = false
    private var isFavorite = false

    override fun onCreate() {
        super.onCreate()
        println("MusicService: onCreate called")
        createNotificationChannel()
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        println("MusicService: onStartCommand called with action: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_PREVIOUS -> {
                println("MusicService: 发送上一曲广播")
                LocalBroadcastManager.getInstance(this)
                    .sendBroadcast(Intent(BROADCAST_PREVIOUS))
            }
            ACTION_PLAY_PAUSE -> {
                isPlaying = !isPlaying
                println("MusicService: 发送播放/暂停广播")
                LocalBroadcastManager.getInstance(this)
                    .sendBroadcast(Intent(BROADCAST_PLAY_PAUSE))
            }
            ACTION_NEXT -> {
                println("MusicService: 发送下一曲广播")
                LocalBroadcastManager.getInstance(this)
                    .sendBroadcast(Intent(BROADCAST_NEXT))
            }
            ACTION_FAVORITE -> {
                isFavorite = !isFavorite
                println("MusicService: 发送收藏广播")
                LocalBroadcastManager.getInstance(this)
                    .sendBroadcast(Intent(BROADCAST_FAVORITE))
            }
            else -> {
                // 获取播放数据
                currentPlayerData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent?.getParcelableExtra(EXTRA_PLAYER_DATA, PlayerData::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent?.getParcelableExtra(EXTRA_PLAYER_DATA)
                }
                isPlaying = intent?.getBooleanExtra("isPlaying", false) ?: false
                isFavorite = intent?.getBooleanExtra("isFavorite", false) ?: false
            }
        }
        
        println("MusicService: 当前播放: ${currentPlayerData?.title}")

        // 更新通知
        startForeground(NOTIFICATION_ID, createNotification())
        return START_NOT_STICKY
    }

    private fun createNotification(): Notification {
        println("MusicService: creating notification")
        try {
            // 创建打开播放器的Intent
            val contentIntent = Intent(this, MainActivity::class.java).apply {
                action = ACTION_SHOW_PLAYER
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            
            val contentPendingIntent = PendingIntent.getActivity(
                this, 0, contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 创建控制按钮的 PendingIntent
            val previousIntent = Intent(this, MusicService::class.java).apply {
                action = ACTION_PREVIOUS
            }
            val previousPendingIntent = PendingIntent.getService(
                this, 0, previousIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val playPauseIntent = Intent(this, MusicService::class.java).apply {
                action = ACTION_PLAY_PAUSE
            }
            val playPausePendingIntent = PendingIntent.getService(
                this, 0, playPauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val nextIntent = Intent(this, MusicService::class.java).apply {
                action = ACTION_NEXT
            }
            val nextPendingIntent = PendingIntent.getService(
                this, 0, nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val favoriteIntent = Intent(this, MusicService::class.java).apply {
                action = ACTION_FAVORITE
            }
            val favoritePendingIntent = PendingIntent.getService(
                this, 0, favoriteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 创建通知
            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(currentPlayerData?.title ?: "易听音乐")
                .setContentText(if (currentPlayerData != null) {
                    if (isFavorite) "已收藏 - 点击返回播放器" else "未收藏 - 点击返回播放器"
                } else "点击打开应用")
                .setSmallIcon(R.drawable.ic_music_note)
                .setContentIntent(contentPendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                // 设置媒体样式
                .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2, 3)  // 在紧凑视图中显示所有按钮
                )
                // 添加控制按钮
                .addAction(R.drawable.ic_previous, "上一曲", previousPendingIntent)
                .addAction(
                    if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                    if (isPlaying) "暂停" else "播放",
                    playPausePendingIntent
                )
                .addAction(R.drawable.ic_next, "下一曲", nextPendingIntent)
                .addAction(
                    if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border,
                    if (isFavorite) "取消收藏" else "收藏",
                    favoritePendingIntent
                )

            return builder.build()
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