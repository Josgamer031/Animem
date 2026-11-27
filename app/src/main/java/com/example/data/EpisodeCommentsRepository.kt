package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.example.model.EpisodeComment
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

enum class OperationType {
    LIST,
    GET,
    CREATE,
    UPDATE,
    DELETE
}

fun handleFirestoreError(
    error: Exception,
    operationType: OperationType,
    path: String
): String {
    val firestoreException = error as? FirebaseFirestoreException
    val currentUser = runCatching { Firebase.auth.currentUser }.getOrNull()
    val payload = JSONObject().apply {
        put("error", error.message ?: error.javaClass.simpleName)
        put("operationType", operationType.name.lowercase())
        put("path", path)
        put("code", firestoreException?.code?.name ?: "UNKNOWN")
        put("uid", currentUser?.uid ?: "unauthenticated")
    }
    Log.e("FirestoreError", payload.toString(), error)

    return when (firestoreException?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "No tienes permisos para realizar esta acción. Verifica tu sesión e intenta de nuevo."
        FirebaseFirestoreException.Code.UNAUTHENTICATED ->
            "Inicia sesión con Google para acceder a esta función."
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
            "Sin conexión estable con el servidor. Intenta de nuevo."
        FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
            "Error de configuración de consulta en la base de datos."
        FirebaseFirestoreException.Code.NOT_FOUND ->
            "El elemento ya no existe o fue eliminado."
        else -> "Ocurrió un error al sincronizar con la nube. Intenta de nuevo."
    }
}

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class EpisodeCommentsRepository(context: Context) {

    private val db: FirebaseFirestore =
        FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))

    private fun commentsCollection(episodeKey: String) =
        db.collection("episodes").document(episodeKey).collection("comments")

    fun observeComments(episodeKey: String): Flow<List<EpisodeComment>> =
        commentsCollection(episodeKey)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(120)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(
                        EpisodeComment::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
            }

    suspend fun postComment(
        episodeKey: String,
        animeTitle: String,
        episodeNumber: Int,
        user: FirebaseUser,
        text: String,
        spoiler: Boolean,
        parentCommentId: String = "",
        replyToCommentId: String = "",
        replyToAuthorName: String = ""
    ): Result<Unit> {
        val cleanText = text.trim()
        if (cleanText.isEmpty() || cleanText.length > 500) {
            return Result.failure(IllegalArgumentException("El comentario debe tener entre 1 y 500 caracteres."))
        }
        val path = "episodes/$episodeKey/comments"
        return try {
            val authorName = user.displayName?.trim()?.takeIf { it.isNotBlank() }?.take(60)
                ?: "Otaku"
            val avatarUrl = user.photoUrl?.toString()?.trim()?.takeIf { it.isNotBlank() }?.take(400)
            val payload = hashMapOf<String, Any>(
                "userId" to user.uid,
                "authorName" to authorName,
                "episodeKey" to episodeKey,
                "animeTitle" to animeTitle.trim().ifBlank { "Anime" }.take(140),
                "episodeNumber" to episodeNumber.coerceIn(1, 5000),
                "text" to cleanText,
                "spoiler" to spoiler,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (avatarUrl != null) {
                payload["authorAvatarUrl"] = avatarUrl
            }
            val validParentId = parentCommentId.trim().replace(Regex("""[^a-zA-Z0-9_-]"""), "").take(128)
            val validReplyId = replyToCommentId.trim().replace(Regex("""[^a-zA-Z0-9_-]"""), "").take(128)
            val cleanReplyAuthor = replyToAuthorName.trim().take(60)
            if (validParentId.isNotEmpty()) {
                payload["parentCommentId"] = validParentId
                if (validReplyId.isNotEmpty()) {
                    payload["replyToCommentId"] = validReplyId
                }
                if (cleanReplyAuthor.isNotEmpty()) {
                    payload["replyToAuthorName"] = cleanReplyAuthor
                }
            }
            commentsCollection(episodeKey).document().set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception(handleFirestoreError(e, OperationType.CREATE, path)))
        }
    }

    suspend fun updateComment(
        episodeKey: String,
        commentId: String,
        text: String,
        spoiler: Boolean
    ): Result<Unit> {
        val cleanText = text.trim()
        if (cleanText.isEmpty() || cleanText.length > 500) {
            return Result.failure(IllegalArgumentException("El comentario debe tener entre 1 y 500 caracteres."))
        }
        val path = "episodes/$episodeKey/comments/$commentId"
        return try {
            val updates = hashMapOf<String, Any>(
                "text" to cleanText,
                "spoiler" to spoiler,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            commentsCollection(episodeKey).document(commentId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception(handleFirestoreError(e, OperationType.UPDATE, path)))
        }
    }

    suspend fun deleteComment(
        episodeKey: String,
        commentId: String
    ): Result<Unit> {
        val path = "episodes/$episodeKey/comments/$commentId"
        return try {
            commentsCollection(episodeKey).document(commentId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception(handleFirestoreError(e, OperationType.DELETE, path)))
        }
    }
}
