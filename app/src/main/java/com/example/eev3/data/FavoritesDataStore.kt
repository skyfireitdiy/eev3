package com.example.eev3.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "favorites")

class FavoritesDataStore(private val context: Context) {
    private val gson = Gson()
    
    companion object {
        private val FAVORITES_KEY = stringPreferencesKey("favorites")
    }
    
    // 获取收藏列表
    val favoritesFlow: Flow<List<Song>> = context.dataStore.data.map { preferences ->
        val favoritesJson = preferences[FAVORITES_KEY] ?: "[]"
        try {
            val type = object : TypeToken<List<Song>>() {}.type
            val songs = gson.fromJson<List<Song>>(favoritesJson, type)
            // 确保旧数据也有 hasMV 和 mvUrl 字段
            songs.map { song ->
                song.copy(
                    hasMV = song.hasMV,
                    mvUrl = song.mvUrl ?: ""  // 如果 mvUrl 为 null，使用空字符串
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    // 保存收藏列表
    suspend fun saveFavorites(favorites: List<Song>) {
        context.dataStore.edit { preferences ->
            val favoritesJson = gson.toJson(favorites)
            preferences[FAVORITES_KEY] = favoritesJson
        }
    }
} 