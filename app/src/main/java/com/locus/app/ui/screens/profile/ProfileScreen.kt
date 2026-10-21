package com.locus.app.ui.screens.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.locus.app.data.LocusRepository
import com.locus.app.model.MathBuild
import com.locus.app.model.UserProfile
import com.locus.app.ui.components.MathCanvas
import com.locus.app.ui.components.ProfileAvatar
import com.locus.app.ui.util.toShortString

private enum class ProfileSection { POSTS, REPOSTS, SAVED }

@Composable
fun ProfileScreen(
    userIdentity: String,
    onBack: (() -> Unit)?,
    onOpenThread: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val profile = LocusRepository.profileFor(userIdentity)
    val isMe = userIdentity == LocusRepository.currentUid || profile.uid == LocusRepository.currentUid
    val uid = profile.uid.ifBlank { userIdentity }
    var section by remember(userIdentity) { mutableStateOf(ProfileSection.POSTS) }
    var repostIds by remember(userIdentity) { mutableStateOf(emptySet<String>()) }
    var showSettings by remember { mutableStateOf(false) }
    var showEditProfile by remember { mutableStateOf(false) }
    var passwordMessage by remember { mutableStateOf<String?>(null) }
    var isSendingReset by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var selectedRelation by remember(userIdentity) { mutableStateOf<String?>(null) }
    var relatedProfiles by remember(userIdentity) { mutableStateOf(emptyList<UserProfile>()) }
    var isLoadingRelations by remember(userIdentity) { mutableStateOf(false) }

    DisposableEffect(uid) {
        if (uid.isBlank()) {
            onDispose { }
        } else {
            val listener = LocusRepository.listenToUserPostIds(
                userId = uid,
                collection = "reposts",
                onPostIds = { repostIds = it },
                onError = { LocusRepository.reportProfileError(it) }
            )
            onDispose { listener.remove() }
        }
    }
    DisposableEffect(uid, selectedRelation) {
        val relation = selectedRelation
        if (relation == null) {
            onDispose { }
        } else {
            val listener = LocusRepository.listenToRelatedProfiles(
                userId = uid,
                relation = relation,
                onProfiles = {
                    relatedProfiles = it
                    isLoadingRelations = false
                },
                onError = {
                    isLoadingRelations = false
                    LocusRepository.reportProfileError(it)
                }
            )
            onDispose { listener.remove() }
        }
    }

    val ownedPosts = LocusRepository.feed.filter { build ->
        if (profile.uid.isNotBlank()) {
            build.ownerId == profile.uid || (build.ownerId.isBlank() && build.username == profile.username)
        } else {
            build.username == profile.username
        }
    }
    val reposts = LocusRepository.feed.filter { it.id in repostIds }
    val visibleBuilds = when (section) {
        ProfileSection.POSTS -> ownedPosts
        ProfileSection.REPOSTS -> reposts
        ProfileSection.SAVED -> if (isMe) LocusRepository.savedBuilds else emptyList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            section == ProfileSection.SAVED -> "Saved posts"
                            isMe -> "Profile"
                            else -> "@${profile.username}"
                        }
                    )
                },
                navigationIcon = {
                    if (section == ProfileSection.SAVED) {
                        IconButton(onClick = { section = ProfileSection.POSTS }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back to profile")
                        }
                    } else if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (isMe && section != ProfileSection.SAVED) {
                        IconButton(onClick = { section = ProfileSection.SAVED }) {
                            Icon(Icons.Filled.Bookmark, contentDescription = "Saved posts")
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Settings")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (section != ProfileSection.SAVED) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProfileAvatar(
                            username = profile.username,
                            photoUrl = profile.photoUrl,
                            size = 76.dp
                        )
                        Spacer(Modifier.width(16.dp))
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceAround) {
                            StatColumn(profile.builds.toShortString(), "Builds")
                            StatColumn(profile.followers.toShortString(), "Followers") {
                                relatedProfiles = emptyList()
                                isLoadingRelations = true
                                selectedRelation = "followers"
                            }
                            StatColumn(profile.following.toShortString(), "Following") {
                                relatedProfiles = emptyList()
                                isLoadingRelations = true
                                selectedRelation = "following"
                            }
                        }
                    }
                    Text(
                        "@${profile.username}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                    if (profile.bio.isNotBlank()) {
                        Text(
                            profile.bio,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    LocusRepository.socialError?.let { error ->
                        Text(
                            error,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    if (!isMe) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { LocusRepository.toggleFollow(userIdentity) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (profile.isFollowing) "Following" else "Follow")
                            }
                            Button(onClick = onOpenThread, modifier = Modifier.weight(1f)) {
                                Text("Message")
                            }
                        }
                    }
                }

                if (isMe) {
                    ScrollableTabRow(
                        selectedTabIndex = if (section == ProfileSection.REPOSTS) 1 else 0,
                        edgePadding = 0.dp
                    ) {
                        Tab(
                            selected = section == ProfileSection.POSTS,
                            onClick = { section = ProfileSection.POSTS },
                            text = { Text("Posts") }
                        )
                        Tab(
                            selected = section == ProfileSection.REPOSTS,
                            onClick = { section = ProfileSection.REPOSTS },
                            text = { Text("Reposts") }
                        )
                    }
                } else {
                    ScrollableTabRow(
                        selectedTabIndex = if (section == ProfileSection.REPOSTS) 1 else 0,
                        edgePadding = 0.dp
                    ) {
                        Tab(
                            selected = section == ProfileSection.POSTS,
                            onClick = { section = ProfileSection.POSTS },
                            text = { Text("Posts") }
                        )
                        Tab(
                            selected = section == ProfileSection.REPOSTS,
                            onClick = { section = ProfileSection.REPOSTS },
                            text = { Text("Reposts") }
                        )
                    }
                }
            } else {
                Text(
                    "Only you can see posts you saved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
            HorizontalDivider()
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (visibleBuilds.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            when (section) {
                                ProfileSection.POSTS -> "No posts yet."
                                ProfileSection.REPOSTS -> "No reposts yet."
                                ProfileSection.SAVED -> "No saved posts yet."
                            },
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                        )
                    }
                }
                items(visibleBuilds.distinctBy { it.id }, key = { it.id }) { build ->
                    GridThumb(build)
                }
            }
        }
    }

    if (showSettings) {
        ProfileSettingsSheet(
            email = LocusRepository.currentEmail.orEmpty(),
            passwordMessage = passwordMessage,
            isSendingReset = isSendingReset,
            onDismiss = { showSettings = false },
            onEditProfile = {
                showSettings = false
                showEditProfile = true
            },
            onSavedPosts = {
                showSettings = false
                section = ProfileSection.SAVED
            },
            onResetPassword = {
                isSendingReset = true
                passwordMessage = null
                LocusRepository.sendPasswordReset { error ->
                    isSendingReset = false
                    passwordMessage = error?.localizedMessage
                        ?: "Password reset link sent to your email."
                }
            },
            onSignOut = {
                showSettings = false
                showSignOutConfirm = true
            }
        )
    }
    selectedRelation?.let { relation ->
        ProfileRelationsSheet(
            title = if (relation == "followers") "Followers" else "Following",
            profiles = relatedProfiles,
            isLoading = isLoadingRelations,
            onDismiss = { selectedRelation = null },
            onOpenProfile = {
                selectedRelation = null
                onOpenProfile(it)
            }
        )
    }
    if (showEditProfile) {
        EditProfileSheet(
            username = profile.username,
            bio = profile.bio,
            photoUrl = profile.photoUrl,
            onDismiss = { showEditProfile = false },
            onSave = { username, bio, selectedPhoto, onComplete ->
                if (selectedPhoto == null) {
                    LocusRepository.updateProfile(username, bio, profile.photoUrl, onComplete)
                } else {
                    LocusRepository.uploadProfilePhoto(selectedPhoto) { uploadedUrl, error ->
                        if (error != null || uploadedUrl == null) {
                            onComplete(error ?: IllegalStateException("Couldn't upload profile photo."))
                        } else {
                            LocusRepository.updateProfile(username, bio, uploadedUrl, onComplete)
                        }
                    }
                }
            }
        )
    }
    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("Sign out?") },
            text = { Text("You can sign back in any time.") },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutConfirm = false
                    LocusRepository.signOut()
                    onSignOut()
                }) { Text("Sign out") }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ProfileSettingsSheet(
    email: String,
    passwordMessage: String?,
    isSendingReset: Boolean,
    onDismiss: () -> Unit,
    onEditProfile: () -> Unit,
    onSavedPosts: () -> Unit,
    onResetPassword: () -> Unit,
    onSignOut: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Settings", style = MaterialTheme.typography.titleLarge)
            Text(email, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onEditProfile, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Edit, contentDescription = null)
                Text("  Edit profile")
            }
            TextButton(onClick = onSavedPosts, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Bookmark, contentDescription = null)
                Text("  Saved posts")
            }
            TextButton(
                onClick = onResetPassword,
                enabled = !isSendingReset,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSendingReset) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Send password reset email")
                }
            }
            passwordMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
            TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                Text("Sign out", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun EditProfileSheet(
    username: String,
    bio: String,
    photoUrl: String,
    onDismiss: () -> Unit,
    onSave: (String, String, Uri?, (Exception?) -> Unit) -> Unit
) {
    var usernameDraft by remember(username) { mutableStateOf(username) }
    var bioDraft by remember(bio) { mutableStateOf(bio) }
    var selectedPhoto by remember { mutableStateOf<Uri?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        selectedPhoto = it
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Edit profile", style = MaterialTheme.typography.titleLarge)
            ProfileAvatar(
                username = usernameDraft.ifBlank { username },
                photoUrl = selectedPhoto?.toString() ?: photoUrl,
                size = 88.dp,
                modifier = Modifier.clickable { imagePicker.launch("image/*") }
            )
            TextButton(onClick = { imagePicker.launch("image/*") }) {
                Text("Change profile photo")
            }
            OutlinedTextField(
                value = usernameDraft,
                onValueChange = { usernameDraft = it.take(20) },
                label = { Text("Username") },
                supportingText = { Text("3–20 letters, numbers, or underscores") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = bioDraft,
                onValueChange = { bioDraft = it.take(160) },
                label = { Text("Bio") },
                supportingText = { Text("${bioDraft.length}/160") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = {
                    isSaving = true
                    errorMessage = null
                    onSave(usernameDraft, bioDraft, selectedPhoto) { error ->
                        isSaving = false
                        if (error == null) onDismiss()
                        else errorMessage = error.localizedMessage ?: "Couldn't save profile."
                    }
                },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Save changes")
            }
        }
    }
}

@Composable
private fun StatColumn(value: String, label: String, onClick: (() -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .padding(4.dp)
    ) {
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Text(label, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
    }
}

@Composable
private fun ProfileRelationsSheet(
    title: String,
    profiles: List<UserProfile>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onOpenProfile: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            when {
                isLoading -> CircularProgressIndicator(Modifier.padding(vertical = 24.dp))
                profiles.isEmpty() -> Text(
                    "No users to show yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
                else -> profiles.forEach { profile ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenProfile(profile.uid) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProfileAvatar(profile.username, profile.photoUrl)
                        Text(
                            "@${profile.username}",
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GridThumb(build: MathBuild) {
    Box(Modifier.aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant)) {
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
            aspectRatioValue = 1f,
            modifier = Modifier.fillMaxSize()
        )
    }
}
