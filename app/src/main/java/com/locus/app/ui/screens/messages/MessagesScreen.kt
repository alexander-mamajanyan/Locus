package com.locus.app.ui.screens.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.locus.app.model.Conversation
import com.locus.app.ui.theme.DisplayFont
import com.locus.app.ui.util.colorForUsername

@Composable
fun MessagesScreen(onOpenThread: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Messages") })
        LazyColumn(Modifier.fillMaxSize()) {
            items(LocusRepository.conversations, key = { it.id }) { convo ->
                ConversationRow(convo, onClick = { onOpenThread(convo.username) })
            }
        }
    }
}

@Composable
private fun ConversationRow(convo: Conversation, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(colorForUsername(convo.username)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                convo.username.take(2).uppercase(),
                color = Color.White,
                fontFamily = DisplayFont,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("@${convo.username}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    convo.time,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Text(
                convo.preview,
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                maxLines = 1
            )
        }
        if (convo.unread) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(9.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary))
        }
    }
}
