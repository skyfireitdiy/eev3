package com.example.eev3.data

import android.content.Context
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

class MusicCache(private val context: Context) {
    private val cacheDir = File(context.cacheDir, "music_cache").apply {
        if (!exists()) mkdirs()
    }
    
    private val client = OkHttpClient.Builder().build()
    
    // 从URL中提取歌曲ID
    private fun extractSongId(url: String): String {
        // 从URL中提取歌曲ID，例如 M800003CA2I33KrRhr
        return url.substringAfterLast("/")
            .substringBefore(".")
    }
    
    // 获取缓存文件
    private fun getCacheFile(url: String): File {
        val songId = extractSongId(url)
        println("MusicCache: URL=$url")
        println("MusicCache: 歌曲ID=$songId")
        
        val fileName = "$songId.mp3"
        println("MusicCache: 缓存文件名=$fileName")
        
        return File(cacheDir, fileName)
    }
    
    // 检查是否已缓存
    fun isCached(url: String): Boolean {
        // 如果已经是本地文件，直接返回true
        if (url.startsWith("file:")) {
            println("MusicCache: 已经是本地文件 url=$url")
            return true
        }
        
        val cacheFile = getCacheFile(url)
        val exists = cacheFile.exists() && cacheFile.length() > 0
        println("MusicCache: 检查缓存 songId=${extractSongId(url)}, exists=$exists, size=${cacheFile.length()}")
        
        // 列出所有缓存文件用于调试
        println("MusicCache: 当前缓存文件列表:")
        cacheDir.listFiles()?.forEach { file ->
            println("MusicCache: - ${file.name} (${file.length()} bytes)")
        }
        
        return exists
    }
    
    // 获取缓存文件的URI
    fun getCacheFileUri(url: String): String {
        // 如果已经是本地文件，直接返回
        if (url.startsWith("file:")) {
            println("MusicCache: 返回本地文件 url=$url")
            return url
        }
        
        val uri = getCacheFile(url).toURI().toString()
        println("MusicCache: 获取缓存URI songId=${extractSongId(url)}, uri=$uri")
        return uri
    }
    
    // 下载并缓存音乐
    suspend fun cacheMusic(url: String): String = withContext(Dispatchers.IO) {
        // 如果已经是本地文件，直接返回
        if (url.startsWith("file:")) {
            println("MusicCache: 跳过缓存，已经是本地文件 url=$url")
            return@withContext url
        }
        
        val songId = extractSongId(url)
        println("MusicCache: 开始缓存 songId=$songId")
        val cacheFile = getCacheFile(url)
        
        // 如果已经缓存，直接返回
        if (isCached(url)) {
            println("MusicCache: 已有缓存，直接返回")
            return@withContext cacheFile.toURI().toString()
        }
        
        try {
            // 创建请求
            val request = Request.Builder()
                .url(url)
                .build()
            
            // 执行请求并保存到缓存
            client.newCall(request).execute().use { response ->
                response.body?.let { body ->
                    println("MusicCache: 开始下载 songId=$songId, contentLength=${body.contentLength()}")
                    cacheFile.outputStream().use { output ->
                        body.byteStream().use { input ->
                            val bytesCopied = input.copyTo(output)
                            println("MusicCache: 下载完成 songId=$songId, bytesCopied=$bytesCopied")
                        }
                    }
                }
            }
            
            println("MusicCache: 缓存成功 songId=$songId, size=${cacheFile.length()}")
            cacheFile.toURI().toString()
        } catch (e: Exception) {
            println("MusicCache: 缓存失败 songId=$songId, error=${e.message}")
            e.printStackTrace()
            // 如果下载失败，删除可能不完整的缓存文件
            cacheFile.delete()
            // 返回原始URL
            url
        }
    }
    
    // 清理缓存
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        cacheDir.listFiles()?.forEach { it.delete() }
    }
    
    // 获取缓存大小
    fun getCacheSize(): Long {
        val size = cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        println("MusicCache: 当前缓存总大小=$size bytes")
        return size
    }
    
    // 列出所有缓存文件
    fun listCacheFiles() {
        println("MusicCache: 缓存文件列表:")
        cacheDir.listFiles()?.forEach { file ->
            println("MusicCache: ${file.name} (${file.length()} bytes)")
        }
    }
    
    // 计算URL的MD5值作为文件名
    private fun String.md5(): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
} 