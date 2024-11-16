package com.example.eev3.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.eev3.data.FavoritesDataStore
import com.example.eev3.data.MusicCache

class MusicViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MusicViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MusicViewModel(
                FavoritesDataStore(context),
                MusicCache(context),
                context
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
} 