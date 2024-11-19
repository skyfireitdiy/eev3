package com.example.eev3.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.eev3.data.PlayerData
import com.example.eev3.data.LyricLine
import com.example.eev3.viewmodel.MusicViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import com.example.eev3.ui.theme.ChineseRed

@Composable
fun CustomPlayer(
    playerData: PlayerData,
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier,
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRepeatMode: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    repeatMode: Int,
    volume: Float
) {
    val lyrics = viewModel.lyrics.collectAsStateWithLifecycle()
    val currentLyricIndex = viewModel.currentLyricIndex.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val shuffleEnabled = viewModel.shuffleEnabled.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // 添加自动滚动效果
    LaunchedEffect(currentLyricIndex.value) {
        if (currentLyricIndex.value >= 0) {
            listState.animateScrollToItem(
                index = maxOf(0, currentLyricIndex.value - 2),
                scrollOffset = -100
            )
        }
    }

    // 收集提示消息
    LaunchedEffect(Unit) {
        viewModel.downloadTip.collect { tip ->
            tip?.message?.let { message ->
                if (message.isNotEmpty()) {
                    snackbarHostState.showSnackbar(message)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 封面图片
            AsyncImage(
                model = playerData.coverImage,
                contentDescription = "封面",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(32.dp)
            )

            // 标题
            Text(
                text = playerData.title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp)
            )

            // 歌词列表
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                items(lyrics.value) { lyric ->
                    val isCurrentLyric = lyrics.value.indexOf(lyric) == currentLyricIndex.value
                    Text(
                        text = lyric.text,
                        style = if (isCurrentLyric) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                        color = if (isCurrentLyric) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                vertical = if (isCurrentLyric) 12.dp else 8.dp,
                                horizontal = 16.dp
                            ),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // 在播放控制之前添加进度条和时间显示
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                // 进度条
                Slider(
                    value = viewModel.currentPosition.collectAsStateWithLifecycle().value.toFloat(),
                    onValueChange = { viewModel.seekTo(it.toLong()) },
                    valueRange = 0f..maxOf(viewModel.duration.collectAsStateWithLifecycle().value.toFloat(), 1f),
                    modifier = Modifier.fillMaxWidth()
                )
                
                // 时间显示
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 当前时间
                    Text(
                        text = formatDuration(viewModel.currentPosition.collectAsStateWithLifecycle().value),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    
                    // 总时长
                    Text(
                        text = formatDuration(viewModel.duration.collectAsStateWithLifecycle().value),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // 播放控制
            PlayerControls(
                isPlaying = isPlaying,
                onPlayPause = onPlayPause,
                onPrevious = onPrevious,
                onNext = {
                    // 根据当前播放模式处理下一曲
                    when {
                        repeatMode == ExoPlayer.REPEAT_MODE_OFF -> {
                            // 单次播放模式，检查是否有下一曲
                            if (viewModel.hasNextSong()) onNext()
                        }
                        repeatMode == ExoPlayer.REPEAT_MODE_ONE -> {
                            // 单曲循环模式，直接播放下一曲
                            onNext()
                        }
                        repeatMode == ExoPlayer.REPEAT_MODE_ALL -> {
                            // 列表循环模式，直接播放下一曲
                            onNext()
                        }
                    }
                },
                onRepeatMode = onRepeatMode,
                onVolumeChange = onVolumeChange,
                onFavoriteClick = onFavoriteClick,
                repeatMode = repeatMode,
                volume = volume,
                isFavorite = isFavorite,
                shuffleEnabled = shuffleEnabled.value,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// 添加时间格式化函数
private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@Composable
fun LyricsDisplay(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,  // 设置水平居中对齐
        verticalArrangement = Arrangement.Center
    ) {
        items(lyrics.size) { index ->
            val isCurrentLine = index == currentIndex
            Text(
                text = lyrics[index].text,
                style = MaterialTheme.typography.bodyLarge.copy(  // 使用 bodyLarge 而不是 titleLarge
                    fontSize = if (isCurrentLine) 20.sp else 16.sp  // 调小字体大小
                ),
                color = if (isCurrentLine) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                },
                textAlign = TextAlign.Center,  // 设置文本居中对齐
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
fun PlayerControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRepeatMode: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onFavoriteClick: () -> Unit,
    repeatMode: Int,
    volume: Float,
    isFavorite: Boolean,
    shuffleEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 播放控制按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevious) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "上一曲"
                )
            }
            
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            IconButton(onClick = onNext) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "下一曲"
                )
            }
        }
        
        // 功能按钮行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 收藏按钮
            IconButton(onClick = onFavoriteClick) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isFavorite) "取消收藏" else "收藏",
                    tint = if (isFavorite) ChineseRed else MaterialTheme.colorScheme.onSurface
                )
            }
            
            // 循环模式按钮
            IconButton(onClick = onRepeatMode) {
                Icon(
                    imageVector = when {
                        repeatMode == ExoPlayer.REPEAT_MODE_ALL && !shuffleEnabled -> Icons.Default.Repeat  // 列表循环
                        repeatMode == ExoPlayer.REPEAT_MODE_ALL && shuffleEnabled -> Icons.Default.Shuffle  // 随机播放
                        repeatMode == ExoPlayer.REPEAT_MODE_ONE -> Icons.Default.RepeatOne  // 单曲循环
                        else -> Icons.Default.Repeat  // 默认显示列表循环图标
                    },
                    contentDescription = when {
                        repeatMode == ExoPlayer.REPEAT_MODE_ALL && !shuffleEnabled -> "列表循环"
                        repeatMode == ExoPlayer.REPEAT_MODE_ALL && shuffleEnabled -> "随机播放"
                        repeatMode == ExoPlayer.REPEAT_MODE_ONE -> "单曲循环"
                        else -> "播放模式"
                    }
                )
            }
            
            // 音量控制
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeDown,
                    contentDescription = "音量"
                )
                Slider(
                    value = volume,
                    onValueChange = onVolumeChange,
                    modifier = Modifier.width(100.dp)
                )
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = "音量"
                )
            }
        }
    }
} 