package com.example.eev3.data

sealed class DownloadStatus {
    object NotStarted : DownloadStatus()
    object Downloading : DownloadStatus()
    data class Success(val path: String) : DownloadStatus()
    data class Error(val message: String) : DownloadStatus()
} 