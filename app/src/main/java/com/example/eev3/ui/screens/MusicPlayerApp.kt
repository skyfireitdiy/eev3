package com.example.eev3.ui.screens

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.eev3.data.Song
import com.example.eev3.ui.components.*
import com.example.eev3.ui.theme.ChineseRed
import com.example.eev3.viewmodel.MusicViewModel
import com.example.eev3.viewmodel.MusicViewModelFactory
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement.spacedBy
import com.example.eev3.data.DownloadStatus
import com.example.eev3.data.ObservableSong

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicPlayerApp(
    viewModel: MusicViewModel = viewModel(
        factory = MusicViewModelFactory(LocalContext.current)
    ),
    onBackPressed: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var currentSong by remember { mutableStateOf<Song?>(null) }
    var showPlayer by remember { mutableStateOf(false) }
    
    // 创建 pager 状态，初始页面设为收藏页面（索引1）
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 5 })
    // 创建协程作用域
    val coroutineScope = rememberCoroutineScope()
    
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle(emptyList())
    val favorites by viewModel.favorites.collectAsStateWithLifecycle(emptyList())
    val searchError by viewModel.searchError.collectAsStateWithLifecycle(null)
    val currentPlayerData by viewModel.currentPlayerData.collectAsStateWithLifecycle(null)
    val playbackError by viewModel.playbackError.collectAsStateWithLifecycle(null)
    
    // 获取当前播放歌曲状态
    val currentPlayingSong by viewModel.currentPlayingSongState.collectAsStateWithLifecycle(null)
    
    val showClearCacheDialog by viewModel.showClearCacheDialog.collectAsStateWithLifecycle()
    val cacheSize by viewModel.cacheSize.collectAsStateWithLifecycle()
    
    // 收集下载提示状态
    val downloadTip by viewModel.downloadTip.collectAsState()
    
    // 添加榜单相关状态
    val rankSongs by viewModel.rankSongs.collectAsStateWithLifecycle(emptyList())
    val rankLoadingMore by viewModel.rankLoadingMore.collectAsStateWithLifecycle()
    val rankReachedEnd by viewModel.rankReachedEnd.collectAsStateWithLifecycle()
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    // 处理返回键
    BackHandler(enabled = showPlayer) {
        showPlayer = false
        viewModel.onPlayerVisibilityChanged(false)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showPlayer && currentPlayerData != null) {
            // 播放器页面显示时
            LaunchedEffect(Unit) {
                viewModel.onPlayerVisibilityChanged(true)
            }
            
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                // 直接显示播放器内容，移除顶部栏
                CustomPlayer(
                    playerData = currentPlayerData!!,
                    viewModel = viewModel,
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.statusBars)  // 添加状态栏padding
                )
            }
        } else {
            // 主页面
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.statusBars)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when (pagerState.currentPage) {
                                    0 -> "收藏列表"
                                    1 -> "搜索列表"
                                    2 -> "新歌榜"
                                    3 -> "TOP榜单"
                                    4 -> "DJ舞曲"
                                    else -> ""
                                },
                                style = MaterialTheme.typography.titleLarge
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 显示缓存大小和清除按钮
                                Text(
                                    text = "缓存: ${viewModel.formatCacheSize(cacheSize)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                TextButton(
                                    onClick = { viewModel.showClearCacheDialog() }
                                ) {
                                    Text("清除")
                                }
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(if (pagerState.currentPage == 1) 0 else 1)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (pagerState.currentPage == 1) Icons.Default.Favorite else Icons.Default.Search,
                                        contentDescription = if (pagerState.currentPage == 1) "Show favorites" else "Show search",
                                        tint = ChineseRed
                                    )
                                }
                            }
                        }
                    }
                },
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.weight(1f)
                    ) { page ->
                        when (page) {
                            0 -> {
                                // 收藏页面
                                Box(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(0.dp),
                                        verticalArrangement = Arrangement.Top,
                                        userScrollEnabled = true
                                    ) {
                                        items(favorites) { song ->
                                            SongItem(
                                                song = song,
                                                onSongClick = {
                                                    currentSong = song.song
                                                    viewModel.loadPlayerData(song.song)
                                                    showPlayer = true
                                                },
                                                onFavoriteClick = { viewModel.toggleFavorite(song) },
                                                onDownloadClick = { viewModel.downloadSong(song.song) },
                                                downloadStatus = viewModel.checkSongStatus(song.song)
                                            )
                                        }
                                    }
                                }
                            }
                            1 -> {
                                // 搜索页面
                                Box(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        SearchBar(
                                            query = searchQuery,
                                            onQueryChange = { searchQuery = it },
                                            onSearch = {
                                                if (searchQuery.isNotEmpty()) {
                                                    viewModel.searchSongs(searchQuery)
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp)
                                        )
                                        
                                        LazyColumn(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(0.dp),
                                            verticalArrangement = Arrangement.Top,
                                            userScrollEnabled = true
                                        ) {
                                            items(searchResults) { song ->
                                                SongItem(
                                                    song = song,
                                                    onSongClick = {
                                                        currentSong = song.song
                                                        viewModel.loadPlayerData(song.song)
                                                        showPlayer = true
                                                    },
                                                    onFavoriteClick = { viewModel.toggleFavorite(song) },
                                                    onDownloadClick = { viewModel.downloadSong(song.song) },
                                                    downloadStatus = viewModel.checkSongStatus(song.song),
                                                    modifier = Modifier.padding(vertical = 4.dp)
                                                )
                                            }
                                            
                                            // 添加加载更多项
                                            item {
                                                val loadingMore by viewModel.loadingMore.collectAsStateWithLifecycle()
                                                val reachedEnd by viewModel.reachedEnd.collectAsStateWithLifecycle()
                                                
                                                if (searchResults.isNotEmpty()) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(16.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (loadingMore) {
                                                            CircularProgressIndicator(
                                                                modifier = Modifier.size(24.dp),
                                                                color = ChineseRed
                                                            )
                                                        } else if (reachedEnd) {
                                                            Text(
                                                                text = "已经到底了",
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                            )
                                                        } else {
                                                            // 如果不是在加载中且没有到底，显示加载更多按钮
                                                            TextButton(
                                                                onClick = { viewModel.loadMore() }
                                                            ) {
                                                                Text("加载更多")
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            2 -> {
                                // 新歌榜
                                LaunchedEffect(Unit) {
                                    viewModel.loadRankSongs(MusicViewModel.RankType.NEW)
                                }
                                RankListPage(
                                    songs = rankSongs,
                                    loadingMore = rankLoadingMore,
                                    reachedEnd = rankReachedEnd,
                                    onLoadMore = { viewModel.loadMoreRank() },
                                    onSongClick = { song ->
                                        currentSong = song.song
                                        viewModel.loadPlayerData(song.song)
                                        showPlayer = true
                                    },
                                    onFavoriteClick = { viewModel.toggleFavorite(it) },
                                    onDownloadClick = { viewModel.downloadSong(it.song) },
                                    viewModel = viewModel
                                )
                            }
                            3 -> {
                                // TOP榜单
                                LaunchedEffect(Unit) {
                                    viewModel.loadRankSongs(MusicViewModel.RankType.TOP)
                                }
                                RankListPage(
                                    songs = rankSongs,
                                    loadingMore = rankLoadingMore,
                                    reachedEnd = rankReachedEnd,
                                    onLoadMore = { viewModel.loadMoreRank() },
                                    onSongClick = { song ->
                                        currentSong = song.song
                                        viewModel.loadPlayerData(song.song)
                                        showPlayer = true
                                    },
                                    onFavoriteClick = { viewModel.toggleFavorite(it) },
                                    onDownloadClick = { viewModel.downloadSong(it.song) },
                                    viewModel = viewModel
                                )
                            }
                            4 -> {
                                // DJ舞曲
                                LaunchedEffect(Unit) {
                                    viewModel.loadRankSongs(MusicViewModel.RankType.DJ_DANCE)
                                }
                                RankListPage(
                                    songs = rankSongs,
                                    loadingMore = rankLoadingMore,
                                    reachedEnd = rankReachedEnd,
                                    onLoadMore = { viewModel.loadMoreRank() },
                                    onSongClick = { song ->
                                        currentSong = song.song
                                        viewModel.loadPlayerData(song.song)
                                        showPlayer = true
                                    },
                                    onFavoriteClick = { viewModel.toggleFavorite(it) },
                                    onDownloadClick = { viewModel.downloadSong(it.song) },
                                    viewModel = viewModel
                                )
                            }
                        }
                    }

                    if (currentPlayingSong != null) {
                        PlayerControls(
                            song = currentPlayingSong!!,
                            onClick = { 
                                showPlayer = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
    
    // 错误对话框
    if (searchError != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearSearchError() },
            title = { Text("搜索错误") },
            text = { Text(searchError ?: "") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearSearchError() }) {
                    Text("确定")
                }
            }
        )
    }

    // 播放错误对话框
    if (playbackError != null) {
        when (val error = playbackError) {
            is MusicViewModel.PlaybackError.PlaybackTimeout -> {
                AlertDialog(
                    onDismissRequest = { viewModel.clearPlaybackError() },
                    title = { Text("播放失败") },
                    text = { Text("歌曲《${error.song.title}》播放失败，可能链接已失效。") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.handlePlaybackTimeout(error.song)
                                viewModel.clearPlaybackError()
                                viewModel.playNext()
                            }
                        ) {
                            Text("移除收藏并播放下一首")
                        }
                    },
                    dismissButton = {
                        Row {
                            TextButton(
                                onClick = {
                                    viewModel.clearPlaybackError()
                                    viewModel.searchSongs(error.song.title)
                                }
                            ) {
                                Text("重新搜索")
                            }
                            TextButton(
                                onClick = {
                                    viewModel.clearPlaybackError()
                                    viewModel.playNext()
                                }
                            ) {
                                Text("忽略并播放下一首")
                            }
                        }
                    }
                )
            }
            null -> { /* 不需要处理 null 的情况 */ }
        }
    }

    // 清除缓存确认对话框
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissClearCacheDialog() },
            title = { Text("清除缓存") },
            text = { 
                Text(
                    "确定要清除所有缓存的音乐文件吗？\n当前缓存大小: ${viewModel.formatCacheSize(cacheSize)}"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.clearCache() }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissClearCacheDialog() }
                ) {
                    Text("取消")
                }
            }
        )
    }

    // 显示下载提示
    LaunchedEffect(downloadTip) {
        downloadTip?.let { tip ->
            snackbarHostState.showSnackbar(
                message = buildString {
                    append(tip.message)
                    tip.path?.let { path ->
                        append("\n保存位置: $path")
                    }
                },
                actionLabel = if (tip.path != null) "查看" else null,
                duration = SnackbarDuration.Long
            )
            // 显示后清除提示
            viewModel.clearDownloadTip()
        }
    }
}

// 添加榜单页面组件
@Composable
private fun RankListPage(
    songs: List<ObservableSong>,
    loadingMore: Boolean,
    reachedEnd: Boolean,
    onLoadMore: () -> Unit,
    onSongClick: (ObservableSong) -> Unit,
    onFavoriteClick: (ObservableSong) -> Unit,
    onDownloadClick: (ObservableSong) -> Unit,
    viewModel: MusicViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp),
        verticalArrangement = Arrangement.Top,
        userScrollEnabled = true
    ) {
        items(songs) { song ->
            SongItem(
                song = song,
                onSongClick = { onSongClick(song) },
                onFavoriteClick = { onFavoriteClick(song) },
                onDownloadClick = { onDownloadClick(song) },
                downloadStatus = viewModel.checkSongStatus(song.song),
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        
        item {
            if (songs.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (loadingMore) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = ChineseRed
                        )
                    } else if (reachedEnd) {
                        Text(
                            text = "已经到底了",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    } else {
                        TextButton(
                            onClick = onLoadMore
                        ) {
                            Text("加载更多")
                        }
                    }
                }
            }
        }
    }
}

