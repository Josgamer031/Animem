package com.example.data

import android.content.Context
import androidx.annotation.Keep
import com.example.R
import com.example.model.AnimeProvider
import com.example.model.FavoriteAnime
import com.example.model.WatchHistoryEntry
import com.example.model.WatchStatus
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

@Keep
@IgnoreExtraProperties
data class CloudFavoriteDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val animeSlug: String = "",
    val animeTitle: String = "",
    val coverUrl: String = "",
    val type: String = "",
    val status: String = "",
    val totalEpisodesText: String = "",
    val providerId: String = AnimeProvider.JKANIME.id,
    val profileId: String = "default",
    val watchStatus: String = WatchStatus.WATCHING.name,
    val addedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toDomain(): FavoriteAnime? {
        if (animeSlug.isBlank() || animeTitle.isBlank()) return null
        val parsedStatus = WatchStatus.entries.firstOrNull { it.name == watchStatus }
            ?: WatchStatus.WATCHING
        return FavoriteAnime(
            animeSlug = animeSlug,
            animeTitle = animeTitle,
            coverUrl = coverUrl,
            type = type,
            status = status,
            totalEpisodesText = totalEpisodesText,
            providerId = providerId.ifBlank { AnimeProvider.JKANIME.id },
            profileId = profileId.ifBlank { "default" },
            addedAtMillis = addedAtMillis,
            watchStatus = parsedStatus
        )
    }
}

@Keep
@IgnoreExtraProperties
data class CloudHistoryDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val animeSlug: String = "",
    val animeTitle: String = "",
    val coverUrl: String = "",
    val episodeNumber: Int = 1,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val updatedAtMillis: Long = 0L,
    val providerId: String = AnimeProvider.JKANIME.id,
    val episodeUrl: String = "",
    val profileId: String = "default",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toDomain(): WatchHistoryEntry? {
        if (animeSlug.isBlank() || animeTitle.isBlank() || episodeNumber < 1) return null
        return WatchHistoryEntry(
            animeSlug = animeSlug,
            animeTitle = animeTitle,
            coverUrl = coverUrl,
            episodeNumber = episodeNumber,
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L),
            updatedAtMillis = updatedAtMillis.coerceAtLeast(0L),
            providerId = providerId.ifBlank { AnimeProvider.JKANIME.id },
            episodeUrl = episodeUrl,
            profileId = profileId.ifBlank { "default" }
        )
    }
}

data class CloudSyncResult(
    val mergedFavorites: List<FavoriteAnime>,
    val mergedHistory: List<WatchHistoryEntry>,
    val totalFavorites: Int,
    val totalHistory: Int
)

class CloudSyncRepository(context: Context) {

    private val db: FirebaseFirestore =
        FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))

    private fun favoritesCol(userId: String) =
        db.collection("users").document(userId).collection("favorites")

    private fun historyCol(userId: String) =
        db.collection("users").document(userId).collection("history")

    private fun sanitizeId(raw: String): String =
        raw.trim().replace(Regex("""[^a-zA-Z0-9_-]"""), "_").ifBlank { "item" }.take(128)

    private fun buildFavoriteDocId(profileId: String, providerId: String, slug: String): String =
        sanitizeId("${profileId}__${providerId}__${slug}")

    private fun buildHistoryDocId(profileId: String, providerId: String, slug: String, episodeNumber: Int): String =
        sanitizeId("${profileId}__${providerId}__${slug}__${episodeNumber.coerceIn(1, 5000)}")

    suspend fun syncAll(
        userId: String,
        localFavorites: List<FavoriteAnime>,
        localHistory: List<WatchHistoryEntry>
    ): Result<CloudSyncResult> {
        val favPath = "users/$userId/favorites"
        val histPath = "users/$userId/history"
        return try {
            val cloudFavSnap = favoritesCol(userId).get().await()
            val cloudHistSnap = historyCol(userId).get().await()

            val cloudFavByDocId = LinkedHashMap<String, CloudFavoriteDoc>()
            cloudFavSnap.documents.forEach { doc ->
                doc.toObject(CloudFavoriteDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?.let { cloudFavByDocId[doc.id] = it }
            }

            val cloudHistByDocId = LinkedHashMap<String, CloudHistoryDoc>()
            cloudHistSnap.documents.forEach { doc ->
                doc.toObject(CloudHistoryDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?.let { cloudHistByDocId[doc.id] = it }
            }

            // Merge favorites (cloud <-> local) and push missing/newer local items
            val mergedFavMap = LinkedHashMap<String, FavoriteAnime>()
            cloudFavByDocId.values.mapNotNull { it.toDomain() }.forEach { fav ->
                mergedFavMap[buildFavoriteDocId(fav.profileId, fav.providerId, fav.animeSlug)] = fav
            }
            val favToPush = mutableListOf<Pair<String, FavoriteAnime>>()
            localFavorites.forEach { local ->
                val docId = buildFavoriteDocId(local.profileId, local.providerId, local.animeSlug)
                val cloud = mergedFavMap[docId]
                if (cloud == null || local.addedAtMillis > cloud.addedAtMillis ||
                    (local.addedAtMillis == cloud.addedAtMillis && local.watchStatus != cloud.watchStatus)
                ) {
                    mergedFavMap[docId] = local
                    favToPush += docId to local
                }
            }

            favToPush.forEach { (docId, item) ->
                val exists = cloudFavByDocId.containsKey(docId)
                writeFavoriteInternal(userId, docId, item, exists)
            }

            // Merge watch history (cloud <-> local) and push newer local progress
            val mergedHistMap = LinkedHashMap<String, WatchHistoryEntry>()
            cloudHistByDocId.values.mapNotNull { it.toDomain() }.forEach { entry ->
                mergedHistMap[buildHistoryDocId(entry.profileId, entry.providerId, entry.animeSlug, entry.episodeNumber)] = entry
            }
            val histToPush = mutableListOf<Pair<String, WatchHistoryEntry>>()
            localHistory.take(150).forEach { local ->
                val docId = buildHistoryDocId(local.profileId, local.providerId, local.animeSlug, local.episodeNumber)
                val cloud = mergedHistMap[docId]
                if (cloud == null || local.updatedAtMillis > cloud.updatedAtMillis) {
                    mergedHistMap[docId] = local
                    histToPush += docId to local
                }
            }

            histToPush.forEach { (docId, entry) ->
                val exists = cloudHistByDocId.containsKey(docId)
                writeHistoryInternal(userId, docId, entry, exists)
            }

            Result.success(
                CloudSyncResult(
                    mergedFavorites = mergedFavMap.values.sortedByDescending { it.addedAtMillis },
                    mergedHistory = mergedHistMap.values.sortedByDescending { it.updatedAtMillis },
                    totalFavorites = mergedFavMap.size,
                    totalHistory = mergedHistMap.size
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val path = if (e.message?.contains("history", true) == true) histPath else favPath
            Result.failure(Exception(handleFirestoreError(e, OperationType.LIST, path)))
        }
    }

    suspend fun upsertFavorite(userId: String, favorite: FavoriteAnime): Result<Unit> {
        val docId = buildFavoriteDocId(favorite.profileId, favorite.providerId, favorite.animeSlug)
        val path = "users/$userId/favorites/$docId"
        return try {
            val snap = favoritesCol(userId).document(docId).get().await()
            writeFavoriteInternal(userId, docId, favorite, snap.exists())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception(handleFirestoreError(e, OperationType.UPDATE, path)))
        }
    }

    suspend fun deleteFavorite(
        userId: String,
        profileId: String,
        providerId: String,
        slug: String
    ): Result<Unit> {
        val docId = buildFavoriteDocId(profileId, providerId, slug)
        val path = "users/$userId/favorites/$docId"
        return try {
            favoritesCol(userId).document(docId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception(handleFirestoreError(e, OperationType.DELETE, path)))
        }
    }

    suspend fun upsertHistory(userId: String, entry: WatchHistoryEntry): Result<Unit> {
        val docId = buildHistoryDocId(entry.profileId, entry.providerId, entry.animeSlug, entry.episodeNumber)
        val path = "users/$userId/history/$docId"
        return try {
            val snap = historyCol(userId).document(docId).get().await()
            writeHistoryInternal(userId, docId, entry, snap.exists())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception(handleFirestoreError(e, OperationType.UPDATE, path)))
        }
    }

    suspend fun deleteHistoryEntry(
        userId: String,
        profileId: String,
        providerId: String,
        slug: String,
        episodeNumber: Int
    ): Result<Unit> {
        val docId = buildHistoryDocId(profileId, providerId, slug, episodeNumber)
        val path = "users/$userId/history/$docId"
        return try {
            historyCol(userId).document(docId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception(handleFirestoreError(e, OperationType.DELETE, path)))
        }
    }

    suspend fun clearProfileHistory(userId: String, profileId: String): Result<Unit> {
        val path = "users/$userId/history"
        return try {
            val docs = historyCol(userId).whereEqualTo("profileId", profileId).get().await()
            docs.documents.forEach { doc ->
                doc.reference.delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception(handleFirestoreError(e, OperationType.DELETE, path)))
        }
    }

    private suspend fun writeFavoriteInternal(
        userId: String,
        docId: String,
        favorite: FavoriteAnime,
        exists: Boolean
    ) {
        val validProvider = if (favorite.providerId == AnimeProvider.LATANIME.id) {
            AnimeProvider.LATANIME.id
        } else {
            AnimeProvider.JKANIME.id
        }
        val ref = favoritesCol(userId).document(docId)
        if (!exists) {
            val createMap = hashMapOf<String, Any>(
                "userId" to userId,
                "animeSlug" to favorite.animeSlug.trim().ifBlank { "anime" }.take(160),
                "animeTitle" to favorite.animeTitle.trim().ifBlank { "Anime" }.take(200),
                "providerId" to validProvider,
                "profileId" to favorite.profileId.trim().ifBlank { "default" }.take(64),
                "watchStatus" to favorite.watchStatus.name,
                "addedAtMillis" to favorite.addedAtMillis.coerceAtLeast(0L),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            favorite.coverUrl.trim().takeIf { it.isNotBlank() }?.let { createMap["coverUrl"] = it.take(400) }
            favorite.type.trim().takeIf { it.isNotBlank() }?.let { createMap["type"] = it.take(40) }
            favorite.status.trim().takeIf { it.isNotBlank() }?.let { createMap["status"] = it.take(40) }
            favorite.totalEpisodesText.trim().takeIf { it.isNotBlank() }?.let {
                createMap["totalEpisodesText"] = it.take(40)
            }
            ref.set(createMap).await()
        } else {
            val updateMap = hashMapOf<String, Any>(
                "animeTitle" to favorite.animeTitle.trim().ifBlank { "Anime" }.take(200),
                "watchStatus" to favorite.watchStatus.name,
                "addedAtMillis" to favorite.addedAtMillis.coerceAtLeast(0L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            favorite.coverUrl.trim().takeIf { it.isNotBlank() }?.let { updateMap["coverUrl"] = it.take(400) }
            favorite.type.trim().takeIf { it.isNotBlank() }?.let { updateMap["type"] = it.take(40) }
            favorite.status.trim().takeIf { it.isNotBlank() }?.let { updateMap["status"] = it.take(40) }
            favorite.totalEpisodesText.trim().takeIf { it.isNotBlank() }?.let {
                updateMap["totalEpisodesText"] = it.take(40)
            }
            ref.update(updateMap).await()
        }
    }

    private suspend fun writeHistoryInternal(
        userId: String,
        docId: String,
        entry: WatchHistoryEntry,
        exists: Boolean
    ) {
        val validProvider = if (entry.providerId == AnimeProvider.LATANIME.id) {
            AnimeProvider.LATANIME.id
        } else {
            AnimeProvider.JKANIME.id
        }
        val ref = historyCol(userId).document(docId)
        if (!exists) {
            val createMap = hashMapOf<String, Any>(
                "userId" to userId,
                "animeSlug" to entry.animeSlug.trim().ifBlank { "anime" }.take(160),
                "animeTitle" to entry.animeTitle.trim().ifBlank { "Anime" }.take(200),
                "episodeNumber" to entry.episodeNumber.coerceIn(1, 5000),
                "positionMs" to entry.positionMs.coerceAtLeast(0L),
                "durationMs" to entry.durationMs.coerceAtLeast(0L),
                "updatedAtMillis" to entry.updatedAtMillis.coerceAtLeast(0L),
                "providerId" to validProvider,
                "profileId" to entry.profileId.trim().ifBlank { "default" }.take(64),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            entry.coverUrl.trim().takeIf { it.isNotBlank() }?.let { createMap["coverUrl"] = it.take(400) }
            entry.episodeUrl.trim().takeIf { it.isNotBlank() }?.let { createMap["episodeUrl"] = it.take(400) }
            ref.set(createMap).await()
        } else {
            val updateMap = hashMapOf<String, Any>(
                "animeTitle" to entry.animeTitle.trim().ifBlank { "Anime" }.take(200),
                "positionMs" to entry.positionMs.coerceAtLeast(0L),
                "durationMs" to entry.durationMs.coerceAtLeast(0L),
                "updatedAtMillis" to entry.updatedAtMillis.coerceAtLeast(0L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            entry.coverUrl.trim().takeIf { it.isNotBlank() }?.let { updateMap["coverUrl"] = it.take(400) }
            entry.episodeUrl.trim().takeIf { it.isNotBlank() }?.let { updateMap["episodeUrl"] = it.take(400) }
            ref.update(updateMap).await()
        }
    }
}
