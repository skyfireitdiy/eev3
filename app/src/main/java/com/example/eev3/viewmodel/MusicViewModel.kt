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

    // 添加播放列表相关的状态
    private var currentPlayingIndex = -1
    private val playList: List<Song>
        get() = _favorites.value.map { it.song }

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
                    // 耳机断开时暂停播放
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

    fun searchSongs(query: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
                    val url = "$BASE_URL/so/$encodedQuery.html"
                    
                    val document = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                        .get()
                    
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
                        if (title.isNotEmpty() && songUrl.isNotEmpty()) {
                            ObservableSong(Song(title, songUrl))
                        } else null
                    }
                    
                    _searchResults.value = songs
                    
                    if (songs.isEmpty()) {
                        _searchError.value = "未找到相关歌曲"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _searchError.value = when {
                    e.message?.contains("Unable to resolve host") == true -> "网络连接失败，请检查网络置"
                    e.message?.contains("timeout") == true -> "网络连接超时，请稍后重试"
                    e.message != null -> "搜索出错: ${e.message}"
                    else -> "网络连接失败，请检查网络设置"
                }
            }
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
        
        // 保存更新后的收藏列表
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

    fun loadPlayerData(song: Song) {
        println("MusicViewModel: 开始加载歌曲 title=${song.title}")
        
        // 更新当前播放和状态
        currentPlayingIndex = playList.indexOfFirst { it.url == song.url }
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
                    
                    // 检查缓存状态
                    val hasCachedMusic = musicCache.isCached(song.url, MusicCache.CacheType.MUSIC)
                    val hasCachedLyrics = musicCache.isCached(song.url, MusicCache.CacheType.LYRICS)
                    val hasCachedCover = musicCache.isCached(song.url, MusicCache.CacheType.COVER)
                    
                    println("MusicViewModel: 缓存状态检查:")
                    println("- 音乐缓存: ${if (hasCachedMusic) "已缓存" else "未缓存"}")
                    println("- 歌词缓存: ${if (hasCachedLyrics) "已缓存" else "未缓存"}")
                    println("- 封面缓存: ${if (hasCachedCover) "已缓存" else "未缓存"}")

                    if (hasCachedMusic && hasCachedLyrics && hasCachedCover) {
                        println("MusicViewModel: 使用缓存的文件")
                        // 如果所有内容都已缓存，直接使用缓存
                        val musicUri = musicCache.getCacheFileUri(song.url, MusicCache.CacheType.MUSIC)
                        val coverUri = musicCache.getCacheFileUri(song.url, MusicCache.CacheType.COVER)
                        println("MusicViewModel: 缓存音乐URI=$musicUri")
                        println("MusicViewModel: 缓存封面URI=$coverUri")
                        
                        _currentPlayerData.value = PlayerData(
                            coverImage = coverUri,
                            title = song.title,
                            audioUrl = musicUri
                        )
                        
                        // 加载缓存的歌词
                        val lyricsUri = musicCache.getCacheFileUri(song.url, MusicCache.CacheType.LYRICS)
                        println("MusicViewModel: 缓存歌词URI=$lyricsUri")
                        val lyricsFile = File(URI(lyricsUri))
                        val lyricsContent = lyricsFile.readText()
                        _lyrics.value = parseLyrics(lyricsContent)
                        println("MusicViewModel: 已加载缓存的歌词，行数=${_lyrics.value.size}")
                        
                        // 初始化播放器
                        withContext(Dispatchers.Main) {
                            println("MusicViewModel: 使用缓存音乐初始化播放器")
                            initializePlayer(context, musicUri)
                        }
                    } else {
                        println("MusicViewModel: 从服务器获取数据")
                        // 如果没有完整缓存，从服务器获取数据
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
                                if (playResponse.msg == 1 && playResponse.url.isNotEmpty()) {
                                    println("MusicViewModel: 开始缓存音乐")
                                    // 缓存音乐
                                    val audioUrl = playResponse.url
                                    val finalUrl = musicCache.cacheMusic(song.url, audioUrl)
                                    println("MusicViewModel: 音乐缓存完成 finalUrl=$finalUrl")
                                    
                                    println("MusicViewModel: 开始缓存封面")
                                    // 缓存封面
                                    val coverUrl = playResponse.pic
                                    val finalCoverUrl = musicCache.cacheCover(song.url, coverUrl)
                                    println("MusicViewModel: 封面缓存完成 finalCoverUrl=$finalCoverUrl")
                                    
                                    println("MusicViewModel: 开始获取歌词")
                                    // 获取并缓存歌词
                                    val lrcUrl = "$BASE_URL/plug/down.php?ac=music&lk=lrc&id=$songId"
                                    val lrcRequest = Request.Builder()
                                        .url(lrcUrl)
                                        .build()
                                    
                                    var lyricsUri: String? = null
                                    client.newCall(lrcRequest).execute().use { lrcResponse ->
                                        val lrcContent = lrcResponse.body?.string() ?: ""
                                        println("MusicViewModel: 开始缓存歌词")
                                        // 缓存歌词
                                        lyricsUri = musicCache.cacheLyrics(song.url, lrcContent)
                                        println("MusicViewModel: 歌词缓存完成 lyricsUri=$lyricsUri")
                                        _lyrics.value = parseLyrics(lrcContent)
                                        println("MusicViewModel: 歌词解析完成，行数=${_lyrics.value.size}")
                                    }
                                    
                                    // 更新缓存大小显示
                                    println("MusicViewModel: 更新缓存大小显示")
                                    updateCacheSize()
                                    
                                    // 所有内容都缓存完成后，再更新播放器数据并开始播放
                                    println("MusicViewModel: 所有内容缓存完成，开始播放")
                                    _currentPlayerData.value = PlayerData(
                                        coverImage = finalCoverUrl,
                                        title = playResponse.title.replace("[Mp3_Lrc]", "").trim(),
                                        audioUrl = finalUrl
                                    )
                                    
                                    // 初始化播放器
                                    withContext(Dispatchers.Main) {
                                        println("MusicViewModel: 使用新下载的音乐初始化播放器")
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
    
    // 更新当前歌词位置
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
        
        // 如果是同一个URL，只需要更新UI状态
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
                        println("MusicViewModel: 播放结束，切换到下一首")
                        // 播放结束时，根据播放模式播放下一首
                        when (_playMode.value) {
                            PlayMode.SEQUENCE -> {
                                // 顺序播放，播放下一首
                                val nextIndex = (currentPlayingIndex + 1) % playList.size
                                loadPlayerData(playList[nextIndex])
                            }
                            PlayMode.SINGLE_LOOP -> {
                                // 单曲循环，重新播放当前歌曲
                                player.seekTo(0)
                                player.play()
                            }
                            PlayMode.RANDOM -> {
                                // 随机播放，随机选择一首
                                val nextIndex = (0 until playList.size).random()
                                loadPlayerData(playList[nextIndex])
                            }
                            PlayMode.ONCE -> {
                                // 单次播放，不做任何操作
                            }
                        }
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
        
        // 启动前台服务
        val serviceIntent = Intent(context, MusicService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
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
        // 停止服务
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
            PlayMode.RANDOM -> Player.REPEAT_MODE_ALL  // 随机模式下也设置为循环全部
            PlayMode.ONCE -> Player.REPEAT_MODE_OFF
        }
        
        // 设置随机播放
        exoPlayer?.shuffleModeEnabled = (_playMode.value == PlayMode.RANDOM)
    }

    fun playNext() {
        if (playList.isEmpty()) return
        
        when (_playMode.value) {
            PlayMode.SEQUENCE -> {
                // 顺序播放，播放下一首
                val nextIndex = (currentPlayingIndex + 1) % playList.size
                loadPlayerData(playList[nextIndex])
            }
            PlayMode.SINGLE_LOOP -> {
                // 单曲循环，重新播放当前歌曲
                currentPlayingSong?.let { loadPlayerData(it) }
            }
            PlayMode.RANDOM -> {
                // 随机播放，随机选择一首
                val nextIndex = (0 until playList.size).random()
                loadPlayerData(playList[nextIndex])
            }
            PlayMode.ONCE -> {
                // 单次播放，不做任何操作
            }
        }
    }

    fun playPrevious() {
        if (playList.isEmpty()) return
        
        when (_playMode.value) {
            PlayMode.SEQUENCE -> {
                // 顺序放，播放上一首
                val previousIndex = if (currentPlayingIndex > 0) {
                    currentPlayingIndex - 1
                } else {
                    playList.size - 1
                }
                loadPlayerData(playList[previousIndex])
            }
            PlayMode.SINGLE_LOOP -> {
                // 单曲循环，重新播放当前歌曲
                currentPlayingSong?.let { loadPlayerData(it) }
            }
            PlayMode.RANDOM -> {
                // 随机播放，随机选一首
                val nextIndex = (0 until playList.size).random()
                loadPlayerData(playList[nextIndex])
            }
            PlayMode.ONCE -> {
                // 单次播放模式下也允许切换到上一首
                val previousIndex = if (currentPlayingIndex > 0) {
                    currentPlayingIndex - 1
                } else {
                    playList.size - 1
                }
                loadPlayerData(playList[previousIndex])
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
            // 界面隐藏时，保存当前状态但不停止播放
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
            musicCache.clearCache()
            // 清除缓存后立即更新缓存大小显示
            updateCacheSize()
            dismissClearCacheDialog()
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
}