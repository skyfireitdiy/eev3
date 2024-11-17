package com.example.eev3.data

import android.content.Context
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

class MusicCache(context: Context) {
    private val cacheDir = File(context.cacheDir, "music")
    private val lyricsDir = File(context.cacheDir, "lyrics")
    private val coverDir = File(context.cacheDir, "covers")
    private val client = OkHttpClient()

    init {
        cacheDir.mkdirs()
        lyricsDir.mkdirs()
        coverDir.mkdirs()
    }

    // 从URL中提取歌曲ID
    private fun extractSongId(url: String): String {
        // Extract ID from the HTML page URL instead of MP3 URL
        return url.substringAfterLast("/")
            .substringBefore(".html")
    }
    
    // 获取缓存文件
    private fun getCacheFile(url: String, type: CacheType): File {
        val songId = extractSongId(url)
        val fileName = when (type) {
            CacheType.MUSIC -> "$songId.mp3"
            CacheType.LYRICS -> "$songId.lrc"
            CacheType.COVER -> "$songId.jpg"
        }
        val directory = when (type) {
            CacheType.MUSIC -> cacheDir
            CacheType.LYRICS -> lyricsDir
            CacheType.COVER -> coverDir
        }
        return File(directory, fileName)
    }
    
    // 检查是否已缓存
    fun isCached(url: String, type: CacheType): Boolean {
        val file = getCacheFile(url, type)
        val exists = file.exists()
        println("MusicCache: 检查缓存 type=$type, exists=$exists, file=${file.absolutePath}")
        return exists
    }
    
    // 获取缓存文件URI
    fun getCacheFileUri(url: String, type: CacheType): String {
        return getCacheFile(url, type).toURI().toString()
    }
    
    // 缓存音乐
    suspend fun cacheMusic(htmlUrl: String, audioUrl: String): String = withContext(Dispatchers.IO) {
        cacheFile(htmlUrl, audioUrl, CacheType.MUSIC)
    }
    
    // 缓存歌词
    suspend fun cacheLyrics(htmlUrl: String, lyricsContent: String): String = withContext(Dispatchers.IO) {
        val file = getCacheFile(htmlUrl, CacheType.LYRICS)
        file.writeText(lyricsContent)
        file.toURI().toString()
    }
    
    // 缓存封面
    suspend fun cacheCover(htmlUrl: String, coverUrl: String): String = withContext(Dispatchers.IO) {
        cacheFile(htmlUrl, coverUrl, CacheType.COVER)
    }
    
    // 通用文件缓存逻辑
    private suspend fun cacheFile(htmlUrl: String, contentUrl: String, type: CacheType): String = withContext(Dispatchers.IO) {
        if (contentUrl.startsWith("file:")) {
            println("MusicCache: 跳过缓存，已经是本地文件 url=$contentUrl")
            return@withContext contentUrl
        }
        
        val songId = extractSongId(htmlUrl)
        val cacheFile = getCacheFile(htmlUrl, type)
        println("MusicCache: 开始缓存 type=$type, songId=$songId")
        println("MusicCache: 缓存文件路径=${cacheFile.absolutePath}")
        
        if (cacheFile.exists()) {
            println("MusicCache: 文件已存在，直接返回缓存")
            return@withContext cacheFile.toURI().toString()
        }
        
        try {
            val request = Request.Builder()
                .url(contentUrl)
                .build()
            
            client.newCall(request).execute().use { response ->
                response.body?.let { body ->
                    println("MusicCache: 开始下载 type=$type, contentLength=${body.contentLength()}")
                    cacheFile.outputStream().use { output ->
                        body.byteStream().use { input ->
                            val bytesCopied = input.copyTo(output)
                            println("MusicCache: 下载完成 type=$type, bytesCopied=$bytesCopied")
                        }
                    }
                }
            }
            
            println("MusicCache: 缓存成功 type=$type, size=${cacheFile.length()}")
            cacheFile.toURI().toString()
        } catch (e: Exception) {
            println("MusicCache: 缓存失败 type=$type, error=${e.message}")
            e.printStackTrace()
            cacheFile.delete()
            contentUrl
        }
    }
    
    // 获取缓存大小
    suspend fun getCacheSize(): Long = withContext(Dispatchers.IO) {
        val musicSize = cacheDir.walkBottomUp().sumOf { it.length() }
        val lyricsSize = lyricsDir.walkBottomUp().sumOf { it.length() }
        val coverSize = coverDir.walkBottomUp().sumOf { it.length() }
        musicSize + lyricsSize + coverSize
    }
    
    // 清除缓存
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        cacheDir.deleteRecursively()
        lyricsDir.deleteRecursively()
        coverDir.deleteRecursively()
        cacheDir.mkdirs()
        lyricsDir.mkdirs()
        coverDir.mkdirs()
    }
    
    enum class CacheType {
        MUSIC,
        LYRICS,
        COVER
    }
    
    // 计算URL的MD5值作为文件名
    private fun String.md5(): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
} 