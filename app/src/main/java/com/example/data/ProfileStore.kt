package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AnimeProvider
import com.example.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.profilesDataStore by preferencesDataStore(name = "kitsune_profiles")

class ProfileStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val profilesKey = stringPreferencesKey("user_profiles_list")
    private val activeProfileIdKey = stringPreferencesKey("active_profile_id")

    private val defaultProfile = UserProfile(
        id = DEFAULT_PROFILE_ID,
        name = "Otaku",
        avatarKey = "kitsune",
        preferredProviderId = AnimeProvider.JKANIME.id
    )

    val profilesFlow: Flow<List<UserProfile>> = context.profilesDataStore.data.map { prefs ->
        decodeProfiles(prefs[profilesKey])
    }

    val activeProfileFlow: Flow<UserProfile> = context.profilesDataStore.data.map { prefs ->
        val list = decodeProfiles(prefs[profilesKey])
        val activeId = prefs[activeProfileIdKey]
        list.firstOrNull { it.id == activeId } ?: list.first()
    }

    suspend fun createProfile(
        name: String,
        avatarKey: String,
        preferredProviderId: String = AnimeProvider.JKANIME.id
    ): UserProfile {
        val cleanName = name.trim().take(24).ifBlank { "Perfil" }
        val newProfile = UserProfile(
            id = "p_${UUID.randomUUID().toString().take(8)}",
            name = cleanName,
            avatarKey = avatarKey.ifBlank { "kitsune" },
            preferredProviderId = preferredProviderId,
            createdAtMillis = System.currentTimeMillis()
        )
        context.profilesDataStore.edit { prefs ->
            val current = decodeProfiles(prefs[profilesKey])
            val updated = current + newProfile
            prefs[profilesKey] = json.encodeToString(updated)
            prefs[activeProfileIdKey] = newProfile.id
        }
        return newProfile
    }

    suspend fun updateProfile(profileId: String, name: String, avatarKey: String) {
        val cleanName = name.trim().take(24).ifBlank { "Perfil" }
        context.profilesDataStore.edit { prefs ->
            val current = decodeProfiles(prefs[profilesKey])
            val updated = current.map { profile ->
                if (profile.id == profileId) {
                    profile.copy(name = cleanName, avatarKey = avatarKey.ifBlank { profile.avatarKey })
                } else {
                    profile
                }
            }
            prefs[profilesKey] = json.encodeToString(updated)
        }
    }

    suspend fun switchProfile(profileId: String) {
        context.profilesDataStore.edit { prefs ->
            val current = decodeProfiles(prefs[profilesKey])
            if (current.any { it.id == profileId }) {
                prefs[activeProfileIdKey] = profileId
            }
        }
    }

    suspend fun updatePreferredProvider(profileId: String, providerId: String) {
        context.profilesDataStore.edit { prefs ->
            val current = decodeProfiles(prefs[profilesKey])
            val updated = current.map {
                if (it.id == profileId) it.copy(preferredProviderId = providerId) else it
            }
            prefs[profilesKey] = json.encodeToString(updated)
        }
    }

    suspend fun deleteProfile(profileId: String): Boolean {
        var deleted = false
        context.profilesDataStore.edit { prefs ->
            val current = decodeProfiles(prefs[profilesKey])
            if (current.size <= 1) return@edit
            val remaining = current.filterNot { it.id == profileId }
            if (remaining.size < current.size) {
                deleted = true
                prefs[profilesKey] = json.encodeToString(remaining)
                val activeId = prefs[activeProfileIdKey]
                if (activeId == profileId || remaining.none { it.id == activeId }) {
                    prefs[activeProfileIdKey] = remaining.first().id
                }
            }
        }
        return deleted
    }

    private fun decodeProfiles(raw: String?): List<UserProfile> {
        if (raw.isNullOrBlank()) return listOf(defaultProfile)
        return try {
            val parsed = json.decodeFromString<List<UserProfile>>(raw)
            parsed.ifEmpty { listOf(defaultProfile) }
        } catch (_: Throwable) {
            listOf(defaultProfile)
        }
    }

    companion object {
        const val DEFAULT_PROFILE_ID = "default"
    }
}
