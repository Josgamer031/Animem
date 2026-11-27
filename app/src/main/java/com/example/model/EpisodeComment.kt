package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class EpisodeComment(
    @DocumentId val id: String = "",
    val userId: String = "",
    val authorName: String = "",
    val authorAvatarUrl: String = "",
    val episodeKey: String = "",
    val animeTitle: String = "",
    val episodeNumber: Int = 1,
    val text: String = "",
    val spoiler: Boolean = false,
    val parentCommentId: String = "",
    val replyToCommentId: String = "",
    val replyToAuthorName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun buildEpisodeCommentsKey(
    providerId: String,
    slug: String,
    episodeNumber: Int
): String {
    val cleanProvider = providerId.trim().lowercase().replace(Regex("""[^a-zA-Z0-9_-]"""), "_").ifBlank { "jkanime" }
    val cleanSlug = slug.trim().lowercase().replace(Regex("""[^a-zA-Z0-9_-]"""), "_").ifBlank { "anime" }
    val ep = episodeNumber.coerceIn(1, 5000)
    return "${cleanProvider}_${cleanSlug}_ep$ep".take(128)
}
