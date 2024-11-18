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

@Composable
fun CustomPlayer(
    playerData: PlayerData,
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    var isLyricsFullscreen by remember { mutableStateOf(false) }
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val currentLyricIndex by viewModel.currentLyricIndex.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    
    // 自动滚动到当前歌词
    LaunchedEffect(currentLyricIndex) {
        if (currentLyricIndex >= 0) {
            listState.animateScrollToItem(
                index = currentLyricIndex.coerceAtMost(lyrics.size - 1),
                scrollOffset = -100
            )
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isLyricsFullscreen) {
            // 正常模式显示所有内容
            // 封面图片
            AsyncImage(
                model = playerData.coverImage,
                contentDescription = null,
                modifier = Modifier
                    .size(200.dp)
                    .padding(bottom = 16.dp)
            )
            
            // 标题
            Text(
                text = playerData.title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // 音频播放器
            AudioPlayer(
                viewModel = viewModel,
                audioUrl = playerData.audioUrl,
                modifier = Modifier.fillMaxWidth()
            )
            
            // 添加歌词提示文字
            Text(
                text = "双击歌词切换大歌词模式",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        } else {
            // 在全屏模式下显示提示文字
            Text(
                text = "双击歌词切换封面模式",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        
        // 歌词列表
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = if (isLyricsFullscreen) 0.dp else 16.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            isLyricsFullscreen = !isLyricsFullscreen
                        }
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (isLyricsFullscreen) Arrangement.Center else Arrangement.Top
        ) {
            items(lyrics) { lyric ->
                val isCurrentLyric = lyrics.indexOf(lyric) == currentLyricIndex
                Text(
                    text = lyric.text,
                    style = if (isCurrentLyric) {
                        if (isLyricsFullscreen) {
                            MaterialTheme.typography.headlineMedium  // 全屏模式使用更大字体
                        } else {
                            MaterialTheme.typography.titleMedium
                        }
                    } else {
                        if (isLyricsFullscreen) {
                            MaterialTheme.typography.titleMedium  // 全屏模式使用更大字体
                        } else {
                            MaterialTheme.typography.bodyMedium
                        }
                    },
                    color = if (isCurrentLyric) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            vertical = if (isCurrentLyric) {
                                if (isLyricsFullscreen) 24.dp else 12.dp
                            } else {
                                if (isLyricsFullscreen) 16.dp else 8.dp
                            },
                            horizontal = 16.dp  // 添加水平内边距
                        ),
                    fontWeight = if (isCurrentLyric) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Normal
                    },
                    textAlign = TextAlign.Center,  // 设置文本居中对齐
                    lineHeight = if (isLyricsFullscreen) 32.sp else 24.sp  // 设置行高
                )
            }
        }
        
        // 在全屏模式下显示迷你播放控制
        if (isLyricsFullscreen) {
            AudioPlayer(
                viewModel = viewModel,
                audioUrl = playerData.audioUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            )
        }
    }
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