package com.locus.app.ui.components

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import com.locus.app.model.BuildParams
import com.locus.app.model.BuildType
import com.locus.app.model.CanvasBox
import com.locus.app.model.CanvasBoxType
import com.locus.app.model.CanvasText
import com.locus.app.model.GeometryObject
import com.locus.app.model.GeometryPoint
import com.locus.app.model.GeometryType
import com.locus.app.model.compileMathExpression
import com.locus.app.model.ShapeType
import java.util.Locale
import kotlin.math.min

private const val RANGE = 4.6f
private val pointLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = android.graphics.Color.WHITE
    textSize = 28f
    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
}
private val pointLabelHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = android.graphics.Color.BLACK
    textSize = 28f
    style = Paint.Style.STROKE
    strokeWidth = 4f
    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
}
private val annotationBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = android.graphics.Color.argb(210, 35, 42, 58)
}
private val annotationTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = android.graphics.Color.WHITE
    textSize = 34f
    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
}
private val boxContentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = android.graphics.Color.WHITE
    textSize = 28f
    textAlign = Paint.Align.CENTER
    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
}
private val featureMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = android.graphics.Color.WHITE
    style = Paint.Style.STROKE
    strokeWidth = 3f
}
private val featureDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = android.graphics.Color.WHITE
}

private data class Geo(val scale: Float, val originX: Float, val originY: Float)
private data class FunctionFeature(
    val id: String,
    val x: Float,
    val y: Float,
    val coordinates: String
)

private fun List<CanvasBox>.containsType(type: CanvasBoxType): Boolean =
    any { box -> box.type == type || box.children.containsType(type) }

private fun geometry(
    width: Float,
    height: Float,
    zoom: Float = 1f,
    offset: Offset = Offset.Zero,
    range: Float = RANGE
): Geo {
    val scale = min(width, height) / (range * 2f) * zoom
    return Geo(
        scale,
        width / 2f + offset.x,
        height / 2f + offset.y
    )
}

private fun Geo.toPx(x: Float, y: Float) = Offset(originX + x * scale, originY - y * scale)

private fun DrawScope.drawGrid(
    geo: Geo,
    gridColor: Color,
    axisColor: Color,
    range: Float = RANGE,
    infinite: Boolean = false
) {
    val firstX = if (infinite) kotlin.math.ceil(-geo.originX / geo.scale).toInt() else -range.toInt()
    val lastX = if (infinite) {
        kotlin.math.floor((size.width - geo.originX) / geo.scale).toInt()
    } else {
        range.toInt()
    }
    for (x in firstX..lastX) {
        val px = geo.originX + x * geo.scale
        drawLine(gridColor, Offset(px, 0f), Offset(px, size.height), strokeWidth = 1f)
    }
    val firstY = if (infinite) {
        kotlin.math.ceil((geo.originY - size.height) / geo.scale).toInt()
    } else {
        -range.toInt()
    }
    val lastY = if (infinite) kotlin.math.floor(geo.originY / geo.scale).toInt() else range.toInt()
    for (y in firstY..lastY) {
        val py = geo.originY - y * geo.scale
        drawLine(gridColor, Offset(0f, py), Offset(size.width, py), strokeWidth = 1f)
    }
    drawLine(axisColor, Offset(0f, geo.originY), Offset(size.width, geo.originY), strokeWidth = 1.6f)
    drawLine(axisColor, Offset(geo.originX, 0f), Offset(geo.originX, size.height), strokeWidth = 1.6f)
}

private fun functionFeatures(
    geo: Geo,
    width: Float,
    height: Float,
    fn: (Float) -> Float
): List<FunctionFeature> {
    if (geo.scale <= 0f || width <= 0f || height <= 0f) return emptyList()

    val minX = -geo.originX / geo.scale
    val maxX = (width - geo.originX) / geo.scale
    val visibleY = height / (2f * geo.scale)
    val sampleCount = (width / 2f).toInt().coerceAtLeast(2)
    val step = (maxX - minX) / sampleCount
    if (!step.isFinite() || step <= 0f) return emptyList()

    val xs = List(sampleCount + 1) { minX + it * step }
    val ys = xs.map { x -> fn(x).takeIf { it.isFinite() } }
    val features = mutableListOf<FunctionFeature>()

    fun coordinate(value: Float): String =
        if (kotlin.math.abs(value) < 0.005f) "0" else "%.2f".format(Locale.US, value)

    fun addFeature(x: Float, y: Float, label: String) {
        if (!x.isFinite() || !y.isFinite() || kotlin.math.abs(y) > visibleY) return
        val kind = label.substringBefore(" (")
        val featureId = "$kind:${coordinate(x)}"
        if (features.any {
                it.id == featureId &&
                    kotlin.math.abs(it.x - x) < step * 1.5f
            }
        ) {
            return
        }
        features += FunctionFeature(featureId, x, y, "(${label.substringAfter(" (")}")
    }

    for (index in 0 until sampleCount) {
        val leftY = ys[index] ?: continue
        val rightY = ys[index + 1] ?: continue
        if ((leftY == 0f && rightY != 0f) ||
            (rightY == 0f && leftY != 0f) ||
            (leftY != 0f && rightY != 0f && (leftY < 0f) != (rightY < 0f))
        ) {
            val rootX = if (leftY == 0f) {
                xs[index]
            } else {
                xs[index] + step * (leftY / (leftY - rightY))
            }
            addFeature(rootX, 0f, "x-int (${coordinate(rootX)}, 0)")
        }
    }

    if (minX <= 0f && maxX >= 0f) {
        val yIntercept = fn(0f)
        if (yIntercept.isFinite()) {
            addFeature(0f, yIntercept, "y-int (0, ${coordinate(yIntercept)})")
        }
    }

    for (index in 1 until sampleCount) {
        val previous = ys[index - 1] ?: continue
        val current = ys[index] ?: continue
        val next = ys[index + 1] ?: continue
        if (kotlin.math.abs(previous) > visibleY ||
            kotlin.math.abs(current) > visibleY ||
            kotlin.math.abs(next) > visibleY ||
            maxOf(
                kotlin.math.abs(previous),
                kotlin.math.abs(current),
                kotlin.math.abs(next)
            ) > visibleY * 0.8f
        ) continue

        val isMaximum = current > previous && current > next
        val isMinimum = current < previous && current < next
        if (!isMaximum && !isMinimum) continue

        val denominator = previous - 2f * current + next
        val fractionalOffset = if (denominator == 0f) 0f else {
            (0.5f * (previous - next) / denominator).coerceIn(-1f, 1f)
        }
        val extremumX = xs[index] + fractionalOffset * step
        val extremumY = fn(extremumX)
        if (extremumY.isFinite()) {
            val estimatedExtremumY = if (denominator == 0f) extremumY else {
                current - (next - previous) * (next - previous) / (8f * denominator)
            }
            if (isMinimum &&
                kotlin.math.abs(estimatedExtremumY) <= maxOf(
                    1e-6f,
                    kotlin.math.abs(denominator) * 1e-4f
                )
            ) {
                addFeature(extremumX, 0f, "x-int (${coordinate(extremumX)}, 0)")
                continue
            }
            val name = if (isMaximum) "max" else "min"
            addFeature(
                extremumX,
                extremumY,
                "$name (${coordinate(extremumX)}, ${coordinate(extremumY)})"
            )
        }
    }

    for (index in 2 until sampleCount) {
        val beforePrevious = ys[index - 2] ?: continue
        val previous = ys[index - 1] ?: continue
        val current = ys[index] ?: continue
        val next = ys[index + 1] ?: continue
        if (maxOf(
                kotlin.math.abs(beforePrevious),
                kotlin.math.abs(previous),
                kotlin.math.abs(current),
                kotlin.math.abs(next)
            ) > visibleY * 0.8f
        ) continue

        val previousCurvature = beforePrevious - 2f * previous + current
        val currentCurvature = previous - 2f * current + next
        if (previousCurvature == 0f ||
            currentCurvature == 0f ||
            (previousCurvature < 0f) == (currentCurvature < 0f)
        ) continue

        val fraction = previousCurvature / (previousCurvature - currentCurvature)
        val inflectionX = xs[index - 1] + fraction * step
        val inflectionY = fn(inflectionX)
        if (inflectionY.isFinite()) {
            addFeature(
                inflectionX,
                inflectionY,
                "inflection (${coordinate(inflectionX)}, ${coordinate(inflectionY)})"
            )
        }
    }

    return features
}

private fun DrawScope.drawFunctionFeatures(
    geo: Geo,
    features: List<FunctionFeature>,
    color: Color,
    selectedId: String?
) {
    val canvas = drawContext.canvas.nativeCanvas
    val markerColor = color.toArgb()
    features.forEach { feature ->
        val position = geo.toPx(feature.x, feature.y)
        featureMarkerPaint.color = markerColor
        featureDotPaint.color = markerColor
        canvas.drawCircle(position.x, position.y, 3.5f, featureDotPaint)
        if (feature.id == selectedId) {
            canvas.drawCircle(position.x, position.y, 8f, featureMarkerPaint)
        }
    }
}

private fun DrawScope.plotFunction(
    geo: Geo,
    color: Color,
    fn: (Float) -> Float,
    selectedFeatureId: String?,
    featureIdPrefix: String,
    featureWidth: Float = size.width,
    featureHeight: Float = size.height
) {
    val path = Path()
    var started = false
    var px = 0f
    while (px <= size.width) {
        val x = (px - geo.originX) / geo.scale
        val y = fn(x)
        val yPx = geo.originY - y * geo.scale
        if (!y.isFinite() || yPx < -size.height || yPx > size.height * 2f) {
            started = false
        } else {
            val point = geo.toPx(x, y)
            if (!started) {
                path.moveTo(point.x, point.y)
                started = true
            } else {
                path.lineTo(point.x, point.y)
            }
        }
        px += 2f
    }
    drawPath(path, color, style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawFunctionFeatures(
        geo,
        functionFeatures(geo, featureWidth, featureHeight, fn).map {
            it.copy(id = "$featureIdPrefix:${it.id}")
        },
        color,
        selectedFeatureId
    )
}

private data class FunctionFeatureHit(val id: String, val coordinates: String)

private fun findFunctionFeatureAt(
    position: Offset,
    geo: Geo,
    width: Float,
    height: Float,
    boxes: List<CanvasBox>,
    showBaseFunction: Boolean,
    type: BuildType,
    params: BuildParams,
    primaryValue: Float,
    expression: ((Double) -> Double)?
): FunctionFeatureHit? {
    val candidates = mutableListOf<Pair<Offset, FunctionFeature>>()

    if (showBaseFunction && !boxes.containsType(CanvasBoxType.GRAPH)) {
        val function: ((Float) -> Float)? = when (type) {
            BuildType.SINE -> { x: Float ->
                primaryValue * kotlin.math.sin(params.b * x + params.c)
            }
            BuildType.QUADRATIC -> { x: Float ->
                primaryValue * x * x + params.b * x + params.c
            }
            BuildType.LINEAR -> { x: Float -> params.a * x + primaryValue }
            BuildType.FUNCTION -> expression?.let { evaluate ->
                val evaluateFunction: (Float) -> Float = { x ->
                    evaluate(x.toDouble()).toFloat()
                }
                evaluateFunction
            }
            BuildType.CIRCLE -> null
        }
        function?.let { evaluator ->
            functionFeatures(geo, width, height, evaluator).forEach { feature ->
                candidates += geo.toPx(feature.x, feature.y) to feature.copy(
                    id = "base:${feature.id}"
                )
            }
        }
    }

    fun collectGraphFeatures(children: List<CanvasBox>) {
        children.forEach { box ->
            if (box.type == CanvasBoxType.CONTAINER) {
                collectGraphFeatures(box.children)
            } else if (box.type == CanvasBoxType.GRAPH) {
                val center = geo.toPx(box.x, box.y)
                val boxGeo = Geo(
                    min(box.width, box.height) * geo.scale / (RANGE * 2f),
                    center.x,
                    center.y
                )
                val evaluator = try {
                    compileMathExpression(box.content)
                } catch (_: IllegalArgumentException) {
                    null
                }
                evaluator?.let { expressionFunction ->
                    val features = functionFeatures(
                        boxGeo,
                        box.width * geo.scale,
                        box.height * geo.scale
                    ) { x -> expressionFunction(x.toDouble()).toFloat() }
                    val angle = Math.toRadians(box.rotation.toDouble())
                    features.forEach { feature ->
                        val local = Offset(
                            feature.x * boxGeo.scale,
                            -feature.y * boxGeo.scale
                        )
                        val rotated = Offset(
                            (local.x * kotlin.math.cos(angle) -
                                local.y * kotlin.math.sin(angle)).toFloat(),
                            (local.x * kotlin.math.sin(angle) +
                                local.y * kotlin.math.cos(angle)).toFloat()
                        )
                        candidates += (center + rotated) to feature.copy(
                            id = "${box.id}:${feature.id}"
                        )
                    }
                }
            }
        }
    }
    collectGraphFeatures(boxes)

    val (point, feature) = candidates.minByOrNull { (candidate, _) ->
        (candidate - position).getDistance()
    } ?: return null
    if ((point - position).getDistance() > 18f) return null
    return FunctionFeatureHit(feature.id, feature.coordinates)
}

private fun DrawScope.plotCircleShape(geo: Geo, radius: Float, color: Color, fill: Boolean = false) {
    val r = radius * geo.scale
    val center = Offset(geo.originX, geo.originY)
    if (fill) drawCircle(color.copy(alpha = 0.14f), r, center)
    drawCircle(color, r, center, style = Stroke(width = 4f))
}

private fun DrawScope.plotTriangleShape(geo: Geo, points: List<Pair<Float, Float>>, color: Color, fill: Boolean = false) {
    val path = Path()
    points.forEachIndexed { index, (x, y) ->
        val p = geo.toPx(x, y)
        if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    if (fill) drawPath(path, color.copy(alpha = 0.14f))
    drawPath(path, color, style = Stroke(width = 4f, join = StrokeJoin.Round))
}

private fun DrawScope.plotPointMark(geo: Geo, x: Float, y: Float, color: Color, radius: Float = 8f) {
    drawCircle(color, radius, geo.toPx(x, y))
}

private fun DrawScope.drawOverlayShape(geo: Geo, shape: ShapeType, color: Color) {
    when (shape) {
        ShapeType.CIRCLE -> plotCircleShape(geo, 1.3f, color, fill = true)
        ShapeType.TRIANGLE -> plotTriangleShape(geo, listOf(-1.4f to -1.1f, 1.4f to -1.1f, 0f to 1.6f), color, fill = true)
        ShapeType.POINT -> plotPointMark(geo, 0.6f, 0.9f, color)
    }
}

private fun DrawScope.drawGeometry(
    geo: Geo,
    points: List<GeometryPoint>,
    objects: List<GeometryObject>,
    color: Color,
    selectedPointId: String?
) {
    val pointsById = points.associateBy { it.id }
    objects.forEach { item ->
        val first = pointsById[item.firstPointId] ?: return@forEach
        val second = pointsById[item.secondPointId] ?: return@forEach
        val firstPx = geo.toPx(first.x, first.y)
        val secondPx = geo.toPx(second.x, second.y)
        when (item.type) {
            GeometryType.SEGMENT -> drawLine(color, firstPx, secondPx, strokeWidth = 4f)
            GeometryType.CIRCLE -> {
                val radius = (firstPx - secondPx).getDistance()
                drawCircle(color.copy(alpha = 0.12f), radius, firstPx)
                drawCircle(color, radius, firstPx, style = Stroke(width = 4f))
            }
        }
    }
    points.forEach { point ->
        val position = geo.toPx(point.x, point.y)
        if (point.id == selectedPointId) {
            drawCircle(Color.White, 17f, position)
        }
        drawCircle(Color.White, 13f, position)
        drawCircle(color, 9f, position)
        drawContext.canvas.nativeCanvas.drawText(
            point.id,
            position.x + 12f,
            position.y - 12f,
            pointLabelHaloPaint
        )
        drawContext.canvas.nativeCanvas.drawText(
            point.id,
            position.x + 12f,
            position.y - 12f,
            pointLabelPaint
        )
    }
}

private fun DrawScope.drawCanvasTexts(geo: Geo, texts: List<CanvasText>, selectedId: String?) {
    texts.forEach { annotation ->
        val position = geo.toPx(annotation.x, annotation.y)
        val textWidth = annotationTextPaint.measureText(annotation.text)
        val bounds = RectF(
            position.x,
            position.y - 38f,
            position.x + textWidth + 20f,
            position.y + 8f
        )
        if (annotation.id == selectedId) {
            val selectionBounds = RectF(bounds).apply { inset(-4f, -4f) }
            drawContext.canvas.nativeCanvas.drawRoundRect(
                selectionBounds,
                12f,
                12f,
                pointLabelHaloPaint
            )
        }
        drawContext.canvas.nativeCanvas.drawRoundRect(bounds, 10f, 10f, annotationBackgroundPaint)
        drawContext.canvas.nativeCanvas.drawText(
            annotation.text,
            position.x + 10f,
            position.y - 7f,
            annotationTextPaint
        )
    }
}

private fun DrawScope.drawCanvasBoxes(
    geo: Geo,
    boxes: List<CanvasBox>,
    points: List<GeometryPoint>,
    objects: List<GeometryObject>,
    selectedPointId: String?,
    selectedBoxIds: Set<String>,
    selectedFunctionFeatureId: String?,
    graphColor: Color,
    gridColor: Color,
    axisColor: Color,
    boxColor: Color,
    contentColor: Color
) {
    boxContentPaint.color = contentColor.toArgb()
    boxes.forEach { box ->
        val center = geo.toPx(box.x, box.y)
        val width = box.width * geo.scale
        val height = box.height * geo.scale
        val topLeft = Offset(center.x - width / 2f, center.y - height / 2f)
        val boxGeo = Geo(min(width, height) / (RANGE * 2f), center.x, center.y)
        if (box.type == CanvasBoxType.CONTAINER) {
            rotate(box.rotation, center) {
                drawRoundRect(
                    color = boxColor,
                    topLeft = topLeft,
                    size = Size(width, height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f)
                )
                drawRoundRect(
                    color = if (box.id in selectedBoxIds) graphColor else axisColor,
                    topLeft = topLeft,
                    size = Size(width, height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f),
                    style = Stroke(width = if (box.id in selectedBoxIds) 3f else 1.5f)
                )
                if (box.id in selectedBoxIds) {
                    val rotateHandle = Offset(center.x, center.y - height / 2f - 18f)
                    drawLine(axisColor, Offset(center.x, center.y - height / 2f), rotateHandle)
                    drawCircle(Color.White, 10f, rotateHandle)
                    drawCircle(graphColor, 7f, rotateHandle)
                    drawCircle(Color.White, 9f, Offset(center.x + width / 2f, center.y + height / 2f))
                    drawCircle(graphColor, 6f, Offset(center.x + width / 2f, center.y + height / 2f))
                }
            }
            drawCanvasBoxes(
                geo,
                box.children,
                points,
                objects,
                selectedPointId,
                selectedBoxIds,
                selectedFunctionFeatureId,
                graphColor,
                gridColor,
                axisColor,
                boxColor,
                contentColor
            )
            return@forEach
        }
        rotate(box.rotation, center) {
            drawRoundRect(
                color = boxColor,
                topLeft = topLeft,
                size = Size(width, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f)
            )
            drawRoundRect(
                color = if (box.id in selectedBoxIds) graphColor else axisColor,
                topLeft = topLeft,
                size = Size(width, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f),
                style = Stroke(width = if (box.id in selectedBoxIds) 3f else 1.5f)
            )
            clipRect(topLeft.x, topLeft.y, topLeft.x + width, topLeft.y + height) {
                when (box.type) {
                    CanvasBoxType.GRAPH -> {
                        drawGrid(boxGeo, gridColor, axisColor)
                        val evaluator = try {
                            compileMathExpression(box.content)
                        } catch (_: IllegalArgumentException) {
                            null
                        }
                        evaluator?.let { function ->
                            plotFunction(
                                boxGeo,
                                graphColor,
                                { x -> function(x.toDouble()).toFloat() },
                                selectedFunctionFeatureId,
                                box.id,
                                width,
                                height
                            )
                        }
                        val label = "y = ${box.content}"
                        val originalTextSize = boxContentPaint.textSize
                        boxContentPaint.textSize = originalTextSize * min(
                            1f,
                            (width - 16f).coerceAtLeast(1f) /
                                boxContentPaint.measureText(label).coerceAtLeast(1f)
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            label,
                            center.x,
                            center.y + height / 2f - 8f,
                            boxContentPaint
                        )
                        boxContentPaint.textSize = originalTextSize
                    }
                    CanvasBoxType.GEOMETRY -> {
                        drawGrid(boxGeo, gridColor, axisColor)
                        drawGeometry(boxGeo, points, objects, graphColor, selectedPointId)
                    }
                    CanvasBoxType.EXPRESSION, CanvasBoxType.TEXT -> {
                        val lines = box.content.split('\n')
                        val previousTextSize = boxContentPaint.textSize
                        val availableWidth = (width - 20f).coerceAtLeast(1f)
                        val widestLine = lines.maxOfOrNull {
                            boxContentPaint.measureText(it)
                        } ?: 1f
                        boxContentPaint.textSize = previousTextSize *
                            min(1f, availableWidth / widestLine)
                        val lineHeight = boxContentPaint.textSize * 1.2f
                        val startY = center.y - (lines.size - 1) * lineHeight / 2f +
                            boxContentPaint.textSize * 0.35f
                        lines.forEachIndexed { index, line ->
                            drawContext.canvas.nativeCanvas.drawText(
                                line,
                                center.x,
                                startY + index * lineHeight,
                                boxContentPaint
                            )
                        }
                        boxContentPaint.textSize = previousTextSize
                    }
                    CanvasBoxType.CONTAINER -> Unit
                }
            }
            if (box.id in selectedBoxIds) {
                val rotateHandle = Offset(center.x, center.y - height / 2f - 18f)
                drawLine(axisColor, Offset(center.x, center.y - height / 2f), rotateHandle)
                drawCircle(Color.White, 10f, rotateHandle)
                drawCircle(graphColor, 7f, rotateHandle)
                drawCircle(Color.White, 9f, Offset(center.x + width / 2f, center.y + height / 2f))
                drawCircle(graphColor, 6f, Offset(center.x + width / 2f, center.y + height / 2f))
            }
        }
    }
}

/**
 * The core reusable math renderer. Every feed card, builder preview,
 * chat bubble, and profile thumbnail draws through this one composable
 * so a "build" always means the same thing everywhere it appears.
 */
@Composable
fun MathCanvas(
    type: BuildType,
    params: BuildParams,
    primaryValue: Float,
    shapes: List<ShapeType> = emptyList(),
    expression: String = "sin(x)",
    geometryPoints: List<GeometryPoint> = emptyList(),
    geometryObjects: List<GeometryObject> = emptyList(),
    canvasTexts: List<CanvasText> = emptyList(),
    canvasBoxes: List<CanvasBox> = emptyList(),
    selectedCanvasObjectId: String? = null,
    selectedCanvasObjectIds: Set<String> = emptySet(),
    selectedFunctionFeatureId: String? = null,
    selectedGeometryPointId: String? = null,
    onCanvasTap: ((Float, Float) -> Unit)? = null,
    onCanvasObjectMoved: ((String, Float, Float) -> Unit)? = null,
    onCanvasBoxSelected: ((String?) -> Unit)? = null,
    onCanvasBoxChanged: ((CanvasBox) -> Unit)? = null,
    onFunctionFeatureSelected: ((String?, String?) -> Unit)? = null,
    viewportZoom: Float = 1f,
    viewportOffset: Offset = Offset.Zero,
    onViewportChanged: ((Float, Offset) -> Unit)? = null,
    showBaseFunction: Boolean = true,
    aspectRatioValue: Float = 4f / 3f,
    maintainAspectRatio: Boolean = true,
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val axisColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    val accent = MaterialTheme.colorScheme.secondary
    val shapeColor = MaterialTheme.colorScheme.error
    val geometryColor = MaterialTheme.colorScheme.tertiary
    val boxColor = MaterialTheme.colorScheme.surfaceVariant
    val boxContentColor = MaterialTheme.colorScheme.onSurface
    val compiledExpression = remember(expression) {
        try {
            compileMathExpression(expression)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
    val latestTap = rememberUpdatedState(onCanvasTap)
    val latestObjectMove = rememberUpdatedState(onCanvasObjectMoved)
    val latestPoints = rememberUpdatedState(geometryPoints)
    val latestBoxes = rememberUpdatedState(canvasBoxes)
    val latestBoxSelected = rememberUpdatedState(onCanvasBoxSelected)
    val latestBoxChanged = rememberUpdatedState(onCanvasBoxChanged)
    val latestFunctionFeatureSelected = rememberUpdatedState(onFunctionFeatureSelected)
    val latestBuildType = rememberUpdatedState(type)
    val latestParams = rememberUpdatedState(params)
    val latestPrimaryValue = rememberUpdatedState(primaryValue)
    val latestExpression = rememberUpdatedState(compiledExpression)
    val latestShowBaseFunction = rememberUpdatedState(showBaseFunction)
    val latestViewportZoom = rememberUpdatedState(viewportZoom)
    val latestViewportOffset = rememberUpdatedState(viewportOffset)
    val latestViewportChanged = rememberUpdatedState(onViewportChanged)
    fun hitBox(
        boxes: List<CanvasBox>,
        geo: Geo,
        position: Offset,
        paddingPx: Float = 0f
    ): CanvasBox? {
        boxes.asReversed().forEach { box ->
            if (box.type == CanvasBoxType.CONTAINER) {
                hitBox(box.children, geo, position, paddingPx)?.let { return it }
            }
            val center = geo.toPx(box.x, box.y)
            val angle = Math.toRadians(box.rotation.toDouble())
            val dx = position.x - center.x
            val dy = position.y - center.y
            val localX = dx * kotlin.math.cos(angle) + dy * kotlin.math.sin(angle)
            val localY = -dx * kotlin.math.sin(angle) + dy * kotlin.math.cos(angle)
            if (kotlin.math.abs(localX) <= box.width * geo.scale / 2f + paddingPx &&
                kotlin.math.abs(localY) <= box.height * geo.scale / 2f + paddingPx
            ) return box
        }
        return null
    }

    var canvasModifier = if (maintainAspectRatio) {
        modifier.fillMaxWidth().aspectRatio(aspectRatioValue)
    } else {
        modifier
    }
    if (onCanvasTap != null || onCanvasBoxSelected != null || onFunctionFeatureSelected != null) {
        canvasModifier = canvasModifier.pointerInput(Unit) {
            detectTapGestures { position ->
                val geo = geometry(
                    size.width.toFloat(),
                    size.height.toFloat(),
                    latestViewportZoom.value,
                    latestViewportOffset.value
                )
                val scale = geo.scale
                if (scale > 0f) {
                    val featureHit = findFunctionFeatureAt(
                        position,
                        geo,
                        size.width.toFloat(),
                        size.height.toFloat(),
                        latestBoxes.value,
                        latestShowBaseFunction.value,
                        latestBuildType.value,
                        latestParams.value,
                        latestPrimaryValue.value,
                        latestExpression.value
                    )
                    if (featureHit != null && latestFunctionFeatureSelected.value != null) {
                        latestFunctionFeatureSelected.value?.invoke(
                            featureHit.id,
                            featureHit.coordinates
                        )
                        latestBoxSelected.value?.invoke(hitBox(latestBoxes.value, geo, position)?.id)
                        return@detectTapGestures
                    }
                    latestFunctionFeatureSelected.value?.invoke(null, null)
                    val x = (position.x - geo.originX) / scale
                    val y = (geo.originY - position.y) / scale
                    val hit = hitBox(latestBoxes.value, geo, position)
                    if (hit != null) {
                        latestBoxSelected.value?.invoke(hit.id)
                        latestTap.value?.invoke(x, y)
                    } else {
                        latestBoxSelected.value?.invoke(null)
                        latestTap.value?.invoke(x, y)
                    }
                }
            }
        }
    }
    if (onViewportChanged != null) {
        canvasModifier = canvasModifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var previousCentroid: Offset? = null
                var previousSpan = 0f
                var currentZoom = latestViewportZoom.value
                var currentOffset = latestViewportOffset.value
                do {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.size >= 2) {
                        val centroid = pressed.fold(Offset.Zero) { total, change ->
                            total + change.position
                        } / pressed.size.toFloat()
                        val span = pressed.map { (it.position - centroid).getDistance() }
                            .average()
                            .toFloat()
                        val lastCentroid = previousCentroid
                        if (lastCentroid != null && previousSpan > 0f) {
                            val zoomChange = span / previousSpan
                            val previousZoom = currentZoom
                            val nextZoom = (previousZoom * zoomChange).coerceIn(0.25f, 8f)
                            val zoomRatio = nextZoom / previousZoom
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val nextOffset = centroid - center -
                                (lastCentroid - center - currentOffset) * zoomRatio
                            latestViewportChanged.value?.invoke(nextZoom, nextOffset)
                            currentZoom = nextZoom
                            currentOffset = nextOffset
                        }
                        pressed.forEach { it.consume() }
                        previousCentroid = centroid
                        previousSpan = span
                    } else {
                        previousCentroid = null
                        previousSpan = 0f
                    }
                } while (event.changes.any { it.pressed })
            }
        }
    }
    if (onCanvasObjectMoved != null || onCanvasBoxChanged != null || onViewportChanged != null) {
        canvasModifier = canvasModifier.pointerInput(Unit) {
            var draggedPointId: String? = null
            var draggedBox: CanvasBox? = null
            var dragMode = "move"
            var dragStart = Offset.Zero
            var dragCenter = Offset.Zero
            var dragStartAngle = 0f
            var dragStartWidth = 0f
            var dragStartHeight = 0f
            var pointDragOffsetX = 0f
            var pointDragOffsetY = 0f
            var dragViewportOffset = Offset.Zero
            fun toGraph(position: Offset): Pair<Float, Float> {
                val geo = geometry(
                    size.width.toFloat(),
                    size.height.toFloat(),
                    latestViewportZoom.value,
                    latestViewportOffset.value
                )
                val scale = geo.scale
                return if (scale > 0f) {
                    ((position.x - geo.originX) / scale) to
                        ((geo.originY - position.y) / scale)
                } else {
                    0f to 0f
                }
            }
            fun toBoxGraph(position: Offset, box: CanvasBox): Pair<Float, Float> {
                val geo = geometry(
                    size.width.toFloat(),
                    size.height.toFloat(),
                    latestViewportZoom.value,
                    latestViewportOffset.value
                )
                val scale = geo.scale
                val centerX = geo.originX + box.x * scale
                val centerY = geo.originY - box.y * scale
                val angle = Math.toRadians(box.rotation.toDouble())
                val dx = position.x - centerX
                val dy = position.y - centerY
                val localX = dx * kotlin.math.cos(angle) + dy * kotlin.math.sin(angle)
                val localY = -dx * kotlin.math.sin(angle) + dy * kotlin.math.cos(angle)
                val boxScale = min(box.width, box.height) * scale / (RANGE * 2f)
                return if (boxScale > 0f) {
                    (localX / boxScale).toFloat() to (-localY / boxScale).toFloat()
                } else {
                    0f to 0f
                }
            }
            detectDragGestures(
                onDragStart = { position ->
                    dragViewportOffset = latestViewportOffset.value
                    val geo = geometry(
                        size.width.toFloat(),
                        size.height.toFloat(),
                        latestViewportZoom.value,
                        latestViewportOffset.value
                    )
                    val scale = geo.scale
                    val box = if (latestBoxChanged.value == null) {
                        null
                    } else {
                        hitBox(latestBoxes.value, geo, position, paddingPx = 22f)
                    }
                    if (box != null) {
                        draggedBox = box
                        dragStart = position
                        dragCenter = Offset(
                            geo.originX + box.x * scale,
                            geo.originY - box.y * scale
                        )
                        dragStartWidth = box.width
                        dragStartHeight = box.height
                        val angle = Math.toRadians(box.rotation.toDouble())
                        val dx = position.x - dragCenter.x
                        val dy = position.y - dragCenter.y
                        val localX = dx * kotlin.math.cos(angle) + dy * kotlin.math.sin(angle)
                        val localY = -dx * kotlin.math.sin(angle) + dy * kotlin.math.cos(angle)
                        dragMode = when {
                            localX > box.width * scale / 2f - 20f &&
                                localY > box.height * scale / 2f - 20f -> "resize"
                            kotlin.math.abs(localX) < 20f &&
                                localY < -box.height * scale / 2f + 22f &&
                                localY > -box.height * scale / 2f - 24f -> "rotate"
                            else -> "move"
                        }
                        dragStartAngle = kotlin.math.atan2(
                            position.y - dragCenter.y,
                            position.x - dragCenter.x
                        )
                        if (box.type == CanvasBoxType.GEOMETRY &&
                            latestObjectMove.value != null
                        ) {
                            val (x, y) = toBoxGraph(position, box)
                            val point = latestPoints.value.minByOrNull { item ->
                                val dx = item.x - x
                                val dy = item.y - y
                                dx * dx + dy * dy
                            }?.takeIf { item ->
                                val dx = item.x - x
                                val dy = item.y - y
                                dx * dx + dy * dy <= 0.55f * 0.55f
                            }
                            if (point != null) {
                                draggedPointId = point.id
                                pointDragOffsetX = x - point.x
                                pointDragOffsetY = y - point.y
                            }
                        }
                        latestBoxSelected.value?.invoke(box.id)
                    }
                },
                onDrag = { change, _ ->
                    val box = draggedBox
                    if (box != null && draggedPointId == null) {
                        change.consume()
                        val scale = geometry(
                            size.width.toFloat(),
                            size.height.toFloat(),
                            latestViewportZoom.value,
                            latestViewportOffset.value
                        ).scale
                        val dx = change.position.x - dragStart.x
                        val dy = change.position.y - dragStart.y
                        val changed = when (dragMode) {
                            "rotate" -> {
                                val angle = kotlin.math.atan2(
                                    change.position.y - dragCenter.y,
                                    change.position.x - dragCenter.x
                                )
                                box.copy(
                                    rotation = box.rotation +
                                        Math.toDegrees((angle - dragStartAngle).toDouble()).toFloat()
                                )
                            }
                            "resize" -> {
                                val radians = Math.toRadians(box.rotation.toDouble())
                                val localX = dx * kotlin.math.cos(radians) + dy * kotlin.math.sin(radians)
                                val localY = -dx * kotlin.math.sin(radians) + dy * kotlin.math.cos(radians)
                                box.copy(
                                    width = (dragStartWidth + localX.toFloat() / scale * 2f)
                                        .coerceAtLeast(0.25f),
                                    height = (dragStartHeight + localY.toFloat() / scale * 2f)
                                        .coerceAtLeast(0.25f)
                                )
                            }
                            else -> {
                                val (x, y) = toGraph(change.position)
                                val (startX, startY) = toGraph(dragStart)
                                box.copy(x = box.x + x - startX, y = box.y + y - startY)
                            }
                        }
                        latestBoxChanged.value?.invoke(changed)
                    } else if (draggedPointId != null) {
                        change.consume()
                        val box = draggedBox
                        val (x, y) = if (box != null) toBoxGraph(change.position, box) else toGraph(change.position)
                        latestObjectMove.value?.invoke(
                            draggedPointId!!,
                            x - pointDragOffsetX,
                            y - pointDragOffsetY
                        )
                    } else if (onViewportChanged != null) {
                        change.consume()
                        dragViewportOffset += change.position - change.previousPosition
                        latestViewportChanged.value?.invoke(
                            latestViewportZoom.value,
                            dragViewportOffset
                        )
                    }
                },
                onDragEnd = {
                    draggedPointId = null
                    draggedBox = null
                },
                onDragCancel = {
                    draggedPointId = null
                    draggedBox = null
                }
            )
        }
    }

    Canvas(modifier = canvasModifier) {
        val geo = geometry(size.width, size.height, viewportZoom, viewportOffset)
        drawGrid(geo, gridColor, axisColor, infinite = onViewportChanged != null)
        if (showBaseFunction && !canvasBoxes.containsType(CanvasBoxType.GRAPH)) when (type) {
            BuildType.SINE -> plotFunction(
                geo, accent,
                { x -> primaryValue * kotlin.math.sin(params.b * x + params.c) },
                selectedFunctionFeatureId,
                "base"
            )
            BuildType.QUADRATIC -> plotFunction(
                geo, accent,
                { x -> primaryValue * x * x + params.b * x + params.c },
                selectedFunctionFeatureId,
                "base"
            )
            BuildType.LINEAR -> plotFunction(
                geo, accent,
                { x -> params.a * x + primaryValue },
                selectedFunctionFeatureId,
                "base"
            )
            BuildType.CIRCLE -> plotCircleShape(geo, primaryValue, accent)
            BuildType.FUNCTION -> {
                compiledExpression?.let { evaluate ->
                    plotFunction(
                        geo,
                        accent,
                        { x -> evaluate(x.toDouble()).toFloat() },
                        selectedFunctionFeatureId,
                        "base"
                    )
                }
            }
        }
        shapes.forEach { drawOverlayShape(geo, it, shapeColor) }
        if (!canvasBoxes.containsType(CanvasBoxType.GEOMETRY)) {
            drawGeometry(geo, geometryPoints, geometryObjects, geometryColor, selectedGeometryPointId)
        }
        val selectedIds = selectedCanvasObjectIds.ifEmpty {
            setOfNotNull(selectedCanvasObjectId)
        }
        drawCanvasTexts(geo, canvasTexts, selectedCanvasObjectId)
        drawCanvasBoxes(
            geo,
            canvasBoxes,
            geometryPoints,
            geometryObjects,
            selectedGeometryPointId,
            selectedIds,
            selectedFunctionFeatureId,
            accent,
            gridColor,
            axisColor,
            boxColor,
            boxContentColor
        )
    }
}
