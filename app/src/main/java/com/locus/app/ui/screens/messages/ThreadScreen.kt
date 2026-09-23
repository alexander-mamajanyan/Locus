package com.locus.app.ui.screens.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.locus.app.data.LocusRepository
import com.locus.app.model.ChatMessage
import com.locus.app.model.formulaText
import com.locus.app.ui.components.MathCanvas
import com.locus.app.ui.theme.DisplayFont
import com.locus.app.ui.theme.MathFont
import com.locus.app.ui.util.colorForUsername
import com.locus.app.ui.util.mathify

@Composable
fun ThreadScreen(username: String, onBack: () -> Unit) {
    val convo = remember(username) { LocusRepository.conversationFor(username) }
    var input by remember { mutableStateOf("") }
    var mathMode by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Box(
                modifier = Modifier.size(30.dp).clip(CircleShape).background(colorForUsername(username)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    username.take(2).uppercase(),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontFamily = DisplayFont,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.width(8.dp))
            Text("@$username", fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp)
        }
        HorizontalDivider()

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(convo.messages) { message ->
                MessageBubble(message)
            }
        }

        if (mathMode && input.isNotBlank()) {
            Text(
                mathify(input),
                fontFamily = MathFont,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 58.dp, bottom = 4.dp)
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        if (mathMode) MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { mathMode = !mathMode },
                contentAlignment = Alignment.Center
            ) {
                Text("\u03A3", fontFamily = MathFont, fontSize = 14.sp)
            }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text(if (mathMode) "y = x^2 + 3x - 1" else "Message...") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (input.isNotBlank()) {
                        LocusRepository.sendMessage(username, input.trim(), mathMode)
                        input = ""
                    }
                }
            ) {
                Icon(Icons.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val alignment = if (message.fromMe) Alignment.CenterEnd else Alignment.CenterStart
    Box(Modifier.fillMaxWidth(), contentAlignment = alignment) {
        val sharedId = message.sharedBuildId
        if (sharedId != null) {
            val build = LocusRepository.buildById(sharedId)
            if (build != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(0.72f)
                ) {
                    Column {
                        MathCanvas(
                            type = build.type,
                            params = build.params,
                            primaryValue = build.sliderValue,
                            shapes = build.shapes,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            build.formulaText(),
                            fontFamily = MathFont,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        } else {
            Surface(
                color = if (message.fromMe) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (message.fromMe) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(0.78f)
            ) {
                Text(
                    if (message.isMath) mathify(message.text ?: "") else (message.text ?: ""),
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                    fontFamily = if (message.isMath) MathFont else null,
                    fontSize = 13.5.sp
                )
            }
        }
    }
}
