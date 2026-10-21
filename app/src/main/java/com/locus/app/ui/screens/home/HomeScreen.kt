package com.locus.app.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.locus.app.data.LocusRepository
import com.locus.app.model.*
import com.locus.app.ui.components.MathCanvas
import com.locus.app.ui.components.ProfileAvatar
import com.locus.app.ui.screens.comments.CommentsScreen
import com.locus.app.ui.theme.MathFont
import com.locus.app.ui.util.buildAnnotatedCaption
import com.locus.app.ui.util.toShortString

@Composable
fun HomeScreen(onOpenProfile: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var commentsPostId by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Locus", fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold) }
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search builds or @people") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        val matchingProfiles = if (query.isBlank()) {
            emptyList()
        } else {
            LocusRepository.profiles.filter {
                it.uid != LocusRepository.currentUid &&
                    it.username.contains(query.removePrefix("@"), ignoreCase = true)
            }
        }
        val visible = LocusRepository.feed.filter {
            query.isBlank() || it.username.contains(query, ignoreCase = true) ||
                it.caption.contains(query, ignoreCase = true)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (LocusRepository.socialError != null) {
                item {
                    Text(
                        LocusRepository.socialError.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { LocusRepository.clearSocialError() }
                            .padding(10.dp)
                    )
                }
            }
            items(matchingProfiles, key = { "user-${it.uid}" }) { profile ->
                UserSearchResult(
                    profile = profile,
                    onOpenProfile = { onOpenProfile(profile.uid) }
                )
            }
            if (visible.isEmpty() && matchingProfiles.isEmpty()) {
                item {
                    Text(
                        if (query.isBlank()) {
                            "No posts yet. Create the first build!"
                        } else {
                            "No matching posts or users."
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                }
            }
            items(visible, key = { it.id }) { build ->
                BuildCard(
                    build = build,
                    onOpenProfile = { onOpenProfile(build.ownerId.ifBlank { build.username }) },
                    onOpenComments = { commentsPostId = build.id }
                )
            }
        }
        commentsPostId?.let { postId ->
            CommentsScreen(postId = postId, onDismiss = { commentsPostId = null })
        }
    }
}

@Composable
private fun BuildCard(
    build: MathBuild,
    onOpenProfile: () -> Unit,
    onOpenComments: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(bottom = 8.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProfileAvatar(
                    username = build.username,
                    photoUrl = LocusRepository.profileFor(build.ownerId).photoUrl
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "@${build.username}",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            modifier = Modifier.clickable(onClick = onOpenProfile)
                        )
                        if (build.verified) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = "Verified",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Text(
                        "${build.time} ago",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            build.remixOf?.let { origin ->
                Text(
                    "remixed from @$origin",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier
                        .padding(horizontal = 13.dp)
                        .padding(bottom = 6.dp)
                )
            }

            Box(Modifier.padding(horizontal = 13.dp)) {
                MathCanvas(
                    type = build.type,
                    params = build.params,
                    primaryValue = build.sliderValue,
                    shapes = build.shapes,
                    expression = build.expression,
                    geometryPoints = build.geometryPoints,
                    geometryObjects = build.geometryObjects,
                    canvasTexts = build.canvasTexts,
                    canvasBoxes = build.canvasBoxes,
                    showBaseFunction = build.showBaseFunction,
                    modifier = Modifier.clip(MaterialTheme.shapes.medium)
                )
                if (build.canvasBoxes.isEmpty() && build.showBaseFunction) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                        shape = MaterialTheme.shapes.small,
                        tonalElevation = 2.dp
                    ) {
                        Text(
                            build.formulaText(),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontFamily = MathFont,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (build.type != BuildType.FUNCTION) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 13.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(sliderLabelFor(build.type), fontFamily = MathFont, fontSize = 12.sp)
                    Slider(
                        value = build.sliderValue,
                        onValueChange = { build.sliderValue = it },
                        valueRange = sliderRangeFor(build.type),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    Text(
                        "%.1f".format(build.sliderValue),
                        fontFamily = MathFont,
                        fontSize = 12.sp,
                        modifier = Modifier.width(40.dp)
                    )
                }
            }

            Text(
                buildAnnotatedCaption(build.username, build.caption),
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 4.dp),
                fontSize = 13.5.sp
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionButton(
                    icon = if (build.liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    label = build.likes.toShortString(),
                    tint = if (build.liked) MaterialTheme.colorScheme.error else LocalContentColor.current,
                    onClick = { LocusRepository.toggleLike(build) }
                )
                ActionButton(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    label = build.comments.toShortString(),
                    onClick = onOpenComments
                )
                ActionButton(
                    icon = Icons.Filled.Repeat,
                    label = build.reposts.toShortString(),
                    tint = if (build.reposted) MaterialTheme.colorScheme.primary
                    else LocalContentColor.current,
                    onClick = { LocusRepository.toggleRepost(build) }
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { LocusRepository.toggleSave(build) }) {
                    Icon(
                        if (build.saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (build.saved) "Remove saved post" else "Save post",
                        tint = if (build.saved) MaterialTheme.colorScheme.secondary else LocalContentColor.current
                    )
                }
            }
        }
    }
}

@Composable
private fun UserSearchResult(profile: UserProfile, onOpenProfile: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenProfile)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileAvatar(profile.username, profile.photoUrl)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("@${profile.username}", fontWeight = FontWeight.SemiBold)
                Text(
                    "${profile.followers} followers",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            OutlinedButton(onClick = {
                LocusRepository.toggleFollow(profile.uid)
            }) {
                Text(if (profile.isFollowing) "Following" else "Follow")
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    tint: Color = LocalContentColor.current,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, fontFamily = MathFont, fontSize = 12.sp, color = tint)
    }
}
