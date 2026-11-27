package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.EpisodeCommentsRepository
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.model.EpisodeComment
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CommentThread(
    val root: EpisodeComment,
    val replies: List<EpisodeComment>
)

data class ReplyTarget(
    val rootCommentId: String,
    val replyToCommentId: String,
    val authorName: String
)

sealed interface CommentsUiState {
    data object Loading : CommentsUiState
    data class Success(
        val comments: List<EpisodeComment>,
        val threads: List<CommentThread>,
        val mentionSuggestions: List<String>
    ) : CommentsUiState
    data class Error(val message: String) : CommentsUiState
}

class EpisodeCommentsViewModel(
    private val repository: EpisodeCommentsRepository,
    private val episodeKey: String,
    private val animeTitle: String,
    private val episodeNumber: Int
) : ViewModel() {

    val commentsState: StateFlow<CommentsUiState> = repository.observeComments(episodeKey)
        .map<List<EpisodeComment>, CommentsUiState> { list ->
            val byId = list.associateBy { it.id }
            val repliesByParent = LinkedHashMap<String, MutableList<EpisodeComment>>()
            val roots = mutableListOf<EpisodeComment>()

            list.forEach { comment ->
                val parentId = comment.parentCommentId
                if (parentId.isNotBlank() && byId.containsKey(parentId) && parentId != comment.id) {
                    repliesByParent.getOrPut(parentId) { mutableListOf() }.add(comment)
                } else {
                    roots.add(comment)
                }
            }

            val threads = roots.map { root ->
                val sortedReplies = repliesByParent[root.id].orEmpty()
                    .sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
                CommentThread(root = root, replies = sortedReplies)
            }

            val authors = list.mapNotNull { c ->
                c.authorName.trim().takeIf { it.isNotBlank() }
            }.distinct().take(12)

            CommentsUiState.Success(
                comments = list,
                threads = threads,
                mentionSuggestions = authors
            )
        }
        .catch { e ->
            if (e is CancellationException) throw e
            val friendly = handleFirestoreError(
                error = e as? Exception ?: Exception(e),
                operationType = OperationType.LIST,
                path = "episodes/$episodeKey/comments"
            )
            emit(CommentsUiState.Error(friendly))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CommentsUiState.Loading
        )

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private val _replyTarget = MutableStateFlow<ReplyTarget?>(null)
    val replyTarget: StateFlow<ReplyTarget?> = _replyTarget.asStateFlow()

    fun clearActionError() {
        _actionError.value = null
    }

    fun startReply(comment: EpisodeComment) {
        val rootId = comment.parentCommentId.ifBlank { comment.id }
        _replyTarget.value = ReplyTarget(
            rootCommentId = rootId,
            replyToCommentId = comment.id,
            authorName = comment.authorName.ifBlank { "Otaku" }
        )
    }

    fun cancelReply() {
        _replyTarget.value = null
    }

    fun submitComment(
        user: FirebaseUser,
        text: String,
        spoiler: Boolean,
        onSuccess: () -> Unit
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > 500) {
            _actionError.value = "El comentario debe tener entre 1 y 500 caracteres."
            return
        }
        if (_isSubmitting.value) return
        val reply = _replyTarget.value
        viewModelScope.launch {
            _isSubmitting.value = true
            _actionError.value = null
            repository.postComment(
                episodeKey = episodeKey,
                animeTitle = animeTitle,
                episodeNumber = episodeNumber,
                user = user,
                text = trimmed,
                spoiler = spoiler,
                parentCommentId = reply?.rootCommentId.orEmpty(),
                replyToCommentId = reply?.replyToCommentId.orEmpty(),
                replyToAuthorName = reply?.authorName.orEmpty()
            ).onSuccess {
                _replyTarget.value = null
                onSuccess()
            }.onFailure { err ->
                _actionError.value = err.message ?: "No se pudo enviar el comentario."
            }
            _isSubmitting.value = false
        }
    }

    fun updateComment(
        commentId: String,
        text: String,
        spoiler: Boolean,
        onSuccess: () -> Unit
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > 500) {
            _actionError.value = "El comentario debe tener entre 1 y 500 caracteres."
            return
        }
        if (_isSubmitting.value) return
        viewModelScope.launch {
            _isSubmitting.value = true
            _actionError.value = null
            repository.updateComment(
                episodeKey = episodeKey,
                commentId = commentId,
                text = trimmed,
                spoiler = spoiler
            ).onSuccess {
                onSuccess()
            }.onFailure { err ->
                _actionError.value = err.message ?: "No se pudo actualizar el comentario."
            }
            _isSubmitting.value = false
        }
    }

    fun deleteComment(commentId: String) {
        viewModelScope.launch {
            _actionError.value = null
            repository.deleteComment(episodeKey, commentId)
                .onFailure { err ->
                    _actionError.value = err.message ?: "No se pudo eliminar el comentario."
                }
        }
    }

    class Factory(
        private val context: Context,
        private val episodeKey: String,
        private val animeTitle: String,
        private val episodeNumber: Int
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return EpisodeCommentsViewModel(
                repository = EpisodeCommentsRepository(context.applicationContext),
                episodeKey = episodeKey,
                animeTitle = animeTitle,
                episodeNumber = episodeNumber
            ) as T
        }
    }
}
