package com.example.eev3.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class Song(
    val title: String,
    val url: String = "",
    var isFavorite: Boolean = false,
    val hasMV: Boolean = false,
    val mvUrl: String = ""
)

class ObservableSong(
    val song: Song
) {
    var isFavorite by mutableStateOf(song.isFavorite)
}