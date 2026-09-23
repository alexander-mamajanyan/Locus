package com.locus.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.locus.app.data.LocusRepository
import com.locus.app.model.MathBuild
import com.locus.app.ui.components.MathCanvas
import com.locus.app.ui.theme.DisplayFont
import com.locus.app.ui.util.colorForUsername
import com.locus.app.ui.util.toShortString

@Composable
fun ProfileScreen(username: String, onBack: (() -> Unit)?, onOpenThread: () -> Unit) {
    val isMe = username == LocusRepository.currentUsername
    val profile = LocusRepository.profileFor(username)
    val builds = LocusRepository.feed.filter { it.username == username }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (isMe) "Profile" else "@$username") },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            },
            actions = {
                if (isMe) {
                    IconButton(onClick = { }) {
                        Icon(Icons.Filled.Menu, contentDescription = "Settings")
                    }
                }
            }
        )

        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(72.dp).clip(CircleShape).background(colorForUsername(username)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        username.take(2).uppercase(),
                        color = Color.White,
                        fontSize = 24.sp,
                        fontFamily = DisplayFont,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.width(16.dp))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceAround) {
                    StatColumn(profile.builds.toShortString(), "Builds")
                    StatColumn(profile.followers.toShortString(), "Followers")
                    StatColumn(profile.following.toShortString(), "Following")
                }
            }

            Text(
                "@$username",
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

            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isMe) {
                    OutlinedButton(onClick = { }, modifier = Modifier.weight(1f)) { Text("Edit profile") }
                    OutlinedButton(onClick = { }, modifier = Modifier.weight(1f)) { Text("Share") }
                } else {
                    Button(
                        onClick = { LocusRepository.toggleFollow(username) },
                        modifier = Modifier.weight(1f),
                        colors = if (profile.isFollowing) ButtonDefaults.outlinedButtonColors() else ButtonDefaults.buttonColors()
                    ) {
                        Text(if (profile.isFollowing) "Following" else "Follow")
                    }
                    OutlinedButton(onClick = onOpenThread, modifier = Modifier.weight(1f)) { Text("Message") }
                }
            }
        }

        HorizontalDivider()

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(builds, key = { it.id }) { build ->
                GridThumb(build)
            }
        }
    }
}

@Composable
private fun StatColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Text(label, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
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
            aspectRatioValue = 1f,
            modifier = Modifier.fillMaxSize()
        )
    }
}
