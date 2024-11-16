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
            gson.fromJson(favoritesJson, type)
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