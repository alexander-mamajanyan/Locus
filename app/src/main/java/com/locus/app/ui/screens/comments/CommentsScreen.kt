package com.locus.app.ui.screens.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.locus.app.data.LocusRepository
import com.locus.app.model.PostComment
import com.locus.app.ui.util.colorForUsername

@Composable
fun CommentsScreen(postId: String, onDismiss: () -> Unit) {
    val comments = remember(postId) { mutableStateListOf<PostComment>() }
    var isLoading by remember(postId) { mutableStateOf(true) }
    var errorMessage by remember(postId) { mutableStateOf<String?>(null) }
    var commentText by remember(postId) { mutableStateOf("") }
    var isSending by remember(postId) { mutableStateOf(false) }
    var replyTarget by remember(postId) { mutableStateOf<PostComment?>(null) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val build = LocusRepository.feed.firstOrNull { it.id == postId }

    DisposableEffect(postId) {
        val registration = LocusRepository.listenToComments(
            postId = postId,
            onComments = { loaded ->
                comments.clear()
                comments.addAll(loaded)
                isLoading = false
                errorMessage = null
            },
            onError = { error ->
                isLoading = false
                errorMessage = error.localizedMessage ?: "Couldn't load comments."
            }
        )
        onDispose { registration.remove() }
    }
    LaunchedEffect(comments.size) {
        if (comments.isNotEmpty()) listState.animateScrollToItem(comments.lastIndex)
    }

    val sendComment = {
        val text = commentText.trim()
        if (text.isNotEmpty() && !isSending) {
            isSending = true
            errorMessage = null
            LocusRepository.addComment(postId, text, replyTarget?.id) { error ->
                isSending = false
                if (error == null) {
                    commentText = ""
                    replyTarget = null
                    keyboardController?.hide()
                } else {
                    errorMessage = error.localizedMessage ?: "Couldn't send comment."
                }
            }
        }
    }
    val displayedComments = comments
        .filter { it.parentId == null }
        .flatMap { comment ->
            listOf(comment) + comments.filter { it.parentId == comment.id }
        } + comments.filter { comment ->
            comment.parentId != null && comments.none { it.id == comment.parentId }
        }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(
                    Modifier
                        .padding(top = 12.dp, bottom = 16.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
                Text("Comments", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.size(12.dp))
            }
        }
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.82f)
        ) {
            if (build != null && build.caption.isNotBlank()) {
                Text(
                    text = "@${build.username}  ${build.caption}",
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            when {
                isLoading -> Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                comments.isEmpty() && errorMessage == null -> Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No comments yet. Start the conversation.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 20.dp,
                        vertical = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    items(displayedComments, key = { it.id }) { comment ->
                        CommentRow(
                            comment = comment,
                            isReply = comment.parentId != null,
                            onLike = {
                                LocusRepository.toggleCommentLike(postId, comment.id) { error ->
                                    if (error != null) {
                                        errorMessage =
                                            error.localizedMessage ?: "Couldn't like comment."
                                    }
                                }
                            },
                            onReply = {
                                replyTarget = comment.takeIf { it.parentId == null }
                                    ?: comments.firstOrNull { it.id == comment.parentId }
                                commentText = replyTarget?.let { "@${it.username} " }.orEmpty()
                            }
                        )
                    }
                }
            }
            errorMessage?.let { message ->
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    replyTarget?.let { target ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Replying to @${target.username}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f).padding(start = 12.dp, bottom = 4.dp)
                            )
                            TextButton(onClick = {
                                replyTarget = null
                                commentText = ""
                            }) {
                                Text("Cancel")
                            }
                        }
                    }
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { if (it.length <= 1000) commentText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(if (replyTarget == null) "Add a comment…" else "Write a reply…")
                        },
                        maxLines = 4,
                        shape = CircleShape,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendComment() })
                    )
                }
                TextButton(
                    onClick = { sendComment() },
                    enabled = commentText.isNotBlank() && !isSending,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Filled.Send, contentDescription = "Send comment")
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: PostComment,
    isReply: Boolean,
    onLike: () -> Unit,
    onReply: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (isReply) 40.dp else 0.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(colorForUsername(comment.username)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = comment.username.take(2).uppercase(),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("@${comment.username}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(
                    "  ·  ${comment.time}",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    fontSize = 12.sp
                )
            }
            Text(comment.text, fontSize = 14.sp, modifier = Modifier.padding(top = 3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onLike, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) {
                    Icon(
                        imageVector = if (comment.liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (comment.liked) "Unlike comment" else "Like comment",
                        tint = if (comment.liked) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    if (comment.likes > 0) {
                        Text(
                            comment.likes.toString(),
                            modifier = Modifier.padding(start = 4.dp),
                            fontSize = 12.sp
                        )
                    }
                }
                TextButton(
                    onClick = onReply,
                    enabled = !isReply,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
                ) {
                    Text("Reply", fontSize = 12.sp)
                }
                if (!isReply && comment.replies > 0) {
                    Text(
                        "${comment.replies} ${if (comment.replies == 1) "reply" else "replies"}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
