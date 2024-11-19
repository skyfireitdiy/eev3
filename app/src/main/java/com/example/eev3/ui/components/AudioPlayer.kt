package com.example.eev3.ui.components

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.example.eev3.viewmodel.MusicViewModel
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun AudioPlayer(
    viewModel: MusicViewModel,
    audioUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val playMode by viewModel.playMode.collectAsStateWithLifecycle()
    
    // 初始化播放器
    LaunchedEffect(audioUrl) {
        viewModel.initializePlayer(audioUrl)
    }
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 进度条
        Slider(
            value = currentPosition.toFloat(),
            onValueChange = { viewModel.seekTo(it.toLong()) },
            valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        
        // 时间显示
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatDuration(currentPosition))
            Text(formatDuration(duration))
        }
        
        // 第一行控制按钮：播放控制
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 上一首按钮
            Button(
                onClick = { viewModel.playPrevious() }
            ) {
                Text("上一首")
            }
            
            // 播放/暂停按钮
            Button(
                onClick = { viewModel.playPause() }
            ) {
                Text(if (isPlaying) "暂停" else "播放")
            }
            
            // 下一首按钮
            Button(
                onClick = { viewModel.playNext() }
            ) {
                Text("下一首")
            }
        }
        
        // 第二行控制按钮：播放模式和音量
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 播放模式按钮
            Button(
                onClick = { viewModel.togglePlayMode() }
            ) {
                Text(
                    when (playMode) {
                        MusicViewModel.PlayMode.SEQUENCE -> "列表循环"
                        MusicViewModel.PlayMode.SINGLE_LOOP -> "单曲循环"
                        MusicViewModel.PlayMode.RANDOM -> "随机播放"
                        MusicViewModel.PlayMode.ONCE -> "单次播放"
                    }
                )
            }
            
            // 音量控制
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("音量")
                Slider(
                    value = viewModel.getVolume(),
                    onValueChange = { viewModel.setVolume(it) },
                    valueRange = 0f..1f,
                    modifier = Modifier.width(120.dp)
                )
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
} 