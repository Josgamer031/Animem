package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.DeviceMode
import com.example.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.userPrefsDataStore by preferencesDataStore(name = "kitsune_user_prefs")

class UserPreferencesStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val autoPlayNextKey = booleanPreferencesKey("auto_play_next")
    private val autoSkipOpeningKey = booleanPreferencesKey("auto_skip_opening")
    private val recentSearchesKey = stringPreferencesKey("recent_searches")
    private val deviceModeKey = stringPreferencesKey("device_mode")
    private val hasSelectedDeviceModeKey = booleanPreferencesKey("has_selected_device_mode")
    private val premiereAlertsEnabledKey = booleanPreferencesKey("premiere_alerts_enabled")
    private val notifiedPremiereKeysKey = stringPreferencesKey("notified_premiere_keys")

    val preferencesFlow: Flow<UserPreferences> = context.userPrefsDataStore.data.map { prefs ->
        val rawMode = prefs[deviceModeKey]
        val parsedMode = DeviceMode.entries.firstOrNull { it.name == rawMode } ?: DeviceMode.PHONE
        UserPreferences(
            autoPlayNext = prefs[autoPlayNextKey] ?: true,
            autoSkipOpening = prefs[autoSkipOpeningKey] ?: false,
            recentSearches = decodeStringList(prefs[recentSearchesKey]),
            deviceMode = parsedMode,
            hasSelectedDeviceMode = prefs[hasSelectedDeviceModeKey] ?: false,
            premiereAlertsEnabled = prefs[premiereAlertsEnabledKey] ?: false,
            notifiedPremiereKeys = decodeStringList(prefs[notifiedPremiereKeysKey])
        )
    }

    suspend fun setDeviceMode(mode: DeviceMode, markSelected: Boolean = true) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[deviceModeKey] = mode.name
            if (markSelected) {
                prefs[hasSelectedDeviceModeKey] = true
            }
        }
    }

    suspend fun setPremiereAlertsEnabled(enabled: Boolean) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[premiereAlertsEnabledKey] = enabled
        }
    }

    suspend fun recordNotifiedPremiereKeys(newKeys: List<String>) {
        if (newKeys.isEmpty()) return
        context.userPrefsDataStore.edit { prefs ->
            val current = decodeStringList(prefs[notifiedPremiereKeysKey])
            val merged = (newKeys + current).distinct().take(100)
            prefs[notifiedPremiereKeysKey] = json.encodeToString(merged)
        }
    }

    suspend fun setAutoPlayNext(enabled: Boolean) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[autoPlayNextKey] = enabled
        }
    }

    suspend fun setAutoSkipOpening(enabled: Boolean) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[autoSkipOpeningKey] = enabled
        }
    }

    suspend fun recordSearchQuery(rawQuery: String) {
        val cleaned = rawQuery.trim()
        if (cleaned.length < 2) return
        context.userPrefsDataStore.edit { prefs ->
            val current = decodeStringList(prefs[recentSearchesKey])
            val updated = (listOf(cleaned) + current.filterNot {
                it.equals(cleaned, ignoreCase = true)
            }).take(8)
            prefs[recentSearchesKey] = json.encodeToString(updated)
        }
    }

    suspend fun removeSearchQuery(query: String) {
        context.userPrefsDataStore.edit { prefs ->
            val current = decodeStringList(prefs[recentSearchesKey])
            val updated = current.filterNot { it.equals(query, ignoreCase = true) }
            prefs[recentSearchesKey] = json.encodeToString(updated)
        }
    }

    suspend fun clearSearchQueries() {
        context.userPrefsDataStore.edit { prefs ->
            prefs.remove(recentSearchesKey)
        }
    }

    private fun decodeStringList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<String>>(raw)
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
