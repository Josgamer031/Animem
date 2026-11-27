package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.FavoriteAnime
import com.example.model.WatchStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.favoritesDataStore by preferencesDataStore(name = "kitsune_favorites")

class FavoritesStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val favoritesKey = stringPreferencesKey("favorite_animes")

    val favoritesFlow: Flow<List<FavoriteAnime>> = context.favoritesDataStore.data.map { prefs ->
        decode(prefs[favoritesKey]).sortedByDescending { it.addedAtMillis }
    }

    suspend fun toggleFavorite(entry: FavoriteAnime): Boolean {
        var isNowFavorite = false
        context.favoritesDataStore.edit { prefs ->
            val current = decode(prefs[favoritesKey])
            val exists = current.any {
                it.profileId == entry.profileId &&
                    it.providerId == entry.providerId &&
                    it.animeSlug == entry.animeSlug
            }
            val updated = if (exists) {
                isNowFavorite = false
                current.filterNot {
                    it.profileId == entry.profileId &&
                        it.providerId == entry.providerId &&
                        it.animeSlug == entry.animeSlug
                }
            } else {
                isNowFavorite = true
                listOf(entry.copy(addedAtMillis = System.currentTimeMillis())) + current
            }
            prefs[favoritesKey] = json.encodeToString(updated)
        }
        return isNowFavorite
    }

    suspend fun updateWatchStatus(
        profileId: String,
        providerId: String,
        animeSlug: String,
        status: WatchStatus
    ) {
        context.favoritesDataStore.edit { prefs ->
            val current = decode(prefs[favoritesKey])
            val updated = current.map { item ->
                if (item.profileId == profileId &&
                    item.providerId == providerId &&
                    item.animeSlug == animeSlug
                ) {
                    item.copy(watchStatus = status)
                } else {
                    item
                }
            }
            prefs[favoritesKey] = json.encodeToString(updated)
        }
    }

    suspend fun removeFavorite(profileId: String, providerId: String, animeSlug: String) {
        context.favoritesDataStore.edit { prefs ->
            val current = decode(prefs[favoritesKey])
            val updated = current.filterNot {
                it.profileId == profileId &&
                    it.providerId == providerId &&
                    it.animeSlug == animeSlug
            }
            prefs[favoritesKey] = json.encodeToString(updated)
        }
    }

    suspend fun clearProfile(profileId: String) {
        context.favoritesDataStore.edit { prefs ->
            val current = decode(prefs[favoritesKey])
            val remaining = current.filterNot { it.profileId == profileId }
            prefs[favoritesKey] = json.encodeToString(remaining)
        }
    }

    private fun decode(raw: String?): List<FavoriteAnime> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<FavoriteAnime>>(raw)
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
