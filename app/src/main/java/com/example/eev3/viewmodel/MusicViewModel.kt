package com.example.eev3.viewmodel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eev3.data.ObservableSong
import com.example.eev3.data.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.eev3.data.FavoritesDataStore
import com.example.eev3.data.PlayerData
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import kotlinx.coroutines.delay
import com.example.eev3.data.LyricLine
import okhttp3.FormBody
import com.google.gson.Gson
import com.example.eev3.data.MusicCache
import com.example.eev3.service.MusicService
import android.os.Build
import java.io.File
import java.net.URI
import android.media.MediaScannerConnection
import android.os.Environment
import com.example.eev3.data.DownloadStatus
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive

class MusicViewModel(
    private val favoritesDataStore: FavoritesDataStore,
    private val musicCache: MusicCache,
    private val context: Context
) : ViewModel() {
    private val BASE_URL = "http://www.eev3.com"
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    private val gson = Gson()
    
    private val _searchResults = MutableStateFlow<List<ObservableSong>>(emptyList())
    val searchResults: StateFlow<List<ObservableSong>> = _searchResults

    private val _favorites = MutableStateFlow<List<ObservableSong>>(emptyList())
    val favorites: StateFlow<List<ObservableSong>> = _favorites

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError

    private val _currentPlayerData = MutableStateFlow<PlayerData?>(null)
    val currentPlayerData: StateFlow<PlayerData?> = _currentPlayerData

    private var exoPlayer: ExoPlayer? = null
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition
    
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration

    private var currentPlayingSong: Song? = null
    private var currentAudioUrl: String? = null

    private val _lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val lyrics: StateFlow<List<LyricLine>> = _lyrics
    
    private val _currentLyricIndex = MutableStateFlow(-1)
    val currentLyricIndex: StateFlow<Int> = _currentLyricIndex

    // 播放模式
    enum class PlayMode {
        SEQUENCE,    // 列表顺序播放
        SINGLE_LOOP, // 单曲循环
        RANDOM,      // 随机播放
        ONCE        // 单次播放
    }

    private val _playMode = MutableStateFlow(PlayMode.SEQUENCE)
    val playMode: StateFlow<PlayMode> = _playMode

    // 添加播放列表来源枚举
    enum class PlaylistSource {
        FAVORITES,   // 收藏列表
        SEARCH,      // 搜索结果
        NEW_RANK,    // 新歌榜
        TOP_RANK,    // TOP榜单
        DJ_DANCE     // DJ舞曲
    }

    // 当前播放列表来源
    private var currentPlaylistSource = PlaylistSource.FAVORITES
    private var currentPlayingIndex = -1

    // 修改播放列表取逻辑
    private val currentPlaylist: List<Song>
        get() = when (currentPlaylistSource) {
            PlaylistSource.FAVORITES -> _favorites.value.map { it.song }
            PlaylistSource.SEARCH -> _searchResults.value.map { it.song }
            PlaylistSource.NEW_RANK -> _newRankSongs.value.map { it.song }
            PlaylistSource.TOP_RANK -> _topRankSongs.value.map { it.song }
            PlaylistSource.DJ_DANCE -> _djDanceSongs.value.map { it.song }
        }

    // 添加播放失败处理的状态
    sealed class PlaybackError {
        data class PlaybackTimeout(val song: Song) : PlaybackError()
    }

    private val _playbackError = MutableStateFlow<PlaybackError?>(null)
    val playbackError: StateFlow<PlaybackError?> = _playbackError

    // 添加播放器状态
    private var shouldResumePlayback = false
    private var lastPosition = 0L

    // 添加当前播放歌曲的状态
    private val _currentPlayingSongState = MutableStateFlow<Song?>(null)
    val currentPlayingSongState: StateFlow<Song?> = _currentPlayingSongState

    // 添加清除缓存相关的状态
    private val _showClearCacheDialog = MutableStateFlow(false)
    val showClearCacheDialog: StateFlow<Boolean> = _showClearCacheDialog

    private val _cacheSize = MutableStateFlow<Long>(0)
    val cacheSize: StateFlow<Long> = _cacheSize

    private var headsetReceiver: BroadcastReceiver? = null

    // 添加下载状态
    private val _downloadStatus = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val downloadStatus: StateFlow<Map<String, DownloadStatus>> = _downloadStatus.asStateFlow()

    // 添加提示消息状态
    data class DownloadTip(
        val message: String,
        val path: String? = null
    )

    private val _downloadTip = MutableStateFlow<DownloadTip?>(null)
    val downloadTip: StateFlow<DownloadTip?> = _downloadTip.asStateFlow()

    // 添加分页相关状态
    private var currentPage = 1
    private var totalPages = 1
    private var isLastPage = false
    private var currentKeyword = ""

    // 添加加载状态
    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore

    // 添加到底提示状态
    private val _reachedEnd = MutableStateFlow(false)
    val reachedEnd: StateFlow<Boolean> = _reachedEnd

    // 添加榜单类型枚举
    enum class RankType {
        NEW,        // 新歌榜
        TOP,        // TOP榜单
        DJ_DANCE    // DJ舞曲
    }

    // 加榜单相关状态
    private val _rankSongs = MutableStateFlow<List<ObservableSong>>(emptyList())
    val rankSongs: StateFlow<List<ObservableSong>> = _rankSongs

    private var rankCurrentPage = 1
    private var rankTotalPages = 1
    private var isRankLastPage = false
    private var isRankLoading = false
    private var currentRankType: RankType? = null

    // 添加榜单加载状态
    private val _rankLoadingMore = MutableStateFlow(false)
    val rankLoadingMore: StateFlow<Boolean> = _rankLoadingMore

    // 添加榜单到底提示状态
    private val _rankReachedEnd = MutableStateFlow(false)
    val rankReachedEnd: StateFlow<Boolean> = _rankReachedEnd

    // 添加 isLoading 变量
    private var isLoading = false

    // 为每个榜单类型添加独立的状态
    private val _newRankSongs = MutableStateFlow<List<ObservableSong>>(emptyList())
    val newRankSongs: StateFlow<List<ObservableSong>> = _newRankSongs

    private val _topRankSongs = MutableStateFlow<List<ObservableSong>>(emptyList())
    val topRankSongs: StateFlow<List<ObservableSong>> = _topRankSongs

    private val _djDanceSongs = MutableStateFlow<List<ObservableSong>>(emptyList())
    val djDanceSongs: StateFlow<List<ObservableSong>> = _djDanceSongs

    // 每个榜单的加态
    private val _newRankLoadingMore = MutableStateFlow(false)
    val newRankLoadingMore: StateFlow<Boolean> = _newRankLoadingMore

    private val _topRankLoadingMore = MutableStateFlow(false)
    val topRankLoadingMore: StateFlow<Boolean> = _topRankLoadingMore

    private val _djDanceLoadingMore = MutableStateFlow(false)
    val djDanceLoadingMore: StateFlow<Boolean> = _djDanceLoadingMore

    // 每个榜单的到底状态
    private val _newRankReachedEnd = MutableStateFlow(false)
    val newRankReachedEnd: StateFlow<Boolean> = _newRankReachedEnd

    private val _topRankReachedEnd = MutableStateFlow(false)
    val topRankReachedEnd: StateFlow<Boolean> = _topRankReachedEnd

    private val _djDanceReachedEnd = MutableStateFlow(false)
    val djDanceReachedEnd: StateFlow<Boolean> = _djDanceReachedEnd

    // 每个榜单的当前页码
    private var newRankCurrentPage = 1
    private var topRankCurrentPage = 1
    private var djDanceCurrentPage = 1

    // 每个榜单的加载状态
    private var isNewRankLoading = false
    private var isTopRankLoading = false
    private var isDjDanceLoading = false

    // 添加 MV 相关状态
    private val _mvCaching = MutableStateFlow(false)
    val mvCaching: StateFlow<Boolean> = _mvCaching

    // 添加广播接收器变量
    private var controlReceiver: BroadcastReceiver? = null

    // 添加加载状态记录
    private var hasLoadedNewRank = false
    private var hasLoadedTopRank = false
    private var hasLoadedDjDance = false

    // 修改重复模式状态的初始值为列表循环
    private val _repeatMode = MutableStateFlow(ExoPlayer.REPEAT_MODE_ALL)
    val repeatMode: StateFlow<Int> = _repeatMode

    // 添加音量状态
    private val _volume = MutableStateFlow(1f)  // 默认音量为1（最大）
    val volume: StateFlow<Float> = _volume

    // 添加获取随机播放状态的方法
    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled

    // 添加进度监听
    private var progressJob: Job? = null

    // 添加播放器配置状态
    private var playerConfig = PlayerConfig(
        repeatMode = ExoPlayer.REPEAT_MODE_ALL,
        shuffleEnabled = false
    )

    // 数据类来保存播放器配置
    private data class PlayerConfig(
        val repeatMode: Int,
        val shuffleEnabled: Boolean
    )

    // 添加播放进度监听器
    private var positionUpdateJob: Job? = null

    // 添加标志位防止重复触发
    private var isHandlingPlaybackEnd = false

    // 添加标志位表示是否正在加载下一首
    private var isLoadingNext = false

    init {
        // 设置默认播放模式为列表循环
        _repeatMode.value = ExoPlayer.REPEAT_MODE_ALL
        _shuffleEnabled.value = false
        playerConfig = PlayerConfig(ExoPlayer.REPEAT_MODE_ALL, false)
        println("MusicViewModel: 初始化播放模式为列表循环")
        
        // 加载保存的收藏
        viewModelScope.launch {
            favoritesDataStore.favoritesFlow.collect { songs ->
                _favorites.value = songs.map { ObservableSong(it.copy(isFavorite = true)) }
            }
        }

        // 监听播放完成事件
        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    _duration.value = exoPlayer?.duration ?: 0
                } else if (state == Player.STATE_ENDED) {
                    playNext()
                }
            }
            
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                // 播放状态改变时更新通知栏
                updateNotification()
            }
        })

        // 初始化缓存大小
        updateCacheSize()

        // 注册耳机插拔监听器
        registerHeadsetReceiver()

        // 注册广播接收器
        val filter = IntentFilter().apply {
            addAction(MusicService.BROADCAST_PREVIOUS)
            addAction(MusicService.BROADCAST_PLAY_PAUSE)
            addAction(MusicService.BROADCAST_NEXT)
            addAction(MusicService.BROADCAST_FAVORITE)
        }
        
        // 创建广播接收器
        controlReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                println("MusicViewModel: 收到广播: ${intent?.action}")
                when (intent?.action) {
                    MusicService.BROADCAST_PREVIOUS -> {
                        println("MusicViewModel: 播放上一首")
                        viewModelScope.launch(Dispatchers.Main) {
                            playPrevious()
                            // 更新通知栏
                            updateNotification()
                        }
                    }
                    MusicService.BROADCAST_PLAY_PAUSE -> {
                        println("MusicViewModel: 切换播放状态")
                        viewModelScope.launch(Dispatchers.Main) {
                            playPause()
                            // 更新通知栏
                            updateNotification()
                        }
                    }
                    MusicService.BROADCAST_NEXT -> {
                        println("MusicViewModel: 播放下一首")
                        viewModelScope.launch(Dispatchers.Main) {
                            playNext()
                            // 更新通知栏
                            updateNotification()
                        }
                    }
                    MusicService.BROADCAST_FAVORITE -> {
                        println("MusicViewModel: 切换收藏状态")
                        currentPlayingSong?.let { song ->
                            viewModelScope.launch(Dispatchers.Main) {
                                toggleFavorite(ObservableSong(song))
                                // 更新通知栏
                                updateNotification()
                            }
                        }
                    }
                }
            }
        }
        
        // 使用 LocalBroadcastManager 注册广播接收器
        LocalBroadcastManager.getInstance(context)
            .registerReceiver(controlReceiver!!, filter)
    }

    private fun registerHeadsetReceiver() {
        headsetReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                    // 耳机断开暂停播放
                    exoPlayer?.pause()
                }
            }
        }

        // 注册广播接收器
        context.registerReceiver(
            headsetReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        )
    }

    fun searchSongs(query: String, page: Int = 1) {
        if (isLoading) return
        isLoading = true
        
        // 如果是新搜索，重置状态
        if (page == 1) {
            currentKeyword = query
            _searchResults.value = emptyList()
            _reachedEnd.value = false
        }
        
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
                    val url = if (page == 1) {
                        "$BASE_URL/so/$encodedQuery.html"
                    } else {
                        "$BASE_URL/so/$encodedQuery/$page.html"
                    }
                    
                    println("MusicViewModel: 搜索URL=$url")
                    
                    val document = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                        .get()
                    
                    // 解析总数和总页数
                    val pageDataText = document.selectFirst(".pagedata")?.text() ?: ""
                    val totalCountMatch = "共有(\\d+)首搜索结果".toRegex().find(pageDataText)
                    val totalCount = totalCountMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    totalPages = (totalCount + 59) / 60 // 每页60首歌曲
                    
                    println("MusicViewModel: 总数=$totalCount, 总页数=$totalPages, 当前页=$page")
                    
                    val songElements = document.select(".play_list ul li")
                    val songs = songElements.mapNotNull { element ->
                        val nameElement = element.selectFirst(".name a") ?: return@mapNotNull null
                        val title = nameElement.text()
                            .replace("&nbsp;", " ")
                            .replace("[MP3_LRC]", "")
                            .trim()
                        val songPath = nameElement.attr("href")
                        val songUrl = when {
                            songPath.startsWith("http") -> songPath
                            songPath.startsWith("/") -> "$BASE_URL$songPath"
                            else -> "$BASE_URL/$songPath"
                        }
                        
                        // 检查是否有 MV
                        val mvElement = element.selectFirst(".mv a")
                        val hasMV = mvElement != null
                        val mvUrl = if (hasMV) {
                            val mvPath = mvElement?.attr("href") ?: ""
                            when {
                                mvPath.startsWith("http") -> mvPath
                                mvPath.startsWith("/") -> "$BASE_URL$mvPath"
                                else -> "$BASE_URL/$mvPath"
                            }
                        } else ""
                        
                        if (title.isNotEmpty() && songUrl.isNotEmpty()) {
                            ObservableSong(Song(title, songUrl, hasMV = hasMV, mvUrl = mvUrl))
                        } else null
                    }
                    
                    // 更新搜索结果
                    if (page == 1) {
                        _searchResults.value = songs
                    } else {
                        _searchResults.value = _searchResults.value + songs
                    }
                    
                    currentPage = page
                    isLastPage = page >= totalPages
                    
                    if (songs.isEmpty()) {
                        _searchError.value = "未找到相关歌曲"
                    }
                    
                    // 到达最后一页，示提示
                    if (isLastPage) {
                        _reachedEnd.value = true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _searchError.value = when {
                    e.message?.contains("Unable to resolve host") == true -> "网络连接失败，请检查网络设置"
                    e.message?.contains("timeout") == true -> "网络连接超时，请稍后重试"
                    e.message != null -> "搜索出错: ${e.message}"
                    else -> "网络连接失败，请检查网络设置"
                }
            } finally {
                isLoading = false
                _loadingMore.value = false
            }
        }
    }

    // 加载更多
    fun loadMore() {
        if (!isLoading && !isLastPage) {
            _loadingMore.value = true
            searchSongs(currentKeyword, currentPage + 1)
        }
    }

    fun toggleFavorite(song: ObservableSong) {
        println("MusicViewModel: 切换收藏状态: ${song.song.title}")
        println("MusicViewModel: 当前收藏状态: ${song.isFavorite}")
        
        // 更新收藏列表
        val currentFavorites = _favorites.value.toMutableList()
        val existingSong = currentFavorites.find { it.song.url == song.song.url }
        val newFavoriteState = existingSong == null // 如果不在收藏列表中，则为新的收藏状态
        
        if (existingSong != null) {
            // 歌曲已在收藏列表中，移除它
            println("MusicViewModel: 从收藏中移除")
            currentFavorites.remove(existingSong)
            song.isFavorite = false
        } else {
            // 歌曲不在收藏列表中，添加它
            println("MusicViewModel: 添加到收藏")
            song.isFavorite = true
            currentFavorites.add(song)
        }
        
        _favorites.value = currentFavorites
        
        // 同步更新搜索结果列表中的收藏状态
        _searchResults.value.forEach { 
            if (it.song.url == song.song.url) {
                it.isFavorite = newFavoriteState
            }
        }
        
        // 同步更新新歌榜列表中的收藏状态
        _newRankSongs.value.forEach {
            if (it.song.url == song.song.url) {
                it.isFavorite = newFavoriteState
            }
        }
        
        // 同步更新TOP榜列表中的收藏状态
        _topRankSongs.value.forEach {
            if (it.song.url == song.song.url) {
                it.isFavorite = newFavoriteState
            }
        }
        
        // 同步更新DJ舞曲列表中的收藏状态
        _djDanceSongs.value.forEach {
            if (it.song.url == song.song.url) {
                it.isFavorite = newFavoriteState
            }
        }
        
        // 同步更新当前播放歌曲的收藏状态
        currentPlayingSong = currentPlayingSong?.let {
            if (it.url == song.song.url) {
                it.copy(isFavorite = newFavoriteState)
            } else it
        }
        
        // 强制触发状态更新
        _searchResults.value = ArrayList(_searchResults.value)
        _newRankSongs.value = ArrayList(_newRankSongs.value)
        _topRankSongs.value = ArrayList(_topRankSongs.value)
        _djDanceSongs.value = ArrayList(_djDanceSongs.value)
        
        // 保存更新后的收藏列表
        viewModelScope.launch {
            favoritesDataStore.saveFavorites(currentFavorites.map { it.song })
        }
        
        // 更新通知栏
        updateNotification()
    }

    fun clearSearchError() {
        _searchError.value = null
    }

    data class PlayResponse(
        val msg: Int,
        val lkid: Long,
        val title: String,
        val pic: String,
        val url: String
    )

    fun loadPlayerData(song: Song, source: PlaylistSource) {
        println("MusicViewModel: 开始播放 ${song.title}")
        
        // 更新播放列表来源和索引
        currentPlaylistSource = source
        val playlist = currentPlaylist
        currentPlayingIndex = playlist.indexOfFirst { it.url == song.url }
        println("MusicViewModel: 播放列表大小=${playlist.size}, 当前索引=$currentPlayingIndex")
        
        _currentPlayingSongState.value = song
        
        // 如果是同一首，直接返回
        if (song == currentPlayingSong && _currentPlayerData.value != null) {
            println("MusicViewModel: 相同歌曲，直接返回")
            return
        }

        // 重置播放器状态
        _currentPosition.value = 0
        _duration.value = 0
        currentPlayingSong = song
        
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val songId = song.url.substringAfterLast("/")
                        .substringBefore(".html")
                    println("MusicViewModel: 歌曲ID=$songId")
                    
                    // 检查是否已下载或缓存
                    val sanitizedTitle = sanitizeFileName(song.title)
                    val downloadedFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "$sanitizedTitle.mp3")
                    val cacheFile = File(context.cacheDir, "music/$songId.mp3")
                    
                    if (downloadedFile.exists() || cacheFile.exists()) {
                        println("MusicViewModel: 使用本地文件")
                        val audioUri = if (downloadedFile.exists()) {
                            downloadedFile.toURI().toString()
                        } else {
                            cacheFile.toURI().toString()
                        }
                        
                        // 检查歌词缓存
                        val lyricsFile = File(context.cacheDir, "lyrics/$songId.lrc")
                        if (lyricsFile.exists()) {
                            println("MusicViewModel: 使用缓存的歌词")
                            val lyricsContent = lyricsFile.readText()
                            _lyrics.value = parseLyrics(lyricsContent)
                        } else {
                            // 歌词缓存不存在，从远程获取
                            println("MusicViewModel: 歌词缓存不存在，从远程获取")
                            val lrcUrl = "$BASE_URL/plug/down.php?ac=music&lk=lrc&id=$songId"
                            val lrcRequest = Request.Builder()
                                .url(lrcUrl)
                                .build()
                            
                            client.newCall(lrcRequest).execute().use { lrcResponse ->
                                val lrcContent = lrcResponse.body?.string() ?: ""
                                println("MusicViewModel: 缓存新获取的歌词")
                                val lyricsUri = musicCache.cacheLyrics(song.url, lrcContent)
                                println("MusicViewModel: 歌词缓存完成 lyricsUri=$lyricsUri")
                                _lyrics.value = parseLyrics(lrcContent)
                            }
                        }
                        
                        // 检查封面缓存
                        val coverFile = File(context.cacheDir, "covers/$songId.jpg")
                        val coverUri = if (coverFile.exists()) {
                            println("MusicViewModel: 使用缓存的封面")
                            coverFile.toURI().toString()
                        } else {
                            // 封面缓存不存在，从远程获取
                            println("MusicViewModel: 封面缓存不存在，从远程获取")
                            val formBody = FormBody.Builder()
                                .add("id", songId)
                                .add("type", "music")
                                .build()
                            
                            val request = Request.Builder()
                                .url("$BASE_URL/js/play.php")
                                .post(formBody)
                                .build()
                            
                            var newCoverUri: String? = null
                            client.newCall(request).execute().use { response ->
                                val responseBody = response.body?.string()
                                if (responseBody != null) {
                                    val playResponse = gson.fromJson(responseBody, PlayResponse::class.java)
                                    if (playResponse.msg == 1) {
                                        println("MusicViewModel: 缓存新获取的封面")
                                        newCoverUri = musicCache.cacheCover(song.url, playResponse.pic)
                                        println("MusicViewModel: 封面缓存完成 coverUri=$newCoverUri")
                                    }
                                }
                            }
                            newCoverUri
                        }
                        
                        // 更新播放器数据
                        _currentPlayerData.value = PlayerData(
                            coverImage = coverUri ?: "",
                            title = song.title,
                            audioUrl = audioUri
                        )
                        
                        // 立即启动服务
                        withContext(Dispatchers.Main) {
                            println("MusicViewModel: 更新播放数据后启动服务")
                            setupPlayer(audioUri)
                        }
                    } else {
                        // 如果没有本地文件，尝试从网络获取
                        println("MusicViewModel: 从服获取数据")
                        val formBody = FormBody.Builder()
                            .add("id", songId)
                            .add("type", "music")
                            .build()
                        
                        val request = Request.Builder()
                            .url("$BASE_URL/js/play.php")
                            .post(formBody)
                            .build()
                        
                        client.newCall(request).execute().use { response ->
                            val responseBody = response.body?.string()
                            if (responseBody != null) {
                                val playResponse = gson.fromJson(responseBody, PlayResponse::class.java)
                                if (playResponse.msg == 1) {
                                    println("MusicViewModel: 开始缓存音乐")
                                    val audioUrl = playResponse.url
                                    val cacheUri = musicCache.cacheMusic(song.url, audioUrl)
                                    println("MusicViewModel: 音乐缓存完成")
                                    
                                    // 更新缓存大小
                                    updateCacheSize()
                                    
                                    println("MusicViewModel: 开始缓存封")
                                    val coverUri = musicCache.cacheCover(song.url, playResponse.pic)
                                    println("MusicViewModel: 封面缓存完成 coverUri=$coverUri")
                                    
                                    println("MusicViewModel: 开始获取歌词")
                                    val lrcUrl = "$BASE_URL/plug/down.php?ac=music&lk=lrc&id=$songId"
                                    val lrcRequest = Request.Builder()
                                        .url(lrcUrl)
                                        .build()
                                    
                                    client.newCall(lrcRequest).execute().use { lrcResponse ->
                                        val lrcContent = lrcResponse.body?.string() ?: ""
                                        println("MusicViewModel: 开始缓存歌词")
                                        val lyricsUri = musicCache.cacheLyrics(song.url, lrcContent)
                                        println("MusicViewModel: 歌词缓存完成 lyricsUri=$lyricsUri")
                                        _lyrics.value = parseLyrics(lrcContent)
                                        println("MusicViewModel: 歌词解析完成，行数=${_lyrics.value.size}")
                                    }
                                    
                                    println("MusicViewModel: 更新缓存大小显示")
                                    updateCacheSize()
                                    
                                    println("MusicViewModel: 所有内容缓存完成，开始播放")
                                    _currentPlayerData.value = PlayerData(
                                        coverImage = coverUri,
                                        title = playResponse.title.replace("[Mp3_Lrc]", "").trim(),
                                        audioUrl = cacheUri
                                    )
                                    
                                    // 立即启动服务
                                    withContext(Dispatchers.Main) {
                                        println("MusicViewModel: 更新播放数据后启动服务")
                                        setupPlayer(audioUrl)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                println("MusicViewModel: 加载播放数据失败 error=${e.message}")
                e.printStackTrace()
                _searchError.value = "加载播放数据失败"
                
                if (isHandlingPlaybackEnd) {
                    println("MusicViewModel: 加载失败，重新播放当前歌曲")
                    exoPlayer?.seekTo(0)
                    exoPlayer?.play()
                }
            }
        }
    }

    private fun parseLyrics(lrcContent: String): List<LyricLine> {
        val timeRegex = "\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})](.*)".toRegex()
        return lrcContent.lines()
            .mapNotNull { line ->
                timeRegex.matchEntire(line)?.let { matchResult ->
                    val (minutes, seconds, milliseconds, text) = matchResult.destructured
                    val timeMs = minutes.toLong() * 60 * 1000 +
                            seconds.toLong() * 1000 +
                            milliseconds.padEnd(3, '0').toLong()
                    LyricLine(timeMs, text.trim())
                }
            }
            .sortedBy { it.time }
    }
    
    // 更新当前歌位置
    private fun updateCurrentLyric(position: Long) {
        val lyrics = _lyrics.value
        if (lyrics.isEmpty()) return
        
        val index = lyrics.indexOfLast { it.time <= position }
        if (index != _currentLyricIndex.value) {
            _currentLyricIndex.value = index
        }
    }
    
    private fun setupPlayer(audioUrl: String) {
        println("MusicViewModel: 初始化播放器 audioUrl=$audioUrl")
        
        if (audioUrl == currentAudioUrl && exoPlayer != null) {
            println("MusicViewModel: 相同URL，只更新UI状态")
            return
        }
        
        currentAudioUrl = audioUrl
        
        // 使用保存的配置
        println("MusicViewModel: 使用配置: repeatMode=${playerConfig.repeatMode}, shuffle=${playerConfig.shuffleEnabled}")
        
        exoPlayer?.release()
        exoPlayer = ExoPlayer.Builder(context)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                // 先设置播放模式，再添加监听器
                repeatMode = Player.REPEAT_MODE_OFF  // 始终禁用 ExoPlayer 的自动重复
                shuffleModeEnabled = playerConfig.shuffleEnabled
                _repeatMode.value = playerConfig.repeatMode
                _shuffleEnabled.value = playerConfig.shuffleEnabled
                
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        println("MusicViewModel: 播放状态改变: $state")
                        when (state) {
                            Player.STATE_READY -> {
                                println("MusicViewModel: 播放就绪")
                                _duration.value = duration
                                play()
                                _isPlaying.value = true
                                startProgressUpdate()
                                startPositionUpdate()
                            }
                            Player.STATE_BUFFERING -> {
                                println("MusicViewModel: 正在缓冲")
                            }
                            Player.STATE_IDLE -> {
                                println("MusicViewModel: 播放器空闲")
                            }
                        }
                    }
                    
                    override fun onIsPlayingChanged(playing: Boolean) {
                        println("MusicViewModel: onIsPlayingChanged: playing=$playing, state=${playbackState}")
                        _isPlaying.value = playing
                        
                        if (playing) {
                            startProgressUpdate()
                            startPositionUpdate()
                        } else {
                            stopProgressUpdate()
                            stopPositionUpdate()
                        }
                        
                        updateNotification()
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        println("MusicViewModel: onMediaItemTransition: reason=$reason")
                        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                            println("MusicViewModel: 自动切换媒体项，处理播放结束")
                            handlePlaybackEnd()
                        }
                    }

                    override fun onRepeatModeChanged(repeatMode: Int) {
                        println("MusicViewModel: 忽略 ExoPlayer 重复模式变化: $repeatMode")
                    }

                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                        println("MusicViewModel: 使用配置的随机播放状态: ${playerConfig.shuffleEnabled}")
                        _shuffleEnabled.value = playerConfig.shuffleEnabled
                    }
                })
                
                // 设置媒体项
                setMediaItem(MediaItem.fromUri(audioUrl))
                
                // 禁用播放完成时的自动暂停
                setPlayWhenReady(true)
                
                // 准备播放
                prepare()
            }
    }
    
    fun playPause() {
        exoPlayer?.let {
            if (it.isPlaying) {
                it.pause()
            } else {
                it.play()
            }
            // 立即更新通知栏
            updateNotification()
        }
    }
    
    fun seekTo(position: Long) {
        println("MusicViewModel: 跳转到 ${position/1000}秒")
        exoPlayer?.seekTo(position)
    }
    
    fun setVolume(volume: Float) {
        exoPlayer?.let { player ->
            val clampedVolume = volume.coerceIn(0f, 1f)  // 确保音量在0-1之间
            player.volume = clampedVolume
            _volume.value = clampedVolume
            println("MusicViewModel: 设置音量为: $clampedVolume")
        }
    }
    
    fun getVolume(): Float = exoPlayer?.volume ?: 1f

    override fun onCleared() {
        super.onCleared()
        // 停止服务
        context.stopService(Intent(context, MusicService::class.java))
        
        // 取消注册广播接收器
        headsetReceiver?.let {
            context.unregisterReceiver(it)
        }
        headsetReceiver = null
        
        // 使用 LocalBroadcastManager 取消注册广播接收器
        controlReceiver?.let {
            LocalBroadcastManager.getInstance(context)
                .unregisterReceiver(it)
        }
        controlReceiver = null
        
        exoPlayer?.release()
        exoPlayer = null
        stopProgressUpdate()  // 停止进度更新
    }

    fun togglePlayMode() {
        _playMode.value = when (_playMode.value) {
            PlayMode.SEQUENCE -> PlayMode.SINGLE_LOOP
            PlayMode.SINGLE_LOOP -> PlayMode.RANDOM
            PlayMode.RANDOM -> PlayMode.ONCE
            PlayMode.ONCE -> PlayMode.SEQUENCE
        }
        
        // 更新 ExoPlayer 的循环模式
        exoPlayer?.repeatMode = when (_playMode.value) {
            PlayMode.SEQUENCE -> Player.REPEAT_MODE_ALL
            PlayMode.SINGLE_LOOP -> Player.REPEAT_MODE_ONE
            PlayMode.RANDOM -> Player.REPEAT_MODE_ALL  // 随机模式下也设置循环全部
            PlayMode.ONCE -> Player.REPEAT_MODE_OFF
        }
        
        // 设置随机播放
        exoPlayer?.shuffleModeEnabled = (_playMode.value == PlayMode.RANDOM)
    }

    // 修改播放下一曲的方法
    fun playNext() {
        println("MusicViewModel: 播放下一曲")
        
        // 如果正在加载下一首，跳过
        if (isLoadingNext) {
            println("MusicViewModel: 正在加载下一首，跳过重复请求")
            return
        }
        
        isLoadingNext = true
        
        // 停止进度更新
        stopProgressUpdate()
        stopPositionUpdate()
        
        // 暂停当前播放
        exoPlayer?.pause()
        
        viewModelScope.launch {
            try {
                when (currentPlaylistSource) {
                    PlaylistSource.FAVORITES -> {
                        val currentIndex = _favorites.value.indexOfFirst { it.song.url == currentPlayingSong?.url }
                        if (currentIndex != -1) {
                            val nextIndex = if (_shuffleEnabled.value) {
                                (0 until _favorites.value.size).random()
                            } else {
                                (currentIndex + 1) % _favorites.value.size
                            }
                            loadPlayerData(_favorites.value[nextIndex].song, PlaylistSource.FAVORITES)
                        }
                    }
                    PlaylistSource.SEARCH -> {
                        val currentIndex = _searchResults.value.indexOfFirst { it.song.url == currentPlayingSong?.url }
                        if (currentIndex != -1) {
                            val nextIndex = if (_shuffleEnabled.value) {
                                (0 until _searchResults.value.size).random()
                            } else {
                                (currentIndex + 1) % _searchResults.value.size
                            }
                            loadPlayerData(_searchResults.value[nextIndex].song, PlaylistSource.SEARCH)
                        }
                    }
                    PlaylistSource.NEW_RANK -> {
                        val currentIndex = _newRankSongs.value.indexOfFirst { it.song.url == currentPlayingSong?.url }
                        if (currentIndex != -1) {
                            val nextIndex = if (_shuffleEnabled.value) {
                                (0 until _newRankSongs.value.size).random()
                            } else {
                                (currentIndex + 1) % _newRankSongs.value.size
                            }
                            loadPlayerData(_newRankSongs.value[nextIndex].song, PlaylistSource.NEW_RANK)
                        }
                    }
                    PlaylistSource.TOP_RANK -> {
                        val currentIndex = _topRankSongs.value.indexOfFirst { it.song.url == currentPlayingSong?.url }
                        if (currentIndex != -1) {
                            val nextIndex = if (_shuffleEnabled.value) {
                                (0 until _topRankSongs.value.size).random()
                            } else {
                                (currentIndex + 1) % _topRankSongs.value.size
                            }
                            loadPlayerData(_topRankSongs.value[nextIndex].song, PlaylistSource.TOP_RANK)
                        }
                    }
                    PlaylistSource.DJ_DANCE -> {
                        val currentIndex = _djDanceSongs.value.indexOfFirst { it.song.url == currentPlayingSong?.url }
                        if (currentIndex != -1) {
                            val nextIndex = if (_shuffleEnabled.value) {
                                (0 until _djDanceSongs.value.size).random()
                            } else {
                                (currentIndex + 1) % _djDanceSongs.value.size
                            }
                            loadPlayerData(_djDanceSongs.value[nextIndex].song, PlaylistSource.DJ_DANCE)
                        }
                    }
                }
            } finally {
                isLoadingNext = false
            }
        }
    }

    // 修改播放上一曲的方法
    fun playPrevious() {
        println("MusicViewModel: 播放上一曲")
        
        // 如果是单次播放模式且已经是第一首，则不播放
        if (exoPlayer?.repeatMode == ExoPlayer.REPEAT_MODE_OFF) {
            when (currentPlaylistSource) {
                PlaylistSource.FAVORITES -> {
                    val currentIndex = _favorites.value.indexOfFirst { it.song.url == currentPlayingSong?.url }
                    if (currentIndex == 0) {
                        println("MusicViewModel: 单次播放模式，已是第一首")
                        return
                    }
                }
                PlaylistSource.SEARCH -> {
                    val currentIndex = _searchResults.value.indexOfFirst { it.song.url == currentPlayingSong?.url }
                    if (currentIndex == 0) {
                        println("MusicViewModel: 单次播放模式，已是第一首")
                        return
                    }
                }
                PlaylistSource.NEW_RANK -> {
                    val currentIndex = _newRankSongs.value.indexOfFirst { it.song.url == currentPlayingSong?.url }
                    if (currentIndex == 0) {
                        println("MusicViewModel: 单次播放模式，已是第一首")
                        return
                    }
                }
                PlaylistSource.TOP_RANK -> {
                    val currentIndex = _topRankSongs.value.indexOfFirst { it.song.url == currentPlayingSong?.url }
                    if (currentIndex == 0) {
                        println("MusicViewModel: 单播放式，已是第一首")
                        return
                    }
                }
                PlaylistSource.DJ_DANCE -> {
                    val currentIndex = _djDanceSongs.value.indexOfFirst { it.song.url == currentPlayingSong?.url }
                    if (currentIndex == 0) {
                        println("MusicViewModel: 单次播放模式，已是第一首")
                        return
                    }
                }
            }
        }
        
        when (currentPlaylistSource) {
            PlaylistSource.FAVORITES -> {
                val currentIndex = _favorites.value.indexOfFirst { it.song.url == currentPlayingSong?.url }
                if (currentIndex != -1) {
                    val prevIndex = if (exoPlayer?.shuffleModeEnabled == true) {
                        // 随机播放模式
                        (0 until _favorites.value.size).random()
                    } else {
                        // 列表循环模式
                        (currentIndex - 1 + _favorites.value.size) % _favorites.value.size
                    }
                    loadPlayerData(_favorites.value[prevIndex].song, PlaylistSource.FAVORITES)
                }
            }
            PlaylistSource.SEARCH -> {
                val currentIndex = _searchResults.value.indexOfFirst { it.song.url == currentPlayingSong?.url }
                if (currentIndex != -1) {
                    val prevIndex = if (exoPlayer?.shuffleModeEnabled == true) {
                        // 随机播放模式
                        (0 until _searchResults.value.size).random()
                    } else {
                        // 列表循环或单次播放模式
                        (currentIndex - 1 + _searchResults.value.size) % _searchResults.value.size
                    }
                    loadPlayerData(_searchResults.value[prevIndex].song, PlaylistSource.SEARCH)
                }
            }
            PlaylistSource.NEW_RANK -> {
                val currentIndex = _newRankSongs.value.indexOfFirst { it.song.url == currentPlayingSong?.url }
                if (currentIndex != -1) {
                    val prevIndex = if (exoPlayer?.shuffleModeEnabled == true) {
                        // 随播放模式
                        (0 until _newRankSongs.value.size).random()
                    } else {
                        // 列表循环或单次播放模式
                        (currentIndex - 1 + _newRankSongs.value.size) % _newRankSongs.value.size
                    }
                    loadPlayerData(_newRankSongs.value[prevIndex].song, PlaylistSource.NEW_RANK)
                }
            }
            PlaylistSource.TOP_RANK -> {
                val currentIndex = _topRankSongs.value.indexOfFirst { it.song.url == currentPlayingSong?.url }
                if (currentIndex != -1) {
                    val prevIndex = if (exoPlayer?.shuffleModeEnabled == true) {
                        // 随机播放模式
                        (0 until _topRankSongs.value.size).random()
                    } else {
                        // 列表循环或单次播放模式
                        (currentIndex - 1 + _topRankSongs.value.size) % _topRankSongs.value.size
                    }
                    loadPlayerData(_topRankSongs.value[prevIndex].song, PlaylistSource.TOP_RANK)
                }
            }
            PlaylistSource.DJ_DANCE -> {
                val currentIndex = _djDanceSongs.value.indexOfFirst { it.song.url == currentPlayingSong?.url }
                if (currentIndex != -1) {
                    val prevIndex = if (exoPlayer?.shuffleModeEnabled == true) {
                        // 随机播放模式
                        (0 until _djDanceSongs.value.size).random()
                    } else {
                        // 列表循环或单次播放模式
                        (currentIndex - 1 + _djDanceSongs.value.size) % _djDanceSongs.value.size
                    }
                    loadPlayerData(_djDanceSongs.value[prevIndex].song, PlaylistSource.DJ_DANCE)
                }
            }
        }
    }

    fun clearPlaybackError() {
        _playbackError.value = null
    }

    fun handlePlaybackTimeout(song: Song) {
        // 移除收藏
        val currentFavorites = _favorites.value.toMutableList()
        currentFavorites.removeIf { it.song == song }
        _favorites.value = currentFavorites
        
        // 保存更新后的收藏列表
        viewModelScope.launch {
            favoritesDataStore.saveFavorites(currentFavorites.map { it.song })
        }
    }

    fun onPlayerVisibilityChanged(visible: Boolean) {
        if (!visible) {
            // 界面隐藏时，保当前状态但不停止播放
            shouldResumePlayback = exoPlayer?.isPlaying ?: false
            lastPosition = exoPlayer?.currentPosition ?: 0
        } else {
            // 界面显示时，如果需要则复播放
            if (shouldResumePlayback) {
                exoPlayer?.play()
            }
        }
    }

    fun updateCacheSize() {
        viewModelScope.launch {
            try {
                val size = musicCache.getCacheSize()
                println("MusicViewModel: 当前缓存大小: ${formatCacheSize(size)}")
                _cacheSize.value = size
            } catch (e: Exception) {
                println("MusicViewModel: 更新缓存大小失败: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun showClearCacheDialog() {
        _showClearCacheDialog.value = true
    }

    fun dismissClearCacheDialog() {
        _showClearCacheDialog.value = false
    }

    fun clearCache() {
        viewModelScope.launch {
            try {
                // 取当前播放歌曲的URL
                val currentSongUrl = currentPlayingSong?.url
                
                if (currentSongUrl != null) {
                    println("MusicViewModel: 清除缓存时保护当前播放歌曲: ${currentPlayingSong?.title}")
                }
                
                // 清除缓存时跳过前播放的音乐
                musicCache.clearCache(skipUrls = listOfNotNull(currentSongUrl))
                
                // 清除缓存后立即更新缓存大小显示
                updateCacheSize()
                dismissClearCacheDialog()
                
                // 显示提示
                _downloadTip.value = DownloadTip(
                    message = if (currentSongUrl != null) {
                        "已清除缓存（保留当前播放歌曲）"
                    } else {
                        "已清除全部缓"
                    }
                )
            } catch (e: Exception) {
                println("MusicViewModel: 清除缓存失败: ${e.message}")
                e.printStackTrace()
                _downloadTip.value = DownloadTip(
                    message = "清除缓存失败: ${e.message ?: "未知错误"}"
                )
            }
        }
    }

    // 格式化缓存大小
    fun formatCacheSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024f)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024f * 1024f))
            else -> String.format("%.1f GB", bytes / (1024f * 1024f * 1024f))
        }
    }

    // 清文件名中的非法字符
    private fun sanitizeFileName(fileName: String): String {
        return fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), "_")
            .trim()
    }

    // 下载歌曲
    fun downloadSong(song: Song) {
        viewModelScope.launch {
            try {
                println("MusicViewModel: 开始下载 ${song.title}")
                _downloadStatus.update { it + (song.url to DownloadStatus.Downloading) }
                
                withContext(Dispatchers.IO) {
                    val songId = song.url.substringAfterLast("/")
                        .substringBefore(".html")
                    
                    // 获取下载目录
                    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                    downloadDir.mkdirs()
                    
                    // 准备目标文件名
                    val sanitizedTitle = sanitizeFileName(song.title)
                    val targetFile = File(downloadDir, "$sanitizedTitle.mp3")
                    
                    // 检查是否已缓存
                    if (musicCache.isCached(song.url, MusicCache.CacheType.MUSIC)) {
                        println("MusicViewModel: 使用缓存的音乐文件")
                        // 复制到下载目录
                        println("MusicViewModel: 复制到下载目录")
                        val cacheUri = musicCache.getCacheFileUri(song.url, MusicCache.CacheType.MUSIC)
                        File(URI(cacheUri)).copyTo(targetFile, overwrite = true)
                    } else {
                        println("MusicViewModel: 从服务器下载")
                        // 获取音乐真实地址并下载
                        val formBody = FormBody.Builder()
                            .add("id", songId)
                            .add("type", "music")
                            .build()
                        
                        val request = Request.Builder()
                            .url("$BASE_URL/js/play.php")
                            .post(formBody)
                            .build()
                        
                        client.newCall(request).execute().use { response ->
                            val responseBody = response.body?.string()
                            if (responseBody != null) {
                                val playResponse = gson.fromJson(responseBody, PlayResponse::class.java)
                                if (playResponse.msg == 1) {
                                    println("MusicViewModel: 开始缓存音乐")
                                    val audioUrl = playResponse.url
                                    val cacheUri = musicCache.cacheMusic(song.url, audioUrl)
                                    println("MusicViewModel: 音乐缓存完成")
                                    
                                    // 更新缓存大小
                                    updateCacheSize()
                                    
                                    // 复制到下载目录
                                    println("MusicViewModel: 复制到下载目录")
                                    File(URI(cacheUri)).copyTo(targetFile, overwrite = true)
                                }
                            }
                        }
                    }
                    
                    // 确保文件已经复制成功
                    if (targetFile.exists() && targetFile.length() > 0) {
                        println("MusicViewModel: 下载完成 path=${targetFile.absolutePath}")
                        // 通知系统媒体库更新
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(targetFile.absolutePath),
                            arrayOf("audio/mpeg"),
                            null
                        )
                        
                        // 更新下载状态
                        _downloadStatus.update { 
                            it + (song.url to DownloadStatus.Success(
                                path = targetFile.absolutePath,
                                isCached = true
                            ))
                        }
                        
                        _downloadTip.value = DownloadTip(
                            message = "下载完成",
                            path = targetFile.absolutePath
                        )
                    } else {
                        throw Exception("文复制失")
                    }
                }
            } catch (e: Exception) {
                println("MusicViewModel: 下载败 error=${e.message}")
                e.printStackTrace()
                _downloadStatus.update { it + (song.url to DownloadStatus.Error(e.message ?: "下载失败")) }
                _downloadTip.value = DownloadTip(
                    message = "下载败: ${e.message ?: "未知错误"}"
                )
            }
        }
    }

    // 清除提示
    fun clearDownloadTip() {
        _downloadTip.value = null
    }

    // 修改 checkSongStatus 方法
    fun checkSongStatus(song: Song): DownloadStatus {
        // 检查音乐文件
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val sanitizedTitle = sanitizeFileName(song.title)
        val downloadedFile = File(downloadDir, "$sanitizedTitle.mp3")
        
        // 检查 MV 文件
        val mvDownloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val downloadedMV = File(mvDownloadDir, "$sanitizedTitle.mp4")
        
        // 检查缓存状态
        val isCached = musicCache.isCached(song.url, MusicCache.CacheType.MUSIC)
        val isMVCached = song.hasMV && musicCache.isCached(
            song.url.substringAfterLast("/").substringBefore(".html"),
            MusicCache.CacheType.MV
        )
        
        // 检查是否正在下载
        val isDownloading = _downloadStatus.value[song.url] is DownloadStatus.Downloading
        
        return when {
            isDownloading -> DownloadStatus.Downloading
            else -> DownloadStatus.Success(
                path = if (downloadedFile.exists()) downloadedFile.absolutePath 
                       else if (isCached) musicCache.getCacheFileUri(song.url, MusicCache.CacheType.MUSIC)
                       else "",
                isCached = isCached,
                hasMVCached = isMVCached,
                hasMVDownloaded = downloadedMV.exists()
            )
        }
    }

    // 修改加载榜单数据的方法
    fun loadRankSongs(type: RankType, page: Int = 1, isRefresh: Boolean = false) {
        // 如果是刷新，忽略已加载状态
        // 如果不是刷新且已加载过，则跳过
        if (!isRefresh && page == 1) {
            when (type) {
                RankType.NEW -> {
                    if (hasLoadedNewRank && _newRankSongs.value.isNotEmpty()) {
                        println("MusicViewModel: 新歌榜已加载，跳过")
                        return
                    }
                }
                RankType.TOP -> {
                    if (hasLoadedTopRank && _topRankSongs.value.isNotEmpty()) {
                        println("MusicViewModel: TOP榜已加载，跳过")
                        return
                    }
                }
                RankType.DJ_DANCE -> {
                    if (hasLoadedDjDance && _djDanceSongs.value.isNotEmpty()) {
                        println("MusicViewModel: DJ舞曲已加载，跳过")
                        return
                    }
                }
            }
        }

        // 根据类型选择对应的状态
        when (type) {
            RankType.NEW -> {
                if (isNewRankLoading) return
                isNewRankLoading = true
                
                if (page == 1) {
                    _newRankSongs.value = emptyList()
                    _newRankReachedEnd.value = false
                }
                
                loadRankSongsInternal(
                    type = type,
                    page = page,
                    songs = _newRankSongs,
                    loadingMore = _newRankLoadingMore,
                    reachedEnd = _newRankReachedEnd,
                    currentPage = newRankCurrentPage,
                    setCurrentPage = { newRankCurrentPage = it },
                    setLoading = { isNewRankLoading = it },
                    onSuccess = { 
                        hasLoadedNewRank = true
                        if (isRefresh) {
                            _downloadTip.value = DownloadTip(message = "新歌榜刷新成功")
                        }
                    }
                )
            }
            RankType.TOP -> {
                if (isTopRankLoading) return
                isTopRankLoading = true
                
                if (page == 1) {
                    _topRankSongs.value = emptyList()
                    _topRankReachedEnd.value = false
                }
                
                loadRankSongsInternal(
                    type = type,
                    page = page,
                    songs = _topRankSongs,
                    loadingMore = _topRankLoadingMore,
                    reachedEnd = _topRankReachedEnd,
                    currentPage = topRankCurrentPage,
                    setCurrentPage = { topRankCurrentPage = it },
                    setLoading = { isTopRankLoading = it },
                    onSuccess = { 
                        hasLoadedTopRank = true
                        if (isRefresh) {
                            _downloadTip.value = DownloadTip(message = "TOP榜刷新成功")
                        }
                    }
                )
            }
            RankType.DJ_DANCE -> {
                if (isDjDanceLoading) return
                isDjDanceLoading = true
                
                if (page == 1) {
                    _djDanceSongs.value = emptyList()
                    _djDanceReachedEnd.value = false
                }
                
                loadRankSongsInternal(
                    type = type,
                    page = page,
                    songs = _djDanceSongs,
                    loadingMore = _djDanceLoadingMore,
                    reachedEnd = _djDanceReachedEnd,
                    currentPage = djDanceCurrentPage,
                    setCurrentPage = { djDanceCurrentPage = it },
                    setLoading = { isDjDanceLoading = it },
                    onSuccess = { 
                        hasLoadedDjDance = true
                        if (isRefresh) {
                            _downloadTip.value = DownloadTip(message = "DJ舞曲刷新成功")
                        }
                    }
                )
            }
        }
    }

    // 修内部加载方法，添加成功回调
    private fun loadRankSongsInternal(
        type: RankType,
        page: Int,
        songs: MutableStateFlow<List<ObservableSong>>,
        loadingMore: MutableStateFlow<Boolean>,
        reachedEnd: MutableStateFlow<Boolean>,
        currentPage: Int,
        setCurrentPage: (Int) -> Unit,
        setLoading: (Boolean) -> Unit,
        onSuccess: () -> Unit  // 添加成功回调
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val url = when (type) {
                        RankType.NEW -> {
                            if (page == 1) "$BASE_URL/list/new.html"
                            else "$BASE_URL/list/new/$page.html"
                        }
                        RankType.TOP -> {
                            if (page == 1) "$BASE_URL/list/top.html"
                            else "$BASE_URL/list/top/$page.html"
                        }
                        RankType.DJ_DANCE -> {
                            if (page == 1) "$BASE_URL/list/djwuqu.html"
                            else "$BASE_URL/list/djwuqu/$page.html"
                        }
                    }
                    
                    println("MusicViewModel: 加载榜单 type=$type, page=$page, url=$url")
                    
                    val document = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                        .get()
                    
                    val songElements = document.select(".play_list ul li")
                    
                    // 如果没有找到音乐列表，说明已经到底了
                    if (songElements.isEmpty()) {
                        println("MusicViewModel: 未找到音乐列表，已到底")
                        reachedEnd.value = true
                        return@withContext
                    }
                    
                    // 解析歌曲列表时检查收藏状态
                    val parsedSongs = songElements.mapNotNull { element ->
                        val nameElement = element.selectFirst(".name a") ?: return@mapNotNull null
                        val title = nameElement.text()
                            .replace("&nbsp;", " ")
                            .replace("[MP3_LRC]", "")
                            .trim()
                        val songPath = nameElement.attr("href")
                        val songUrl = when {
                            songPath.startsWith("http") -> songPath
                            songPath.startsWith("/") -> "$BASE_URL$songPath"
                            else -> "$BASE_URL/$songPath"
                        }
                        
                        // 查是否有 MV
                        val mvElement = element.selectFirst(".mv a")
                        val hasMV = mvElement != null
                        val mvUrl = if (hasMV) {
                            val mvPath = mvElement?.attr("href") ?: ""
                            when {
                                mvPath.startsWith("http") -> mvPath
                                mvPath.startsWith("/") -> "$BASE_URL$mvPath"
                                else -> "$BASE_URL/$mvPath"
                            }
                        } else ""
                        
                        if (title.isNotEmpty() && songUrl.isNotEmpty()) {
                            // 查是否已收藏
                            val isFavorite = _favorites.value.any { it.song.url == songUrl }
                            ObservableSong(Song(title, songUrl, isFavorite = isFavorite, hasMV = hasMV, mvUrl = mvUrl))
                        } else null
                    }
                    
                    // 如果解析出的歌曲为空，也认为是到底了
                    if (parsedSongs.isEmpty()) {
                        println("MusicViewModel: 解析结果为空，已到底")
                        reachedEnd.value = true
                        return@withContext
                    }
                    
                    // 更新对应榜单的数据
                    if (songs.value.isEmpty()) {
                        songs.value = parsedSongs
                    } else {
                        songs.value = songs.value + parsedSongs
                    }

                    // 更新页码
                    setCurrentPage(page)
                }
                
                // 加载成功后调用回调
                onSuccess()
                
            } catch (e: Exception) {
                e.printStackTrace()
                _searchError.value = when {
                    e.message?.contains("Unable to resolve host") == true -> "网络连接失败，请检查网络设置"
                    e.message?.contains("timeout") == true -> "网络连接超时，请稍后重试"
                    e.message != null -> "加载失败: ${e.message}"
                    else -> "网络连接失败，请检查网络设置"
                }
                // 出错时也标记为到底，防止继续请求
                reachedEnd.value = true
            } finally {
                setLoading(false)
                loadingMore.value = false
            }
        }
    }

    // 修改加载更多的方法
    fun loadMoreRank(type: RankType) {
        when (type) {
            RankType.NEW -> {
                if (!isNewRankLoading && !_newRankReachedEnd.value) {
                    _newRankLoadingMore.value = true
                    loadRankSongs(type, newRankCurrentPage + 1)
                }
            }
            RankType.TOP -> {
                if (!isTopRankLoading && !_topRankReachedEnd.value) {
                    _topRankLoadingMore.value = true
                    loadRankSongs(type, topRankCurrentPage + 1)
                }
            }
            RankType.DJ_DANCE -> {
                if (!isDjDanceLoading && !_djDanceReachedEnd.value) {
                    _djDanceLoadingMore.value = true
                    loadRankSongs(type, djDanceCurrentPage + 1)
                }
            }
        }
    }

    // 处理播放结束
    private fun handlePlaybackEnd() {
        if (isHandlingPlaybackEnd) {
            println("MusicViewModel: 正在处理播放结束，跳过重复触发")
            return
        }
        
        isHandlingPlaybackEnd = true
        println("MusicViewModel: 处理播放结束")
        
        viewModelScope.launch(Dispatchers.Main) {
            try {
                when {
                    playerConfig.repeatMode == ExoPlayer.REPEAT_MODE_ONE -> {
                        println("MusicViewModel: 单曲循环模式，重新播放")
                        // 保持进度显示在结束位置
                        _currentPosition.value = exoPlayer?.duration ?: 0
                        exoPlayer?.seekTo(0)
                        exoPlayer?.play()
                    }
                    playerConfig.repeatMode == ExoPlayer.REPEAT_MODE_ALL -> {
                        println("MusicViewModel: 列表循环/随机播放模式，播放下一曲")
                        // 直接切换下一曲，不更新当前进度
                        playNext()
                    }
                }
                
                // 延迟重置标志位，确保新的播放已经开始
                delay(500)
            } finally {
                isHandlingPlaybackEnd = false
            }
        }
    }

    // 播放 MV
    fun playMV(song: Song) {
        if (!song.hasMV) return
        
        viewModelScope.launch {
            try {
                // 如有音乐在播放，先暂停
                if (exoPlayer?.isPlaying == true) {
                    println("MusicViewModel: 暂停音乐播放")
                    exoPlayer?.pause()
                }
                
                _mvCaching.value = true
                val songId = song.url.substringAfterLast("/").substringBefore(".html")
                val sanitizedTitle = sanitizeFileName(song.title)
                
                // 检查是否已下载
                val downloadedFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "$sanitizedTitle.mp4")
                if (downloadedFile.exists()) {
                    println("MusicViewModel: 使用已下载的 MV")
                    val mvUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        downloadedFile
                    )
                    
                    // 使用系统播放器播放
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(mvUri, "video/mp4")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    
                    // 显示提示
                    _downloadTip.value = DownloadTip(
                        message = "正在播放已下载的 MV"
                    )
                    
                    // 启动播放器
                    context.startActivity(intent)
                    return@launch
                }
                
                // 检查是否已缓存
                val cacheFile = File(context.cacheDir, "mv/$songId.mp4")
                val mvUri = if (cacheFile.exists()) {
                    println("MusicViewModel: 使用缓存的 MV")
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        cacheFile
                    )
                } else {
                    // 显示开始缓存提示
                    _downloadTip.value = DownloadTip(
                        message = "正在准备 MV，由于文件较大，可能需要一些时间，请耐心等待..."
                    )
                    
                    println("MusicViewModel: 获取 MV 地址")
                    val mvUrl = musicCache.getMVUrl(songId)
                    println("MusicViewModel: 开始缓存 MV")
                    val cacheUri = musicCache.cacheMV(songId, mvUrl)
                    
                    // 更新缓存大小
                    updateCacheSize()
                    
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        File(URI(cacheUri))
                    )
                }
                
                // 使用系统播放器播放
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(mvUri, "video/mp4")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                // 显示缓存完成提示
                _downloadTip.value = DownloadTip(
                    message = "MV 已准备就绪"
                )
                
                // 启动播放器
                context.startActivity(intent)
            } catch (e: Exception) {
                println("MusicViewModel: MV 播放失败: ${e.message}")
                e.printStackTrace()
                _downloadTip.value = DownloadTip(
                    message = "MV 播放失败: ${e.message}"
                )
            } finally {
                _mvCaching.value = false
            }
        }
    }

    // 下载 MV
    fun downloadMV(song: Song) {
        if (!song.hasMV) return
        
        viewModelScope.launch {
            try {
                println("MusicViewModel: 开始下载 MV ${song.title}")
                _downloadStatus.update { it + (song.url to DownloadStatus.Downloading) }
                
                // 显示开始下载提示
                _downloadTip.value = DownloadTip(
                    message = "开始下载 MV，由于文件较大，可能需要一些时间，请耐心等待..."
                )
                
                withContext(Dispatchers.IO) {
                    val songId = song.url.substringAfterLast("/").substringBefore(".html")
                    
                    // 获取下载目录
                    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                    downloadDir.mkdirs()
                    
                    // 准备目标文件名
                    val sanitizedTitle = sanitizeFileName(song.title)
                    val targetFile = File(downloadDir, "$sanitizedTitle.mp4")
                    
                    // 检查是否已缓存
                    val cacheFile = File(context.cacheDir, "mv/$songId.mp4")
                    if (cacheFile.exists()) {
                        println("MusicViewModel: 使用缓存的 MV")
                        // 复制到下载目录
                        println("MusicViewModel: 复制到下载目录")
                        cacheFile.copyTo(targetFile, overwrite = true)
                    } else {
                        println("MusicViewModel: 从服务器下载")
                        // 获取 MV 真实地址并下载
                        val mvUrl = musicCache.getMVUrl(songId)
                        println("MusicViewModel: 获��到 MV 地址: $mvUrl")
                        
                        // 缓存 MV
                        val cacheUri = musicCache.cacheMV(songId, mvUrl)
                        println("MusicViewModel: MV 缓存完成")
                        
                        // 更新缓存大小
                        updateCacheSize()
                        
                        // 复制到下载目录
                        println("MusicViewModel: 复制到下载目录")
                        File(URI(cacheUri)).copyTo(targetFile, overwrite = true)
                    }
                    
                    // 确保文件已经复制成功
                    if (targetFile.exists() && targetFile.length() > 0) {
                        println("MusicViewModel: MV 下载完成 path=${targetFile.absolutePath}")
                        // 通知系统媒体库更新
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(targetFile.absolutePath),
                            arrayOf("video/mp4"),
                            null
                        )
                        
                        // 更新下载状态
                        _downloadStatus.update { 
                            it + (song.url to DownloadStatus.Success(
                                path = targetFile.absolutePath,
                                isCached = true,
                                hasMVCached = true,
                                hasMVDownloaded = true
                            ))
                        }
                        
                        // 更新缓存大小
                        updateCacheSize()
                        
                        _downloadTip.value = DownloadTip(
                            message = "MV 下载完成",
                            path = targetFile.absolutePath
                        )
                    } else {
                        throw Exception("MV 文件复制失败")
                    }
                }
            } catch (e: Exception) {
                println("MusicViewModel: MV 下载失败 error=${e.message}")
                e.printStackTrace()
                _downloadStatus.update { it + (song.url to DownloadStatus.Error(e.message ?: "下载失败")) }
                _downloadTip.value = DownloadTip(
                    message = "MV 下载失败: ${e.message ?: "未知错误"}"
                )
            }
        }
    }

    // 修改 updateNotification 方法
    private fun updateNotification() {
        // 更新前台服务的通知
        val serviceIntent = Intent(context, MusicService::class.java).apply {
            putExtra(MusicService.EXTRA_PLAYER_DATA, _currentPlayerData.value)
            // 使用 StateFlow 中的播放状态
            putExtra("isPlaying", _isPlaying.value)
            putExtra("isFavorite", currentPlayingSong?.let { song ->
                _favorites.value.any { it.song.url == song.url }
            } ?: false)
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            println("MusicViewModel: 更新通知失败: ${e.message}")
            e.printStackTrace()
        }
    }

    // 切换重复模式
    fun toggleRepeatMode() {
        println("MusicViewModel: 当前播放模式: repeatMode=${_repeatMode.value}, shuffle=${_shuffleEnabled.value}")
        
        val (newMode, newShuffle) = when {
            _repeatMode.value == ExoPlayer.REPEAT_MODE_ALL && !_shuffleEnabled.value -> {
                println("MusicViewModel: 切换到随机播放模式")
                ExoPlayer.REPEAT_MODE_ALL to true
            }
            _repeatMode.value == ExoPlayer.REPEAT_MODE_ALL && _shuffleEnabled.value -> {
                println("MusicViewModel: 切换到单曲循环模式")
                ExoPlayer.REPEAT_MODE_ONE to false
            }
            _repeatMode.value == ExoPlayer.REPEAT_MODE_ONE -> {
                println("MusicViewModel: 切换到列表循环模式")
                ExoPlayer.REPEAT_MODE_ALL to false
            }
            else -> {
                println("MusicViewModel: 切换到默认的列表循环模式")
                ExoPlayer.REPEAT_MODE_ALL to false
            }
        }
        
        // 更新配置
        playerConfig = PlayerConfig(newMode, newShuffle)
        
        // 更新状态
        _repeatMode.value = newMode
        _shuffleEnabled.value = newShuffle
        
        // 更新播放器状态
        exoPlayer?.let { player ->
            player.repeatMode = newMode
            player.shuffleModeEnabled = newShuffle
        }
        
        // 记录日志
        val modeText = when {
            newMode == ExoPlayer.REPEAT_MODE_ALL && !newShuffle -> "列表循环"
            newMode == ExoPlayer.REPEAT_MODE_ALL && newShuffle -> "随机播放"
            newMode == ExoPlayer.REPEAT_MODE_ONE -> "单曲循环"
            else -> "未知模式"
        }
        println("MusicViewModel: 切换播放模式为: $modeText")
    }

    // 添加检查是否有下一曲的方法
    fun hasNextSong(): Boolean {
        return exoPlayer?.hasNextMediaItem() ?: false
    }

    // 公开的初始化方法
    fun initializePlayer(audioUrl: String) {
        setupPlayer(audioUrl)
    }

    // 添加开始更新进度的方法
    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    _currentPosition.value = player.currentPosition
                    
                    // 检查是否播放结束
                    if (player.currentPosition >= player.duration && player.duration > 0) {
                        println("MusicViewModel: 检测到播放结束")
                        handlePlaybackEnd()
                        progressJob?.cancel()  // 取消当前任务
                        return@launch  // 使用 return@launch 替代 break
                    }
                }
                delay(200)  // 更频繁地检查进度
            }
        }
    }

    // 添加停止更新进度的方法
    private fun stopProgressUpdate() {
        progressJob?.cancel()
        progressJob = null
    }

    // 添加播放进度监听器
    private fun startPositionUpdate() {
        positionUpdateJob?.cancel()
        positionUpdateJob = viewModelScope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    val position = player.currentPosition
                    val duration = player.duration
                    
                    // 只在正常播放时更新进度
                    if (!isHandlingPlaybackEnd) {
                        _currentPosition.value = position
                    }

                    // 检查是否播放到末尾，提前200ms触发切换
                    if (!isHandlingPlaybackEnd && duration > 0 && position >= duration - 200) {
                        println("MusicViewModel: 检测到播放接近结束: position=$position, duration=$duration")
                        // 保持当前进度显示
                        _currentPosition.value = duration
                        handlePlaybackEnd()
                        positionUpdateJob?.cancel()
                        return@launch
                    }
                }
                delay(100)
            }
        }
    }

    // 添加停止播放进度监听器的方法
    private fun stopPositionUpdate() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }
}