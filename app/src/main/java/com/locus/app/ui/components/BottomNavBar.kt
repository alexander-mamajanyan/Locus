package com.locus.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class LocusTab(val icon: ImageVector, val description: String) {
    Home(Icons.Filled.Home, "Home"),
    Messages(Icons.Filled.MailOutline, "Messages"),
    Builder(Icons.Filled.Add, "Create"),
    Profile(Icons.Filled.Person, "Profile")
}

@Composable
fun LocusBottomBar(current: LocusTab, onSelect: (LocusTab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(
            selected = current == LocusTab.Home,
            onClick = { onSelect(LocusTab.Home) },
            icon = { Icon(LocusTab.Home.icon, contentDescription = LocusTab.Home.description) }
        )
        NavigationBarItem(
            selected = current == LocusTab.Messages,
            onClick = { onSelect(LocusTab.Messages) },
            icon = { Icon(LocusTab.Messages.icon, contentDescription = LocusTab.Messages.description) }
        )
        NavigationBarItem(
            selected = false,
            onClick = { onSelect(LocusTab.Builder) },
            icon = {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        LocusTab.Builder.icon,
                        contentDescription = LocusTab.Builder.description,
                        tint = MaterialTheme.colorScheme.surface
                    )
                }
            }
        )
        NavigationBarItem(
            selected = current == LocusTab.Profile,
            onClick = { onSelect(LocusTab.Profile) },
            icon = { Icon(LocusTab.Profile.icon, contentDescription = LocusTab.Profile.description) }
        )
    }
}
