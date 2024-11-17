package com.example.eev3.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.eev3.data.ObservableSong
import com.example.eev3.data.DownloadStatus
import com.example.eev3.ui.theme.ChineseRed

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongItem(
    song: ObservableSong,
    onSongClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onDownloadClick: () -> Unit,
    downloadStatus: DownloadStatus = DownloadStatus.NotStarted,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onSongClick,
                onLongClick = { showMenu = true }
            ),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 歌曲标题和状态标签
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = song.song.title,
                    style = MaterialTheme.typography.bodyLarge
                )
                
                // 修改状态显示逻辑
                when (downloadStatus) {
                    is DownloadStatus.Success -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            // 只在已缓存时显示"已缓存"
                            if (downloadStatus.isCached) {
                                Text(
                                    text = "已缓存",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )
                            }
                            // 只在路径是外部存储时显示"已下载"
                            if (downloadStatus.path.startsWith("/storage/")) {
                                Text(
                                    text = "已下载",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                    is DownloadStatus.Downloading -> {
                        Text(
                            text = "下载中...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    else -> {} // NotStarted 和 Error 状态不显示标签
                }
            }
            
            // 收藏按钮
            IconButton(onClick = onFavoriteClick) {
                Icon(
                    imageVector = if (song.isFavorite) {
                        Icons.Filled.Favorite
                    } else {
                        Icons.Outlined.FavoriteBorder
                    },
                    contentDescription = if (song.isFavorite) "取消收藏" else "收藏",
                    tint = if (song.isFavorite) ChineseRed else MaterialTheme.colorScheme.onSurface
                )
            }
        }
        
        // 长按菜单
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            when (downloadStatus) {
                                is DownloadStatus.NotStarted -> "下载"
                                is DownloadStatus.Downloading -> "下载中..."
                                is DownloadStatus.Success -> {
                                    when {
                                        downloadStatus.path.startsWith("/storage/") && downloadStatus.isCached -> 
                                            "已下载和缓存"
                                        downloadStatus.path.startsWith("/storage/") -> 
                                            "已下载"
                                        downloadStatus.isCached -> 
                                            "已缓存"
                                        else -> 
                                            "下载"
                                    }
                                }
                                is DownloadStatus.Error -> "下载失败"
                            }
                        )
                        if (downloadStatus is DownloadStatus.Downloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                },
                onClick = {
                    showMenu = false
                    if (downloadStatus !is DownloadStatus.Downloading) {
                        onDownloadClick()
                    }
                }
            )
        }
    }
}