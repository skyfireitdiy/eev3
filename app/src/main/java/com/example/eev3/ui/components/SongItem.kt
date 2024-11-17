package com.example.eev3.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
    onPlayMVClick: () -> Unit = {},
    onDownloadMVClick: () -> Unit = {},
    downloadStatus: DownloadStatus = DownloadStatus.NotStarted,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showMVConfirmDialog by remember { mutableStateOf(false) }
    
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = song.song.title,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    // 显示 MV 标记
                    if (song.song.hasMV) {
                        Text(
                            text = "MV",
                            style = MaterialTheme.typography.bodySmall,
                            color = ChineseRed,
                            modifier = Modifier
                                .background(
                                    color = ChineseRed.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                
                // 修改状态显示逻辑
                when (downloadStatus) {
                    is DownloadStatus.Success -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            // 音乐缓存和下载状态
                            if (downloadStatus.isCached) {
                                Text(
                                    text = "已缓存",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )
                            }
                            if (downloadStatus.path.startsWith("/storage/")) {
                                Text(
                                    text = "已下载",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                                )
                            }
                            // MV 缓存和下载状态
                            if (song.song.hasMV) {
                                if (downloadStatus.hasMVCached) {
                                    Text(
                                        text = "MV已缓存",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f)
                                    )
                                }
                                if (downloadStatus.hasMVDownloaded) {
                                    Text(
                                        text = "MV已下载",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                    )
                                }
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
            // 下载音乐菜单项
            DropdownMenuItem(
                enabled = downloadStatus !is DownloadStatus.Downloading,
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            when (downloadStatus) {
                                is DownloadStatus.Downloading -> "下载中..."
                                else -> "下载音乐"
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
                    when (downloadStatus) {
                        is DownloadStatus.Success -> {
                            if (downloadStatus.path.startsWith("/storage/")) {
                                showConfirmDialog = true
                            } else {
                                onDownloadClick()
                            }
                        }
                        else -> onDownloadClick()
                    }
                }
            )
            
            // 如果有 MV，添加 MV 相关菜单项
            if (song.song.hasMV) {
                // 播放 MV 菜单项
                DropdownMenuItem(
                    text = { Text("播放 MV") },
                    onClick = {
                        showMenu = false
                        onPlayMVClick()
                    }
                )
                
                // 下载 MV 菜单项
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                when (downloadStatus) {
                                    is DownloadStatus.Success -> {
                                        if (downloadStatus.hasMVDownloaded) "重新下载 MV"
                                        else "下载 MV"
                                    }
                                    else -> "下载 MV"
                                }
                            )
                        }
                    },
                    onClick = {
                        showMenu = false
                        when (downloadStatus) {
                            is DownloadStatus.Success -> {
                                if (downloadStatus.hasMVDownloaded) {
                                    showMVConfirmDialog = true
                                } else {
                                    onDownloadMVClick()
                                }
                            }
                            else -> onDownloadMVClick()
                        }
                    }
                )
            }
        }

        // 重复下载确认对话框
        if (showConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                title = { Text("确认重新下载") },
                text = { Text("该歌曲已经下载过了，是否要重新下载？") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showConfirmDialog = false
                            onDownloadClick()
                        }
                    ) {
                        Text("确定")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showConfirmDialog = false }
                    ) {
                        Text("取消")
                    }
                }
            )
        }

        // MV 重复下载确认对话框
        if (showMVConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showMVConfirmDialog = false },
                title = { Text("确认重新下载") },
                text = { Text("该 MV 已经下载过了，是否要重新下载？") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showMVConfirmDialog = false
                            onDownloadMVClick()
                        }
                    ) {
                        Text("确定")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showMVConfirmDialog = false }
                    ) {
                        Text("取消")
                    }
                }
            )
        }
    }
}