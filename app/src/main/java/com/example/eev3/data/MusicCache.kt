package com.example.eev3.data

import android.content.Context
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import android.os.Environment

class MusicCache(context: Context) {
    private val cacheDir = File(context.cacheDir, "music")
    private val lyricsDir = File(context.cacheDir, "lyrics")
    private val coverDir = File(context.cacheDir, "covers")
    private val mvDir = File(context.cacheDir, "mv")
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .build()

    init {
        cacheDir.mkdirs()
        lyricsDir.mkdirs()
        coverDir.mkdirs()
        mvDir.mkdirs()
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
            CacheType.MV -> "$songId.mp4"
        }
        val directory = when (type) {
            CacheType.MUSIC -> cacheDir
            CacheType.LYRICS -> lyricsDir
            CacheType.COVER -> coverDir
            CacheType.MV -> mvDir
        }
        return File(directory, fileName)
    }
    
    // 检查是否已缓存，增加检查下载文件
    fun isCached(url: String, type: CacheType): Boolean {
        val file = getCacheFile(url, type)
        val exists = file.exists()
        
        // 如果缓存不存在，检查是否已下载
        if (!exists) {
            val songTitle = extractSongTitle(url)
            val downloadFile = when (type) {
                CacheType.MUSIC -> File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "$songTitle.mp3")
                CacheType.MV -> File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "$songTitle.mp4")
                else -> null
            }
            
            if (downloadFile?.exists() == true) {
                println("MusicCache: 未找到缓存但找到下载文件: ${downloadFile.absolutePath}")
                return true
            }
        }
        
        println("MusicCache: 检查缓存 type=$type, exists=$exists, file=${file.absolutePath}")
        return exists
    }
    
    // 获取缓存文件URI，增加返回下载文件
    fun getCacheFileUri(url: String, type: CacheType): String {
        val file = getCacheFile(url, type)
        if (file.exists()) {
            return file.toURI().toString()
        }
        
        // 如果缓存不存在，尝试使用下载文件
        val songTitle = extractSongTitle(url)
        val downloadFile = when (type) {
            CacheType.MUSIC -> File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "$songTitle.mp3")
            CacheType.MV -> File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "$songTitle.mp4")
            else -> null
        }
        
        if (downloadFile?.exists() == true) {
            println("MusicCache: 使用下载文件作为缓存: ${downloadFile.absolutePath}")
            return downloadFile.toURI().toString()
        }
        
        return file.toURI().toString()
    }
    
    // 缓存音乐
    suspend fun cacheMusic(htmlUrl: String, audioUrl: String): String = withContext(Dispatchers.IO) {
        if (audioUrl.startsWith("file:")) {
            println("MusicCache: 跳过缓存，已经是本地文件 url=$audioUrl")
            return@withContext audioUrl
        }
        
        val songId = extractSongId(htmlUrl)
        val songTitle = extractSongTitle(htmlUrl)
        val cacheFile = getCacheFile(htmlUrl, CacheType.MUSIC)
        println("MusicCache: 开始缓存 type=MUSIC, songId=$songId")
        println("MusicCache: 缓存文件路径=${cacheFile.absolutePath}")
        
        if (cacheFile.exists()) {
            println("MusicCache: 文件已存在，直接返回缓存")
            return@withContext cacheFile.toURI().toString()
        }
        
        // 检查是否已下载
        val downloadFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "$songTitle.mp3")
        if (downloadFile.exists()) {
            println("MusicCache: 发现已下载的文件，复制到缓存目录")
            try {
                downloadFile.copyTo(cacheFile, overwrite = true)
                println("MusicCache: 复制成功，大小=${cacheFile.length()}")
                return@withContext cacheFile.toURI().toString()
            } catch (e: Exception) {
                println("MusicCache: 复制已下载文件失败: ${e.message}")
                // 复制失败时继续从网络下载
            }
        }
        
        try {
            val request = Request.Builder()
                .url(audioUrl)
                .build()
            
            client.newCall(request).execute().use { response ->
                response.body?.let { body ->
                    println("MusicCache: 开始下载 type=MUSIC, contentLength=${body.contentLength()}")
                    cacheFile.outputStream().use { output ->
                        body.byteStream().use { input ->
                            val bytesCopied = input.copyTo(output)
                            println("MusicCache: 下载完成 type=MUSIC, bytesCopied=$bytesCopied")
                        }
                    }
                }
            }
            
            println("MusicCache: 缓存成功 type=MUSIC, size=${cacheFile.length()}")
            cacheFile.toURI().toString()
        } catch (e: Exception) {
            println("MusicCache: 缓存失败 type=MUSIC, error=${e.message}")
            e.printStackTrace()
            cacheFile.delete()
            audioUrl
        }
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
    
    // 生成缓存文件名
    private fun generateCacheFileName(url: String, type: CacheType): String {
        val songId = extractSongId(url)
        return when (type) {
            CacheType.MUSIC -> "$songId.mp3"
            CacheType.LYRICS -> "$songId.lrc"
            CacheType.COVER -> "$songId.jpg"
            CacheType.MV -> "$songId.mp4"
        }
    }
    
    // 清除缓存
    suspend fun clearCache(skipUrls: List<String> = emptyList()) = withContext(Dispatchers.IO) {
        println("MusicCache: 开始清除缓存")
        println("MusicCache: 跳过的URL数量: ${skipUrls.size}")
        
        // 清除音乐缓存
        cacheDir.listFiles()?.forEach { file ->
            // 检查是否需要跳过该文件
            val shouldSkip = skipUrls.any { url ->
                val fileName = generateCacheFileName(url, CacheType.MUSIC)
                file.name == fileName
            }
            
            if (shouldSkip) {
                println("MusicCache: 跳过文件: ${file.name}")
            } else {
                println("MusicCache: 删除文件: ${file.name}")
                file.delete()
            }
        }
        
        // 清除歌词缓存
        lyricsDir.listFiles()?.forEach { file ->
            val shouldSkip = skipUrls.any { url ->
                val fileName = generateCacheFileName(url, CacheType.LYRICS)
                file.name == fileName
            }
            
            if (shouldSkip) {
                println("MusicCache: 跳过歌词: ${file.name}")
            } else {
                println("MusicCache: 删除歌词: ${file.name}")
                file.delete()
            }
        }
        
        // 清除封面缓存
        coverDir.listFiles()?.forEach { file ->
            val shouldSkip = skipUrls.any { url ->
                val fileName = generateCacheFileName(url, CacheType.COVER)
                file.name == fileName
            }
            
            if (shouldSkip) {
                println("MusicCache: 跳过封面: ${file.name}")
            } else {
                println("MusicCache: 删除封面: ${file.name}")
                file.delete()
            }
        }
        
        println("MusicCache: 缓存清除完成")
    }
    
    enum class CacheType {
        MUSIC,
        LYRICS,
        COVER,
        MV
    }
    
    // 计算URL的MD5值作为文件名
    private fun String.md5(): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
    
    // 获取 MV 真实地址
    suspend fun getMVUrl(songId: String): String = withContext(Dispatchers.IO) {
        val url = "http://www.eev3.com/plug/down.php?ac=vplay&id=$songId&q=1080"
        println("MusicCache: 获取 MV 地址: $url")
        
        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "*/*")
            .addHeader("Accept-Encoding", "identity;q=1, *;q=0")
            .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6")
            .addHeader("DNT", "1")
            .addHeader("Host", "www.eev3.com")
            .addHeader("Proxy-Connection", "keep-alive")
            .addHeader("Range", "bytes=0-")
            .addHeader("Referer", "http://www.eev3.com/video/$songId.html")
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0")
            .build()

        client.newCall(request).execute().use { response ->
            println("MusicCache: MV 地址请求响应码: ${response.code}")
            println("MusicCache: MV 地址响应头: ${response.headers}")
            
            val location = response.header("location")
            if (location == null) {
                println("MusicCache: 未找到 location 头")
                throw Exception("无法获取 MV 地址")
            }
            
            println("MusicCache: 获取到 MV 真实地址: $location")
            location
        }
    }
    
    // 缓存 MV
    suspend fun cacheMV(songId: String, mvUrl: String): String = withContext(Dispatchers.IO) {
        println("MusicCache: 准备缓存 MV")
        println("MusicCache: songId = $songId")
        println("MusicCache: mvUrl = $mvUrl")
        
        val cacheFile = File(mvDir, "$songId.mp4")
        println("MusicCache: 缓存文件路径: ${cacheFile.absolutePath}")
        
        if (cacheFile.exists()) {
            println("MusicCache: MV 已缓存，直接返回")
            return@withContext cacheFile.toURI().toString()
        }

        println("MusicCache: 开始下载 MV")
        
        try {
            val request = Request.Builder()
                .url(mvUrl)
                .build()
            
            val client = OkHttpClient.Builder()
                .followRedirects(true)
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    println("MusicCache: MV 下载请求失败: ${response.code}")
                    throw Exception("下载请求失败: ${response.code}")
                }
                
                response.body?.let { body ->
                    val contentLength = body.contentLength()
                    println("MusicCache: MV 文件大小: $contentLength bytes")
                    
                    cacheFile.outputStream().use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var totalBytesRead = 0L
                            
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead
                                if (totalBytesRead % (1024 * 1024) == 0L) {
                                    println("MusicCache: 已下载: ${totalBytesRead / (1024 * 1024)}MB")
                                }
                            }
                            println("MusicCache: MV 下载完成，总大小: ${totalBytesRead / (1024 * 1024)}MB")
                        }
                    }
                } ?: throw Exception("响应体为空")
            }
            
            println("MusicCache: MV 缓存成功: ${cacheFile.absolutePath}")
            cacheFile.toURI().toString()
        } catch (e: Exception) {
            println("MusicCache: MV 缓存失败: ${e.message}")
            println("MusicCache: 错误堆栈:")
            e.printStackTrace()
            cacheFile.delete()
            throw e
        }
    }

    // 从URL中提取歌曲标题
    private fun extractSongTitle(url: String): String {
        val songId = extractSongId(url)
        
        // 先尝试从缓存目录查找标题
        val cachedTitle = findSongTitleById(songId)
        if (cachedTitle != null) {
            return sanitizeFileName(cachedTitle)
        }
        
        // 如果找不到缓存的标题，尝试从下载目录查找匹配的文件
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val downloadedFiles = downloadDir.listFiles()?.filter { it.extension == "mp3" } ?: emptyList()
        
        // 遍历下载目录，查找包含 songId 的文件名
        val matchingFile = downloadedFiles.find { file ->
            val fileName = file.nameWithoutExtension
            fileName.contains(songId, ignoreCase = true)
        }
        
        return if (matchingFile != null) {
            matchingFile.nameWithoutExtension
        } else {
            songId
        }
    }

    // 根据ID查找歌曲标题
    private fun findSongTitleById(songId: String): String? {
        // 遍历缓存目录，查找对应的歌曲标题
        cacheDir.listFiles()?.forEach { file ->
            if (file.name.startsWith(songId)) {
                // 从缓存文件名中提取标题
                return file.nameWithoutExtension
            }
        }
        return null
    }

    // 清理文件名中的非法字符
    private fun sanitizeFileName(fileName: String): String {
        return fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), "_")
            .trim()
    }
} 