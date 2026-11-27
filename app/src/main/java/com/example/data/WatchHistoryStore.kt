package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.WatchHistoryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.watchHistoryDataStore by preferencesDataStore(name = "kitsune_watch_history")

class WatchHistoryStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val historyKey = stringPreferencesKey("watch_history_entries")

    val historyFlow: Flow<List<WatchHistoryEntry>> = context.watchHistoryDataStore.data.map { prefs ->
        val raw = prefs[historyKey] ?: return@map emptyList()
        try {
            json.decodeFromString<List<WatchHistoryEntry>>(raw)
                .sortedByDescending { it.updatedAtMillis }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    suspend fun upsertProgress(entry: WatchHistoryEntry) {
        context.watchHistoryDataStore.edit { prefs ->
            val current = prefs[historyKey]?.let { raw ->
                try {
                    json.decodeFromString<List<WatchHistoryEntry>>(raw)
                } catch (_: Throwable) {
                    emptyList()
                }
            } ?: emptyList()

            val filtered = current.filterNot {
                it.profileId == entry.profileId &&
                    it.providerId == entry.providerId &&
                    it.animeSlug == entry.animeSlug &&
                    it.episodeNumber == entry.episodeNumber
            }
            val sameProfileEntries = filtered.filter { it.profileId == entry.profileId }.take(199)
            val otherProfileEntries = filtered.filter { it.profileId != entry.profileId }
            val updated = listOf(entry) + sameProfileEntries + otherProfileEntries
            prefs[historyKey] = json.encodeToString(updated)
        }
    }

    suspend fun removeEntry(
        profileId: String,
        providerId: String,
        animeSlug: String,
        episodeNumber: Int
    ) {
        context.watchHistoryDataStore.edit { prefs ->
            val current = prefs[historyKey]?.let { raw ->
                try {
                    json.decodeFromString<List<WatchHistoryEntry>>(raw)
                } catch (_: Throwable) {
                    emptyList()
                }
            } ?: emptyList()
            val updated = current.filterNot {
                it.profileId == profileId &&
                    it.providerId == providerId &&
                    it.animeSlug == animeSlug &&
                    it.episodeNumber == episodeNumber
            }
            prefs[historyKey] = json.encodeToString(updated)
        }
    }

    suspend fun mergeFromCloud(cloudEntries: List<WatchHistoryEntry>) {
        if (cloudEntries.isEmpty()) return
        context.watchHistoryDataStore.edit { prefs ->
            val current = prefs[historyKey]?.let { raw ->
                try {
                    json.decodeFromString<List<WatchHistoryEntry>>(raw)
                } catch (_: Throwable) {
                    emptyList()
                }
            } ?: emptyList()

            val mergedMap = LinkedHashMap<String, WatchHistoryEntry>()
            (current + cloudEntries).forEach { entry ->
                val key = "${entry.profileId}|${entry.providerId}|${entry.animeSlug}|${entry.episodeNumber}"
                val prev = mergedMap[key]
                if (prev == null || entry.updatedAtMillis >= prev.updatedAtMillis) {
                    mergedMap[key] = entry
                }
            }
            val sorted = mergedMap.values.sortedByDescending { it.updatedAtMillis }.take(300)
            prefs[historyKey] = json.encodeToString(sorted)
        }
    }

    suspend fun clearProfile(profileId: String) {
        context.watchHistoryDataStore.edit { prefs ->
            val current = prefs[historyKey]?.let { raw ->
                try {
                    json.decodeFromString<List<WatchHistoryEntry>>(raw)
                } catch (_: Throwable) {
                    emptyList()
                }
            } ?: emptyList()
            val remaining = current.filterNot { it.profileId == profileId }
            prefs[historyKey] = json.encodeToString(remaining)
        }
    }

    suspend fun clearAll() {
        context.watchHistoryDataStore.edit { prefs ->
            prefs.remove(historyKey)
        }
    }
}
