package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.R
import com.example.data.authStateFlow
import com.example.model.EpisodeComment
import com.example.model.buildEpisodeCommentsKey
import com.example.ui.viewmodel.CommentThread
import com.example.ui.viewmodel.CommentsUiState
import com.example.ui.viewmodel.EpisodeCommentsViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeCommentsSheet(
    providerId: String,
    slug: String,
    animeTitle: String,
    episodeNumber: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val authFlow = remember { Firebase.auth.authStateFlow() }
    val currentUser by authFlow.collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)
    val episodeKey = remember(providerId, slug, episodeNumber) {
        buildEpisodeCommentsKey(providerId, slug, episodeNumber)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Forum,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "${stringResource(R.string.comments_title)} · Ep. $episodeNumber",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = animeTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val signedUser = currentUser
                if (signedUser != null) {
                    val coroutineScope = rememberCoroutineScope()
                    val credentialManager = remember(context) { CredentialManager.create(context) }
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                GoogleAuthUiHelper.signOut(credentialManager)
                            }
                        },
                        modifier = Modifier.testTag("comments_signout_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.comments_signout_button))
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            val user = currentUser
            if (user == null) {
                CommentsGoogleSignInCard()
            } else {
                key(user.uid) {
                    val vm: EpisodeCommentsViewModel = viewModel(
                        key = "${user.uid}_$episodeKey",
                        factory = EpisodeCommentsViewModel.Factory(
                            context = context,
                            episodeKey = episodeKey,
                            animeTitle = animeTitle,
                            episodeNumber = episodeNumber
                        )
                    )
                    AuthenticatedEpisodeCommentsContent(
                        user = user,
                        viewModel = vm
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentsGoogleSignInCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var isSigningIn by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp)
                )
            }
            Text(
                text = stringResource(R.string.comments_signin_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.comments_signin_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AnimatedVisibility(visible = errorMessage != null, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    text = errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                enabled = !isSigningIn,
                onClick = {
                    scope.launch {
                        isSigningIn = true
                        errorMessage = null
                        when (val outcome = GoogleAuthUiHelper.signInInteractive(context, credentialManager)) {
                            is GoogleSignInOutcome.Success -> Unit
                            GoogleSignInOutcome.Cancelled -> Unit
                            is GoogleSignInOutcome.Error -> errorMessage = outcome.message
                        }
                        isSigningIn = false
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("comments_google_signin_button")
            ) {
                if (isSigningIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = stringResource(R.string.comments_signin_button),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedEpisodeCommentsContent(
    user: FirebaseUser,
    viewModel: EpisodeCommentsViewModel
) {
    val commentsState by viewModel.commentsState.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    val replyTarget by viewModel.replyTarget.collectAsStateWithLifecycle()

    var draftText by rememberSaveable { mutableStateOf("") }
    var isSpoiler by rememberSaveable { mutableStateOf(false) }
    var editingComment by remember { mutableStateOf<EpisodeComment?>(null) }
    var deletingComment by remember { mutableStateOf<EpisodeComment?>(null) }

    fun appendMention(authorName: String) {
        val handle = "@${authorName.trim().replace(" ", "_")}"
        if (draftText.contains(handle, ignoreCase = true)) return
        val prefix = if (draftText.isBlank() || draftText.endsWith(" ")) "" else " "
        val next = "$draftText$prefix$handle "
        if (next.length <= 500) {
            draftText = next
        }
    }

    editingComment?.let { target ->
        EditCommentDialog(
            initialText = target.text,
            initialSpoiler = target.spoiler,
            isSubmitting = isSubmitting,
            onDismiss = { editingComment = null },
            onSave = { updatedText, updatedSpoiler ->
                viewModel.updateComment(target.id, updatedText, updatedSpoiler) {
                    editingComment = null
                }
            }
        )
    }

    deletingComment?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingComment = null },
            title = { Text("¿Eliminar comentario?") },
            text = { Text("Esta acción quitará tu comentario del episodio.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteComment(target.id)
                        deletingComment = null
                    }
                ) {
                    Text(
                        text = stringResource(R.string.comments_delete_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingComment = null }) {
                    Text(stringResource(R.string.comments_cancel))
                }
            }
        )
    }

    // Comment composer with Reply-To Banner + @Mention quick suggestions
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(visible = replyTarget != null) {
                replyTarget?.let { target ->
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("comments_reply_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Reply,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.comments_replying_to, target.authorName),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = viewModel::cancelReply,
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("comments_cancel_reply")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.comments_cancel),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = draftText,
                onValueChange = { next ->
                    if (next.length <= 500) {
                        draftText = next
                        viewModel.clearActionError()
                    }
                },
                placeholder = {
                    Text(
                        text = if (replyTarget != null) {
                            stringResource(R.string.comments_reply_placeholder)
                        } else {
                            stringResource(R.string.comments_input_placeholder)
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                maxLines = 4,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("comments_input_field")
            )

            // Quick @mention suggestions when typing '@' or when authors exist in thread
            val mentionSuggestions = (commentsState as? CommentsUiState.Success)?.mentionSuggestions.orEmpty()
            val showMentionStrip = draftText.endsWith("@") || (replyTarget == null && mentionSuggestions.isNotEmpty() && draftText.contains("@"))
            AnimatedVisibility(visible = showMentionStrip && mentionSuggestions.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(mentionSuggestions) { author ->
                        Surface(
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
                            shape = CircleShape,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    val cleanHandle = author.trim().replace(" ", "_")
                                    val base = if (draftText.endsWith("@")) draftText.dropLast(1) else draftText
                                    val candidate = "$base@$cleanHandle "
                                    if (candidate.length <= 500) {
                                        draftText = candidate
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AlternateEmail,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = author,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isSpoiler,
                        onClick = { isSpoiler = !isSpoiler },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.comments_spoiler_toggle),
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f),
                            selectedLabelColor = MaterialTheme.colorScheme.tertiary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.tertiary
                        ),
                        modifier = Modifier.testTag("comments_spoiler_chip")
                    )
                    Text(
                        text = "${draftText.length}/500",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    enabled = draftText.isNotBlank() && !isSubmitting,
                    onClick = {
                        viewModel.submitComment(user, draftText, isSpoiler) {
                            draftText = ""
                            isSpoiler = false
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("comments_send_button")
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.comments_send))
                    }
                }
            }

            AnimatedVisibility(visible = actionError != null) {
                Text(
                    text = actionError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    when (val state = commentsState) {
        is CommentsUiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 28.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        is CommentsUiState.Error -> {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
        is CommentsUiState.Success -> {
            if (state.threads.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChatBubbleOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        modifier = Modifier.size(34.dp)
                    )
                    Text(
                        text = stringResource(R.string.comments_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.comments_empty_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 370.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.threads, key = { it.root.id }) { thread ->
                        CommentThreadSection(
                            thread = thread,
                            currentUserUid = user.uid,
                            onReply = { targetComment ->
                                viewModel.startReply(targetComment)
                                appendMention(targetComment.authorName)
                            },
                            onMention = { authorName ->
                                appendMention(authorName)
                            },
                            onEdit = { editingComment = it },
                            onDelete = { deletingComment = it }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentThreadSection(
    thread: CommentThread,
    currentUserUid: String,
    onReply: (EpisodeComment) -> Unit,
    onMention: (String) -> Unit,
    onEdit: (EpisodeComment) -> Unit,
    onDelete: (EpisodeComment) -> Unit
) {
    var expanded by rememberSaveable(thread.root.id) { mutableStateOf(false) }
    val totalReplies = thread.replies.size
    val visibleReplies = if (expanded || totalReplies <= 2) {
        thread.replies
    } else {
        thread.replies.take(2)
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CommentCard(
            comment = thread.root,
            isOwnComment = thread.root.userId == currentUserUid,
            isReply = false,
            replyCount = totalReplies,
            onReply = { onReply(thread.root) },
            onMention = { onMention(thread.root.authorName) },
            onEdit = { onEdit(thread.root) },
            onDelete = { onDelete(thread.root) }
        )

        if (visibleReplies.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(start = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.32f))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    visibleReplies.forEach { reply ->
                        CommentCard(
                            comment = reply,
                            isOwnComment = reply.userId == currentUserUid,
                            isReply = true,
                            replyCount = 0,
                            onReply = { onReply(reply) },
                            onMention = { onMention(reply.authorName) },
                            onEdit = { onEdit(reply) },
                            onDelete = { onDelete(reply) }
                        )
                    }
                    if (totalReplies > 2) {
                        TextButton(
                            onClick = { expanded = !expanded },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("thread_toggle_${thread.root.id}")
                        ) {
                            Text(
                                text = if (expanded) {
                                    stringResource(R.string.comments_hide_replies)
                                } else {
                                    stringResource(R.string.comments_show_more_replies, totalReplies - 2)
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentCard(
    comment: EpisodeComment,
    isOwnComment: Boolean,
    isReply: Boolean,
    replyCount: Int,
    onReply: () -> Unit,
    onMention: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var spoilerRevealed by remember(comment.id, comment.spoiler) {
        mutableStateOf(!comment.spoiler)
    }

    val createdMs = comment.createdAt?.toDate()?.time ?: 0L
    val updatedMs = comment.updatedAt?.toDate()?.time ?: 0L
    val wasEdited = updatedMs > createdMs + 5_000L && createdMs > 0L

    Surface(
        color = if (isReply) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
        },
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isOwnComment) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("comment_card_${comment.id}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    val avatarSize = if (isReply) 28.dp else 34.dp
                    if (comment.authorAvatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = comment.authorAvatarUrl,
                            contentDescription = comment.authorName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(avatarSize)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(avatarSize)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = comment.authorName.firstOrNull()?.uppercase() ?: "O",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = comment.authorName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable(onClick = onMention)
                            )
                            if (comment.replyToAuthorName.isNotBlank()) {
                                Text(
                                    text = "▸ @${comment.replyToAuthorName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        val timeText = formatRelativeTimeSpanish(createdMs) +
                            if (wasEdited) " · editado" else ""
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isOwnComment) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("comment_edit_${comment.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = "Editar comentario",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("comment_delete_${comment.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Eliminar comentario",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }

            if (comment.spoiler && !spoilerRevealed) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { spoilerRevealed = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Visibility,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.comments_spoiler_warning),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                val mentionColor = MaterialTheme.colorScheme.primary
                val annotatedBody = remember(comment.text, mentionColor) {
                    buildMentionAnnotatedText(comment.text, mentionColor)
                }
                Text(
                    text = annotatedBody,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Reply + Mention action bar
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onReply)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .testTag("comment_reply_${comment.id}")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Reply,
                        contentDescription = stringResource(R.string.comments_reply_action),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (replyCount > 0) {
                            "${stringResource(R.string.comments_reply_action)} ($replyCount)"
                        } else {
                            stringResource(R.string.comments_reply_action)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onMention)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .testTag("comment_mention_${comment.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AlternateEmail,
                        contentDescription = stringResource(R.string.comments_mention_action),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = stringResource(R.string.comments_mention_action),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private val MENTION_REGEX = Regex("""@[A-Za-z0-9_.\-\p{L}]+""")

private fun buildMentionAnnotatedText(
    text: String,
    mentionColor: Color
): AnnotatedString = buildAnnotatedString {
    append(text)
    MENTION_REGEX.findAll(text).forEach { match ->
        addStyle(
            style = SpanStyle(
                color = mentionColor,
                fontWeight = FontWeight.Bold
            ),
            start = match.range.first,
            end = match.range.last + 1
        )
    }
}

@Composable
private fun EditCommentDialog(
    initialText: String,
    initialSpoiler: Boolean,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Boolean) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    var spoiler by remember { mutableStateOf(initialSpoiler) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.comments_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 500) text = it },
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                FilterChip(
                    selected = spoiler,
                    onClick = { spoiler = !spoiler },
                    label = { Text(stringResource(R.string.comments_spoiler_toggle)) }
                )
            }
        },
        confirmButton = {
            Button(
                enabled = text.isNotBlank() && !isSubmitting,
                onClick = { onSave(text, spoiler) }
            ) {
                Text(stringResource(R.string.comments_save))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.comments_cancel))
            }
        }
    )
}

private fun formatRelativeTimeSpanish(timeMillis: Long): String {
    if (timeMillis <= 0L) return "Hace un momento"
    val diffSec = ((System.currentTimeMillis() - timeMillis) / 1000L).coerceAtLeast(0L)
    return when {
        diffSec < 60L -> "Hace un momento"
        diffSec < 3600L -> "Hace ${diffSec / 60L} min"
        diffSec < 86_400L -> "Hace ${diffSec / 3600L} h"
        diffSec < 2_592_000L -> "Hace ${diffSec / 86_400L} d"
        else -> "Hace ${diffSec / 2_592_000L} mes(es)"
    }
}
