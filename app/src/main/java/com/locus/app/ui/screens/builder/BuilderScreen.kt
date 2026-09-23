package com.locus.app.ui.screens.builder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.locus.app.data.LocusRepository
import com.locus.app.model.*
import com.locus.app.ui.components.MathCanvas
import com.locus.app.ui.theme.MathFont

@Composable
fun BuilderScreen(onPosted: () -> Unit) {
    var type by remember { mutableStateOf(BuildType.SINE) }
    var a by remember { mutableStateOf(1f) }
    var b by remember { mutableStateOf(1.2f) }
    var c by remember { mutableStateOf(0f) }
    var r by remember { mutableStateOf(2f) }
    val shapes = remember { mutableStateListOf<ShapeType>() }
    var caption by remember { mutableStateOf("") }

    val params = BuildParams(a = a, b = b, c = c, r = r)
    val primaryValue = when (type) {
        BuildType.SINE, BuildType.QUADRATIC -> a
        BuildType.LINEAR -> b
        BuildType.CIRCLE -> r
    }
    val formula = when (type) {
        BuildType.SINE -> "y = %.1f\u00B7sin(%.1fx + %.1f)".format(a, b, c)
        BuildType.QUADRATIC -> "y = %.1fx\u00B2 + %.1fx + %.1f".format(a, b, c)
        BuildType.LINEAR -> "y = %.1fx + %.1f".format(a, b)
        BuildType.CIRCLE -> "x\u00B2 + y\u00B2 = %.1f\u00B2".format(r)
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("New build") })

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            MathCanvas(
                type = type,
                params = params,
                primaryValue = primaryValue,
                shapes = shapes,
                modifier = Modifier.clip(MaterialTheme.shapes.large)
            )

            Text(
                formula,
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                textAlign = TextAlign.Center,
                fontFamily = MathFont,
                fontSize = 15.sp
            )

            TypeSelector(selected = type, onSelect = { type = it })

            Spacer(Modifier.height(14.dp))

            when (type) {
                BuildType.SINE -> {
                    ParamSlider("a", a, 0.2f..3f) { a = it }
                    ParamSlider("b", b, 0.2f..3f) { b = it }
                    ParamSlider("c", c, -3f..3f) { c = it }
                }
                BuildType.LINEAR -> {
                    ParamSlider("a", a, -3f..3f) { a = it }
                    ParamSlider("b", b, -3f..3f) { b = it }
                }
                BuildType.QUADRATIC -> {
                    ParamSlider("a", a, -2f..2f) { a = it }
                    ParamSlider("b", b, -3f..3f) { b = it }
                    ParamSlider("c", c, -3f..3f) { c = it }
                }
                BuildType.CIRCLE -> {
                    ParamSlider("r", r, 0.5f..4f) { r = it }
                }
            }

            Text(
                "Add a shape to the frame",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { shapes.add(ShapeType.CIRCLE) }, label = { Text("Circle") })
                AssistChip(onClick = { shapes.add(ShapeType.TRIANGLE) }, label = { Text("Triangle") })
                AssistChip(onClick = { shapes.add(ShapeType.POINT) }, label = { Text("Point") })
            }

            if (shapes.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    shapes.forEachIndexed { index, shape ->
                        AssistChip(
                            onClick = { shapes.removeAt(index) },
                            label = { Text(shape.name.lowercase()) },
                            trailingIcon = {
                                Icon(Icons.Filled.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                placeholder = { Text("Say something about this build...") },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                minLines = 2
            )

            Button(
                onClick = {
                    val newBuild = MathBuild(
                        id = "u${System.currentTimeMillis()}",
                        username = LocusRepository.currentUsername,
                        time = "now",
                        caption = caption.ifBlank { "new build" },
                        type = type,
                        params = BuildParams(a, b, c, r),
                        shapes = shapes.toList()
                    )
                    LocusRepository.addBuild(newBuild)
                    caption = ""
                    shapes.clear()
                    onPosted()
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp)
            ) {
                Text("Post to Locus")
            }
        }
    }
}

@Composable
private fun TypeSelector(selected: BuildType, onSelect: (BuildType) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        BuildType.values().forEach { t ->
            val isSelected = selected == t
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(t) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    t.label(),
                    fontSize = 12.5.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun ParamSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, fontFamily = MathFont, fontSize = 14.sp, modifier = Modifier.width(20.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        Text("%.1f".format(value), fontFamily = MathFont, fontSize = 12.sp, modifier = Modifier.width(44.dp))
    }
}
