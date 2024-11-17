package com.example.eev3.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PlayerData(
    val coverImage: String,
    val title: String,
    val audioUrl: String,
    val lyrics: String = ""
) : Parcelable 