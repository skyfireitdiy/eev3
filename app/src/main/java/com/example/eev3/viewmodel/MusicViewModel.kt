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

    // 修改播放列表获取逻辑
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

    // 添加榜单相关状态
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

    // 每个榜单的加载状态
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

    init {
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
            }
        })

        // 初始化缓存大小
        updateCacheSize()

        // 注册耳机插拔监听器
        registerHeadsetReceiver()
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

    fun toggleFavorite(observableSong: ObservableSong) {
        observableSong.isFavorite = !observableSong.isFavorite
        
        val currentFavorites = _favorites.value.toMutableList()
        if (observableSong.isFavorite) {
            if (!currentFavorites.contains(observableSong)) {
                currentFavorites.add(observableSong)
            }
        } else {
            currentFavorites.remove(observableSong)
        }
        _favorites.value = currentFavorites
        
        // 保存更新后的藏列表
        viewModelScope.launch {
            favoritesDataStore.saveFavorites(currentFavorites.map { it.song })
        }
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
        
        // 如果是同一首歌，直接返回
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
                    
                    // 检查是否已下载
                    val sanitizedTitle = sanitizeFileName(song.title)
                    val downloadedFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "$sanitizedTitle.mp3")
                    
                    if (downloadedFile.exists()) {
                        println("MusicViewModel: 使用已下载的文件")
                        // 如果已下载，直接使用下载的文件
                        val downloadedUri = downloadedFile.toURI().toString()
                        
                        // 获取并缓存歌词和封面
                        val lrcUrl = "$BASE_URL/plug/down.php?ac=music&lk=lrc&id=$songId"
                        val lrcRequest = Request.Builder()
                            .url(lrcUrl)
                            .build()
                        
                        var lyricsUri: String? = null
                        client.newCall(lrcRequest).execute().use { lrcResponse ->
                            val lrcContent = lrcResponse.body?.string() ?: ""
                            println("MusicViewModel: 开始缓存歌词")
                            lyricsUri = musicCache.cacheLyrics(song.url, lrcContent)
                            println("MusicViewModel: 歌词缓存完成 lyricsUri=$lyricsUri")
                            _lyrics.value = parseLyrics(lrcContent)
                            println("MusicViewModel: 歌词解析完成，行数=${_lyrics.value.size}")
                        }
                        
                        // 获取封面
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
                                    println("MusicViewModel: 开始缓存封面")
                                    val coverUri = musicCache.cacheCover(song.url, playResponse.pic)
                                    println("MusicViewModel: 封面缓存完成 coverUri=$coverUri")
                                    
                                    // 更新播放器数据
                                    _currentPlayerData.value = PlayerData(
                                        coverImage = coverUri,
                                        title = playResponse.title.replace("[Mp3_Lrc]", "").trim(),
                                        audioUrl = downloadedUri
                                    )
                                    
                                    // 立即启动服务
                                    withContext(Dispatchers.Main) {
                                        println("MusicViewModel: 更新播放数据后启动服务")
                                        initializePlayer(context, downloadedUri)
                                    }
                                }
                            }
                        }
                    } else {
                        // 如果未下载，走原有缓存和下载逻辑
                        println("MusicViewModel: 从服务器获取数据")
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
                                    val finalUrl = musicCache.cacheMusic(song.url, audioUrl)
                                    println("MusicViewModel: 音乐缓存完成 finalUrl=$finalUrl")
                                    
                                    // 更新缓存大小显示
                                    updateCacheSize()
                                    
                                    println("MusicViewModel: 开始缓存封面")
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
                                        audioUrl = finalUrl
                                    )
                                    
                                    // 立即启动服务
                                    withContext(Dispatchers.Main) {
                                        println("MusicViewModel: 更新播放数据后启动服务")
                                        initializePlayer(context, finalUrl)
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
    
    fun initializePlayer(context: Context, audioUrl: String) {
        println("MusicViewModel: 初始化播放器 audioUrl=$audioUrl")
        
        // 如是同一个URL只需要更新UI状态
        if (audioUrl == currentAudioUrl && exoPlayer != null) {
            println("MusicViewModel: 相同URL，只更新UI状态")
            // 更新进度和时长
            exoPlayer?.let { player ->
                _currentPosition.value = player.currentPosition
                _duration.value = player.duration
            }
            return
        }
        
        currentAudioUrl = audioUrl
        
        // 初始化播放器
        exoPlayer?.release()
        exoPlayer = ExoPlayer.Builder(context).build()
        
        exoPlayer?.let { player ->
            player.setMediaItem(MediaItem.fromUri(audioUrl))
            player.prepare()
            
            val playerListener = object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        _duration.value = player.duration
                        player.play()  // 准备就绪后开始播放
                    } else if (state == Player.STATE_ENDED) {
                        handlePlaybackEnd()
                    }
                }
                
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }
            }
            
            player.addListener(playerListener)
        }
        
        // 启动进度更新
        viewModelScope.launch {
            while (true) {
                delay(100)
                exoPlayer?.let {
                    if (it.isPlaying) {
                        val position = it.currentPosition
                        _currentPosition.value = position
                        updateCurrentLyric(position)
                    }
                }
            }
        }
        
        // 启动前台服前先检查 PlayerData
        val playerData = _currentPlayerData.value
        if (playerData == null) {
            println("MusicViewModel: 当前没有播放数据，不启动服务")
            return
        }
        
        // 启动前台服务
        val serviceIntent = Intent(context, MusicService::class.java).apply {
            println("MusicViewModel: 准备启动服务，当前播放数据:")
            println("- 标题: ${playerData.title}")
            println("- 音频URL: ${playerData.audioUrl}")
            println("- 封面: ${playerData.coverImage}")
            putExtra(MusicService.EXTRA_PLAYER_DATA, playerData)
        }
        
        println("MusicViewModel: 开始启动服务")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                println("MusicViewModel: 使用 startForegroundService")
                context.startForegroundService(serviceIntent)
            } else {
                println("MusicViewModel: 使用 startService")
                context.startService(serviceIntent)
            }
            println("MusicViewModel: 服务启动成功")
        } catch (e: Exception) {
            println("MusicViewModel: 启动服务失败: ${e.message}")
            e.printStackTrace()
        }
    }
    
    fun playPause() {
        exoPlayer?.let {
            if (it.isPlaying) {
                it.pause()
            } else {
                it.play()
            }
        }
    }
    
    fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
    }
    
    fun setVolume(volume: Float) {
        exoPlayer?.volume = volume
    }
    
    fun getVolume(): Float = exoPlayer?.volume ?: 1f

    override fun onCleared() {
        super.onCleared()
        // 停止
        context.stopService(Intent(context, MusicService::class.java))
        
        // 取消注册广播接收器
        headsetReceiver?.let {
            context.unregisterReceiver(it)
        }
        headsetReceiver = null
        
        exoPlayer?.release()
        exoPlayer = null
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

    fun playNext() {
        val playlist = currentPlaylist
        if (playlist.isEmpty()) return
        
        when (_playMode.value) {
            PlayMode.SEQUENCE -> {
                // 顺序播放，播放下一首
                val nextIndex = (currentPlayingIndex + 1) % playlist.size
                loadPlayerData(playlist[nextIndex], currentPlaylistSource)
            }
            PlayMode.SINGLE_LOOP -> {
                // 单曲循环，重新播放当前歌曲
                currentPlayingSong?.let { loadPlayerData(it, currentPlaylistSource) }
            }
            PlayMode.RANDOM -> {
                // 随机播放，随机选择一首
                val nextIndex = (0 until playlist.size).random()
                loadPlayerData(playlist[nextIndex], currentPlaylistSource)
            }
            PlayMode.ONCE -> {
                // 单次播放，不做任何操作
            }
        }
    }

    fun playPrevious() {
        val playlist = currentPlaylist
        if (playlist.isEmpty()) return
        
        when (_playMode.value) {
            PlayMode.SEQUENCE -> {
                // 顺序播放，播放上一首
                val previousIndex = if (currentPlayingIndex > 0) {
                    currentPlayingIndex - 1
                } else {
                    playlist.size - 1
                }
                loadPlayerData(playlist[previousIndex], currentPlaylistSource)
            }
            PlayMode.SINGLE_LOOP -> {
                // 单曲循环，重新播放当前歌曲
                currentPlayingSong?.let { loadPlayerData(it, currentPlaylistSource) }
            }
            PlayMode.RANDOM -> {
                // 随机播放，随机选择一首
                val nextIndex = (0 until playlist.size).random()
                loadPlayerData(playlist[nextIndex], currentPlaylistSource)
            }
            PlayMode.ONCE -> {
                // 单次播放模式下也允许切换到上一首
                val previousIndex = if (currentPlayingIndex > 0) {
                    currentPlayingIndex - 1
                } else {
                    playlist.size - 1
                }
                loadPlayerData(playlist[previousIndex], currentPlaylistSource)
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
            // 界面显示时，如果需要则恢复播放
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
                // 获取当前播放歌曲的URL
                val currentSongUrl = currentPlayingSong?.url
                
                if (currentSongUrl != null) {
                    println("MusicViewModel: 清除缓存时保护当前播放歌曲: ${currentPlayingSong?.title}")
                }
                
                // 清除缓存时跳过当前播放的音乐
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
                        println("MusicViewModel: 复制到下��目录")
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
                        throw Exception("文件复制失败")
                    }
                }
            } catch (e: Exception) {
                println("MusicViewModel: 下载失败 error=${e.message}")
                e.printStackTrace()
                _downloadStatus.update { it + (song.url to DownloadStatus.Error(e.message ?: "下载失败")) }
                _downloadTip.value = DownloadTip(
                    message = "下载失败: ${e.message ?: "未知错误"}"
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
    fun loadRankSongs(type: RankType, page: Int = 1) {
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
                    setLoading = { isNewRankLoading = it }
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
                    setLoading = { isTopRankLoading = it }
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
                    setLoading = { isDjDanceLoading = it }
                )
            }
        }
    }

    // 添加内部加载方法
    private fun loadRankSongsInternal(
        type: RankType,
        page: Int,
        songs: MutableStateFlow<List<ObservableSong>>,
        loadingMore: MutableStateFlow<Boolean>,
        reachedEnd: MutableStateFlow<Boolean>,
        currentPage: Int,
        setCurrentPage: (Int) -> Unit,
        setLoading: (Boolean) -> Unit
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

    // 修改播放束时的处理
    private fun handlePlaybackEnd() {
        println("MusicViewModel: 播放结束，切换到下一首")
        val playlist = currentPlaylist
        if (playlist.isEmpty()) {
            println("MusicViewModel: 播放列表为空，停止播放")
            return
        }
        
        println("MusicViewModel: 播放列表大小=${playlist.size}, 当前索引=$currentPlayingIndex")
        
        when (_playMode.value) {
            PlayMode.SEQUENCE -> {
                // 顺序播放，播放下一首
                if (currentPlayingIndex >= 0 && currentPlayingIndex < playlist.size) {
                    val nextIndex = if (currentPlayingIndex + 1 >= playlist.size) 0 
                                  else currentPlayingIndex + 1
                    println("MusicViewModel: 顺序播放下一首，当前索引=$currentPlayingIndex, 下一首索引=$nextIndex")
                    loadPlayerData(playlist[nextIndex], currentPlaylistSource)
                } else {
                    println("MusicViewModel: 当前索引无效: $currentPlayingIndex")
                }
            }
            PlayMode.SINGLE_LOOP -> {
                println("MusicViewModel: 单曲循环，重新播放")
                exoPlayer?.seekTo(0)
                exoPlayer?.play()
            }
            PlayMode.RANDOM -> {
                // 随机播放，随机选择一首
                val nextIndex = (0 until playlist.size).random()
                println("MusicViewModel: 随机播放，选择索引=$nextIndex")
                loadPlayerData(playlist[nextIndex], currentPlaylistSource)
            }
            PlayMode.ONCE -> {
                println("MusicViewModel: 单次播放模式，播放束")
            }
        }
    }

    // 播放 MV
    fun playMV(song: Song) {
        if (!song.hasMV) return
        
        viewModelScope.launch {
            try {
                // 如���有音乐在播放，先暂停
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
                        println("MusicViewModel: 获取到 MV 地址: $mvUrl")
                        
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
}