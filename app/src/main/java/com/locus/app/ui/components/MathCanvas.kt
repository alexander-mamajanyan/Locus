package com.locus.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.locus.app.model.BuildParams
import com.locus.app.model.BuildType
import com.locus.app.model.ShapeType
import kotlin.math.abs
import kotlin.math.min

private const val RANGE = 4.6f

private data class Geo(val scale: Float, val originX: Float, val originY: Float)

private fun DrawScope.geometry(range: Float = RANGE): Geo {
    val scale = min(size.width, size.height) / (range * 2f)
    return Geo(scale, size.width / 2f, size.height / 2f)
}

private fun Geo.toPx(x: Float, y: Float) = Offset(originX + x * scale, originY - y * scale)

private fun DrawScope.drawGrid(geo: Geo, gridColor: Color, axisColor: Color, range: Float = RANGE) {
    var x = -range.toInt()
    while (x <= range) {
        val px = geo.originX + x * geo.scale
        drawLine(gridColor, Offset(px, 0f), Offset(px, size.height), strokeWidth = 1f)
        x++
    }
    var y = -range.toInt()
    while (y <= range) {
        val py = geo.originY - y * geo.scale
        drawLine(gridColor, Offset(0f, py), Offset(size.width, py), strokeWidth = 1f)
        y++
    }
    drawLine(axisColor, Offset(0f, geo.originY), Offset(size.width, geo.originY), strokeWidth = 1.6f)
    drawLine(axisColor, Offset(geo.originX, 0f), Offset(geo.originX, size.height), strokeWidth = 1.6f)
}

private fun DrawScope.plotFunction(geo: Geo, color: Color, range: Float = RANGE, fn: (Float) -> Float) {
    val path = Path()
    var started = false
    var px = 0f
    while (px <= size.width) {
        val x = (px - geo.originX) / geo.scale
        val y = fn(x)
        if (abs(y) > range * 1.6f) {
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
    aspectRatioValue: Float = 4f / 3f,
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val axisColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    val accent = MaterialTheme.colorScheme.secondary
    val shapeColor = MaterialTheme.colorScheme.error

    Canvas(modifier = modifier.fillMaxWidth().aspectRatio(aspectRatioValue)) {
        val geo = geometry()
        drawGrid(geo, gridColor, axisColor)
        when (type) {
            BuildType.SINE -> plotFunction(geo, accent) { x -> primaryValue * kotlin.math.sin(params.b * x + params.c) }
            BuildType.QUADRATIC -> plotFunction(geo, accent) { x -> primaryValue * x * x + params.b * x + params.c }
            BuildType.LINEAR -> plotFunction(geo, accent) { x -> params.a * x + primaryValue }
            BuildType.CIRCLE -> plotCircleShape(geo, primaryValue, accent)
        }
        shapes.forEach { drawOverlayShape(geo, it, shapeColor) }
    }
}
