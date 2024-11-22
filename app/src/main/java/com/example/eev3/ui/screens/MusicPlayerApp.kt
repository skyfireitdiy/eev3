package com.example.eev3.ui.screens

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import com.example.eev3.data.Quotes
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.activity.compose.rememberLauncherForActivityResult
import com.example.eev3.viewmodel.MusicViewModel.ImportMode
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(
    ExperimentalFoundationApi::class, 
    ExperimentalMaterialApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun MusicPlayerApp(
    viewModel: MusicViewModel = viewModel(
        factory = MusicViewModelFactory(LocalContext.current)
    ),
    onBackPressed: () -> Unit
) {
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument()
    ) { uri ->
        uri?.let { viewModel.exportFavorites(it) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importFavoritesPreview(it) }
    }

    var searchQuery by remember { mutableStateOf("") }
    var currentSong by remember { mutableStateOf<Song?>(null) }
    var showPlayer by remember { mutableStateOf(false) }
    
    // 使用 Int 来表示当前页面
    var currentPage by remember { mutableStateOf(0) }
    
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
    
    // 添加记录进入播放页面前的页面索引
    var lastPageBeforePlayer by remember { mutableStateOf(0) }
    
    // 添加随机语句状态
    var randomQuote by remember { mutableStateOf(Quotes.getRandomQuote()) }
    
    // 处理返回键
    BackHandler(enabled = showPlayer) {
        showPlayer = false
        viewModel.onPlayerVisibilityChanged(false)
        // 返回到之前的页面
        coroutineScope.launch {
            pagerState.scrollToPage(lastPageBeforePlayer)
        }
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
                    modifier = Modifier.fillMaxSize(),
                    isFavorite = currentSong?.let { song ->
                        viewModel.favorites.collectAsStateWithLifecycle().value.any { it.song.url == song.url }
                    } ?: false,
                    onFavoriteClick = {
                        currentSong?.let { song ->
                            viewModel.toggleFavorite(ObservableSong(song))
                        }
                    },
                    isPlaying = viewModel.isPlaying.collectAsStateWithLifecycle().value,
                    onPlayPause = { viewModel.playPause() },
                    onPrevious = { viewModel.playPrevious() },
                    onNext = { viewModel.playNext() },
                    onRepeatMode = { viewModel.toggleRepeatMode() },
                    onVolumeChange = { viewModel.setVolume(it) },
                    repeatMode = viewModel.repeatMode.collectAsStateWithLifecycle().value,
                    volume = viewModel.volume.collectAsStateWithLifecycle().value
                )
            }
        } else {
            // 主页面
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    Column {
                        // 修改顶部横幅部分
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "46VV",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = randomQuote,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                        
                        // 原有的顶部栏
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
                                    text = when (currentPage) {
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
                                    // 在收藏页面显���入导出按钮
                                    if (currentPage == 0) {
                                        OutlinedButton(
                                            onClick = {
                                                exportLauncher.launch("favorites.json")
                                            },
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text(
                                                "导出",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                importLauncher.launch(arrayOf("application/json"))
                                            },
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text(
                                                "导入",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                    Text(
                                        text = "缓存: ${viewModel.formatCacheSize(cacheSize)}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    TextButton(
                                        onClick = { viewModel.showClearCacheDialog() }
                                    ) {
                                        Text("清除")
                                    }
                                }
                            }
                        }
                    }
                },
                bottomBar = {
                    Column {
                        // 播放控制条半透明
                        if (currentPlayingSong != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                tonalElevation = 2.dp
                            ) {
                                PlayerControls(
                                    song = currentPlayingSong!!,
                                    onClick = { 
                                        showPlayer = true
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        
                        // 底部导航栏半透明
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            tonalElevation = 2.dp
                        ) {
                            NavigationBar(
                                containerColor = Color.Transparent  // 导航栏容器透明
                            ) {
                                NavigationBarItem(
                                    icon = { Icon(Icons.Default.Favorite, contentDescription = "收藏") },
                                    label = { Text("收藏") },
                                    selected = currentPage == 0,
                                    onClick = { currentPage = 0 }
                                )
                                NavigationBarItem(
                                    icon = { Icon(Icons.Default.Search, contentDescription = "搜索") },
                                    label = { Text("搜索") },
                                    selected = currentPage == 1,
                                    onClick = { currentPage = 1 }
                                )
                                NavigationBarItem(
                                    icon = { Icon(Icons.Default.Star, contentDescription = "新歌") },
                                    label = { Text("新歌") },
                                    selected = currentPage == 2,
                                    onClick = { currentPage = 2 }
                                )
                                NavigationBarItem(
                                    icon = { Icon(Icons.Default.List, contentDescription = "TOP") },
                                    label = { Text("TOP") },
                                    selected = currentPage == 3,
                                    onClick = { currentPage = 3 }
                                )
                                NavigationBarItem(
                                    icon = { Icon(Icons.Default.MusicNote, contentDescription = "DJ") },
                                    label = { Text("DJ") },
                                    selected = currentPage == 4,
                                    onClick = { currentPage = 4 }
                                )
                            }
                        }
                    }
                },
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { paddingValues ->
                // 列表内容半透明
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.85f)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        when (currentPage) {
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
                                                    lastPageBeforePlayer = currentPage // 记录当前页面
                                                    currentSong = song.song
                                                    viewModel.loadPlayerData(song.song, MusicViewModel.PlaylistSource.FAVORITES)
                                                    showPlayer = true
                                                },
                                                onFavoriteClick = { viewModel.toggleFavorite(song) },
                                                onDownloadClick = { viewModel.downloadSong(song.song) },
                                                onPlayMVClick = { viewModel.playMV(song.song) },
                                                onDownloadMVClick = { viewModel.downloadMV(song.song) },
                                                downloadStatus = viewModel.checkSongStatus(song.song),
                                                modifier = Modifier.padding(vertical = 4.dp)
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
                                                        lastPageBeforePlayer = currentPage // 记录当前页面
                                                        currentSong = song.song
                                                        viewModel.loadPlayerData(song.song, MusicViewModel.PlaylistSource.SEARCH)
                                                        showPlayer = true
                                                    },
                                                    onFavoriteClick = { viewModel.toggleFavorite(song) },
                                                    onDownloadClick = { viewModel.downloadSong(song.song) },
                                                    onPlayMVClick = { viewModel.playMV(song.song) },
                                                    onDownloadMVClick = { viewModel.downloadMV(song.song) },
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
                                    songs = viewModel.newRankSongs.collectAsStateWithLifecycle(emptyList()).value,
                                    loadingMore = viewModel.newRankLoadingMore.collectAsStateWithLifecycle().value,
                                    reachedEnd = viewModel.newRankReachedEnd.collectAsStateWithLifecycle().value,
                                    onLoadMore = { viewModel.loadMoreRank(MusicViewModel.RankType.NEW) },
                                    onRefresh = { viewModel.loadRankSongs(MusicViewModel.RankType.NEW, 1, true) },
                                    onSongClick = { song ->
                                        lastPageBeforePlayer = currentPage
                                        currentSong = song.song
                                        viewModel.loadPlayerData(song.song, MusicViewModel.PlaylistSource.NEW_RANK)
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
                                    songs = viewModel.topRankSongs.collectAsStateWithLifecycle(emptyList()).value,
                                    loadingMore = viewModel.topRankLoadingMore.collectAsStateWithLifecycle().value,
                                    reachedEnd = viewModel.topRankReachedEnd.collectAsStateWithLifecycle().value,
                                    onLoadMore = { viewModel.loadMoreRank(MusicViewModel.RankType.TOP) },
                                    onRefresh = { viewModel.loadRankSongs(MusicViewModel.RankType.TOP, 1, true) },
                                    onSongClick = { song ->
                                        lastPageBeforePlayer = currentPage
                                        currentSong = song.song
                                        viewModel.loadPlayerData(song.song, MusicViewModel.PlaylistSource.TOP_RANK)
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
                                    songs = viewModel.djDanceSongs.collectAsStateWithLifecycle(emptyList()).value,
                                    loadingMore = viewModel.djDanceLoadingMore.collectAsStateWithLifecycle().value,
                                    reachedEnd = viewModel.djDanceReachedEnd.collectAsStateWithLifecycle().value,
                                    onLoadMore = { viewModel.loadMoreRank(MusicViewModel.RankType.DJ_DANCE) },
                                    onRefresh = { viewModel.loadRankSongs(MusicViewModel.RankType.DJ_DANCE, 1, true) },
                                    onSongClick = { song ->
                                        lastPageBeforePlayer = currentPage
                                        currentSong = song.song
                                        viewModel.loadPlayerData(song.song, MusicViewModel.PlaylistSource.DJ_DANCE)
                                        showPlayer = true
                                    },
                                    onFavoriteClick = { viewModel.toggleFavorite(it) },
                                    onDownloadClick = { viewModel.downloadSong(it.song) },
                                    viewModel = viewModel
                                )
                            }
                        }
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
                    text = { Text("歌《${error.song.title}播放失败，可能链接已失效。") },
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
                    "确定要清除所有缓存的音乐文件吗？\n当前缓存小: ${viewModel.formatCacheSize(cacheSize)}"
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

    // 在 MusicPlayerApp 的 Composable 函数中添加状态收集
    val showImportDialog by viewModel.showImportDialog.collectAsStateWithLifecycle(null)

    // 添加导入确认对话框
    if (showImportDialog != null) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelImport() },
            title = { Text("导入收藏") },
            text = {
                Column {
                    Text("发现 ${showImportDialog?.size} 首歌曲，请选择导入方式：")
                    Text(
                        "覆盖：清空当前收藏列表后导入\n合并：保留当前收藏并添加新歌曲",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = { viewModel.confirmImport(ImportMode.OVERRIDE) }
                    ) {
                        Text("覆盖")
                    }
                    TextButton(
                        onClick = { viewModel.confirmImport(ImportMode.MERGE) }
                    ) {
                        Text("合并")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelImport() }) {
                    Text("取消")
                }
            }
        )
    }
}

// 添加榜单页面组件
@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun RankListPage(
    songs: List<ObservableSong>,
    loadingMore: Boolean,
    reachedEnd: Boolean,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    onSongClick: (ObservableSong) -> Unit,
    onFavoriteClick: (ObservableSong) -> Unit,
    onDownloadClick: (ObservableSong) -> Unit,
    viewModel: MusicViewModel
) {
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            onRefresh()
            isRefreshing = false
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
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
                    onPlayMVClick = { viewModel.playMV(song.song) },
                    onDownloadMVClick = { viewModel.downloadMV(song.song) },
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

        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

