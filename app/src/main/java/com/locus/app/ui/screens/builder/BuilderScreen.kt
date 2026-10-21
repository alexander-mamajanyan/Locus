package com.locus.app.ui.screens.builder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.locus.app.data.LocusRepository
import com.locus.app.model.*
import com.locus.app.ui.components.MathCanvas
import java.util.UUID
import kotlin.math.roundToInt

private enum class PlaygroundTool(val label: String, val icon: ImageVector) {
    MOVE("Move", Icons.Filled.OpenWith),
    GRAPH("Graph", Icons.Filled.Timeline),
    EXPRESSION("Expr", Icons.Filled.Calculate),
    TEXT("Text", Icons.Filled.TextFields),
    BOX("Box", Icons.Filled.CropSquare),
    POINT("Point", Icons.Filled.Add),
    SEGMENT("Segment", Icons.Filled.Timeline),
    CIRCLE("Circle", Icons.Filled.Circle)
}

private enum class ToolCategory(val label: String) {
    ARITHMETIC("Arithmetic"),
    GEOMETRY("Geometry"),
    OTHER("Other")
}

@Composable
private fun ToolOption(
    tool: PlaygroundTool,
    selected: Boolean,
    onSelect: (PlaygroundTool) -> Unit,
    onDrop: (PlaygroundTool, Offset) -> Unit
) {
    var coordinates by remember(tool) { mutableStateOf<LayoutCoordinates?>(null) }
    var dragPosition by remember(tool) { mutableStateOf(Offset.Zero) }
    val latestDrop = rememberUpdatedState(onDrop)
    Column(
        modifier = Modifier
            .widthIn(min = 64.dp)
            .height(56.dp)
            .onGloballyPositioned { coordinates = it }
            .pointerInput(tool) {
                detectDragGestures(
                    onDragStart = { dragPosition = it },
                    onDragEnd = {
                        coordinates?.localToWindow(dragPosition)?.let { position ->
                            latestDrop.value(tool, position)
                        }
                    },
                    onDrag = { change, dragAmount ->
                        dragPosition += dragAmount
                        change.consume()
                    }
                )
            }
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
            )
            .clickable(role = Role.Button) { onSelect(tool) }
            .semantics {
                contentDescription = "${tool.label} tool"
                this.selected = selected
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            tool.icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Text(
            tool.label,
            fontSize = 10.sp,
            maxLines = 1,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun findCanvasBoxByType(
    boxes: List<CanvasBox>,
    type: CanvasBoxType
): CanvasBox? {
    boxes.forEach { box ->
        if (box.type == type) return box
        findCanvasBoxByType(box.children, type)?.let { return it }
    }
    return null
}

@Composable
fun BuilderScreen(onPosted: () -> Unit) {
    var expression by remember { mutableStateOf("sin(x)") }
    var points by remember { mutableStateOf(emptyList<GeometryPoint>()) }
    var objects by remember { mutableStateOf(emptyList<GeometryObject>()) }
    var canvasBoxes by remember { mutableStateOf(emptyList<CanvasBox>()) }
    var nextPointId by remember { mutableIntStateOf(1) }
    var nextBoxId by remember { mutableIntStateOf(1) }
    var selectedCategory by remember { mutableStateOf(ToolCategory.GEOMETRY) }
    var selectedTool by remember { mutableStateOf(PlaygroundTool.MOVE) }
    var selectedFunctionFeatureId by remember { mutableStateOf<String?>(null) }
    var selectedFunctionFeatureCoordinates by remember { mutableStateOf<String?>(null) }
    var pendingPointId by remember { mutableStateOf<String?>(null) }
    var activeContainerId by remember { mutableStateOf<String?>(null) }
    var groupSelectionMode by remember { mutableStateOf(false) }
    var groupSelection by remember { mutableStateOf(emptySet<String>()) }
    var mathEditorOpen by remember { mutableStateOf(false) }
    var formulaDraft by remember { mutableStateOf("sin(x)") }
    var editingBoxId by remember { mutableStateOf<String?>(null) }
    var selectedBoxId by remember { mutableStateOf<String?>(null) }
    var editingGraph by remember { mutableStateOf(false) }
    var textEditorOpen by remember { mutableStateOf(false) }
    var textDraft by remember { mutableStateOf("") }
    var canvasCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var viewportZoom by remember { mutableFloatStateOf(1f) }
    var viewportOffset by remember { mutableStateOf(Offset.Zero) }

    val formulaDraftError = remember(formulaDraft, editingGraph) {
        if (editingGraph) {
            try {
                compileMathExpression(formulaDraft)
                null
            } catch (exception: IllegalArgumentException) {
                exception.message ?: "Check the formula syntax."
            }
        } else null
    }

    val toolHint = when (selectedTool) {
        PlaygroundTool.MOVE ->
            "Drag boxes to move; tap graph dots for coordinates; drag empty canvas to pan, pinch to zoom."
        PlaygroundTool.GRAPH -> "Tap a dot to see its coordinates."
        PlaygroundTool.EXPRESSION,
        PlaygroundTool.TEXT -> "Tap the canvas to place, or drag this tool onto it."
        PlaygroundTool.BOX -> "Tap or drag to create a box, then add tools inside it."
        PlaygroundTool.POINT -> if (findCanvasBoxByType(canvasBoxes, CanvasBoxType.GEOMETRY) != null) {
            "Tap inside the geometry box; points snap to grid intersections."
        } else {
            "Tap the plane to add a point; it snaps to a grid intersection."
        }
        PlaygroundTool.SEGMENT -> if (pendingPointId == null) {
            "Tap one point, then another to connect them."
        } else {
            "Starting at $pendingPointId — tap the point to connect."
        }
        PlaygroundTool.CIRCLE -> if (pendingPointId == null) {
            "Tap a point to set the circle's center."
        } else {
            "Center set at $pendingPointId — tap a second point for the radius."
        }
    }

    fun snapToGrid(value: Float): Float = value.roundToInt().toFloat()

    fun addBox(
        type: CanvasBoxType,
        content: String = "",
        x: Float? = null,
        y: Float? = null
    ): CanvasBox {
        val placements = listOf(
            0f to 0f,
            -3.2f to 1.8f,
            3.2f to 1.8f,
            -3.2f to -1.8f,
            3.2f to -1.8f,
            0f to 1.8f
        )
        val placement = if (type == CanvasBoxType.GEOMETRY) {
            0f to 0f
        } else {
            placements[canvasBoxes.size % placements.size]
        }
        val box = CanvasBox(
            id = "B${nextBoxId++}",
            type = type,
            x = x ?: placement.first,
            y = y ?: placement.second,
            width = if (type == CanvasBoxType.GEOMETRY) 4.2f else 3.4f,
            height = if (type == CanvasBoxType.GEOMETRY) 3.4f else 2.5f,
            content = content
        )
        canvasBoxes = canvasBoxes + box
        selectedBoxId = box.id
        return box
    }

    fun findBox(boxes: List<CanvasBox>, id: String?): CanvasBox? {
        if (id == null) return null
        boxes.forEach { box ->
            if (box.id == id) return box
            findBox(box.children, id)?.let { return it }
        }
        return null
    }

    fun findRootId(boxes: List<CanvasBox>, id: String, rootId: String? = null): String? {
        boxes.forEach { box ->
            val root = rootId ?: box.id
            if (box.id == id) return root
            findRootId(box.children, id, root)?.let { return it }
        }
        return null
    }

    fun findParentContainerId(boxes: List<CanvasBox>, id: String): String? {
        boxes.forEach { box ->
            if (box.children.any { it.id == id }) return box.id
            findParentContainerId(box.children, id)?.let { return it }
        }
        return null
    }

    fun updateBoxTree(
        boxes: List<CanvasBox>,
        id: String,
        update: (CanvasBox) -> CanvasBox
    ): List<CanvasBox> = boxes.map { box ->
        when {
            box.id == id -> update(box)
            box.children.isNotEmpty() -> box.copy(
                children = updateBoxTree(box.children, id, update)
            )
            else -> box
        }
    }

    fun moveChildrenWithContainer(
        children: List<CanvasBox>,
        previous: CanvasBox,
        changed: CanvasBox
    ): List<CanvasBox> {
        val angle = Math.toRadians((changed.rotation - previous.rotation).toDouble())
        val scaleX = changed.width / previous.width
        val scaleY = changed.height / previous.height
        return children.map { child ->
            val dx = child.x - previous.x
            val dy = child.y - previous.y
            val movedChild = child.copy(
                x = changed.x + (dx * kotlin.math.cos(angle) + dy * kotlin.math.sin(angle)).toFloat() * scaleX,
                y = changed.y + (-dx * kotlin.math.sin(angle) + dy * kotlin.math.cos(angle)).toFloat() * scaleY,
                width = child.width * scaleX,
                height = child.height * scaleY,
                rotation = child.rotation + changed.rotation - previous.rotation
            )
            movedChild.copy(
                children = moveChildrenWithContainer(child.children, child, movedChild)
            )
        }
    }

    fun createElement(
        type: CanvasBoxType,
        content: String,
        x: Float,
        y: Float
    ): CanvasBox = CanvasBox(
        id = "B${nextBoxId++}",
        type = type,
        x = x,
        y = y,
        width = 3.4f,
        height = 2.5f,
        content = content
    )

    fun attachToContainerIfInside(container: CanvasBox?, child: CanvasBox): Boolean {
        if (container == null || container.type != CanvasBoxType.CONTAINER) return false
        val angle = Math.toRadians(container.rotation.toDouble())
        val dx = child.x - container.x
        val dy = child.y - container.y
        val localX = dx * kotlin.math.cos(angle) + dy * kotlin.math.sin(angle)
        val localY = -dx * kotlin.math.sin(angle) + dy * kotlin.math.cos(angle)
        if (kotlin.math.abs(localX) > container.width / 2f ||
            kotlin.math.abs(localY) > container.height / 2f
        ) return false
        canvasBoxes = updateBoxTree(canvasBoxes, container.id) {
            it.copy(children = it.children + child)
        }
        selectedBoxId = container.id
        activeContainerId = container.id
        return true
    }

    fun openBoxEditor(box: CanvasBox, isGraph: Boolean) {
        if (box.type != CanvasBoxType.CONTAINER) selectedBoxId = box.id
        editingBoxId = box.id
        editingGraph = isGraph
        formulaDraft = box.content
        mathEditorOpen = true
    }

    fun placeTool(tool: PlaygroundTool, x: Float, y: Float) {
        if (tool == PlaygroundTool.BOX) {
            val newContainer = CanvasBox(
                id = "B${nextBoxId++}",
                type = CanvasBoxType.CONTAINER,
                x = x,
                y = y,
                width = 5.4f,
                height = 4.2f
            )
            val parent = findBox(canvasBoxes, activeContainerId)
            if (!attachToContainerIfInside(parent, newContainer)) {
                canvasBoxes = canvasBoxes + newContainer
                selectedBoxId = newContainer.id
                activeContainerId = newContainer.id
            }
            selectedCategory = ToolCategory.GEOMETRY
            selectedTool = PlaygroundTool.MOVE
            return
        }
        val (type, content) = when (tool) {
            PlaygroundTool.GRAPH -> CanvasBoxType.GRAPH to "sin(x)"
            PlaygroundTool.EXPRESSION -> CanvasBoxType.EXPRESSION to "x^2 = 2*x"
            PlaygroundTool.TEXT -> CanvasBoxType.TEXT to "Text"
            else -> return
        }
        val box = createElement(type, content, x, y)
        val targetContainer = findBox(canvasBoxes, selectedBoxId)
            ?.takeIf { it.type == CanvasBoxType.CONTAINER }
            ?: findBox(canvasBoxes, activeContainerId)
        val attached = attachToContainerIfInside(targetContainer, box)
        if (!attached) {
            canvasBoxes = canvasBoxes + box
            selectedBoxId = box.id
        }
        if (attached) activeContainerId = targetContainer?.id
        selectedTool = PlaygroundTool.MOVE
        when (tool) {
            PlaygroundTool.GRAPH -> openBoxEditor(box, isGraph = true)
            PlaygroundTool.EXPRESSION -> openBoxEditor(box, isGraph = false)
            PlaygroundTool.TEXT -> {
                editingBoxId = box.id
                textDraft = box.content
                textEditorOpen = true
            }
            else -> Unit
        }
        if (attached) selectedBoxId = targetContainer?.id
        selectedCategory = ToolCategory.GEOMETRY
        selectedTool = PlaygroundTool.MOVE
    }

    fun clearCanvas() {
        points = emptyList()
        objects = emptyList()
        canvasBoxes = emptyList()
        pendingPointId = null
        selectedBoxId = null
        activeContainerId = null
        selectedFunctionFeatureId = null
        selectedFunctionFeatureCoordinates = null
        groupSelection = emptySet()
        groupSelectionMode = false
    }

    fun postBuild() {
        val newBuild = MathBuild(
            id = "u${UUID.randomUUID()}",
            username = LocusRepository.currentUsername,
            ownerId = LocusRepository.currentUid.orEmpty(),
            time = "now",
            caption = "new build",
            type = BuildType.FUNCTION,
            params = BuildParams(),
            expression = canvasBoxes.firstOrNull {
                it.type == CanvasBoxType.GRAPH
            }?.content ?: expression,
            geometryPoints = points,
            geometryObjects = objects,
            canvasBoxes = canvasBoxes,
            showBaseFunction = false
        )
        LocusRepository.addBuild(newBuild) {
            clearCanvas()
            onPosted()
        }
    }

    fun editSelectedBox() {
        val box = findBox(canvasBoxes, selectedBoxId) ?: return
        when (box.type) {
            CanvasBoxType.GRAPH, CanvasBoxType.EXPRESSION ->
                openBoxEditor(box, isGraph = box.type == CanvasBoxType.GRAPH)
            CanvasBoxType.TEXT -> {
                editingBoxId = box.id
                textDraft = box.content
                textEditorOpen = true
            }
            CanvasBoxType.GEOMETRY, CanvasBoxType.CONTAINER -> Unit
        }
    }

    fun deleteSelectedBox() {
        val box = findBox(canvasBoxes, selectedBoxId) ?: return
        fun containsGeometry(item: CanvasBox): Boolean =
            item.type == CanvasBoxType.GEOMETRY || item.children.any(::containsGeometry)

        fun remove(boxes: List<CanvasBox>): List<CanvasBox> = boxes
            .filterNot { it.id == box.id }
            .map { item ->
                if (item.children.any { it.id == box.id }) {
                    item.copy(children = item.children.filterNot { it.id == box.id })
                } else {
                    item.copy(children = remove(item.children))
                }
            }
        canvasBoxes = remove(canvasBoxes)
        if (containsGeometry(box)) {
            points = emptyList()
            objects = emptyList()
            pendingPointId = null
        }
        selectedBoxId = null
        if (activeContainerId == box.id) activeContainerId = null
    }

    fun createGeometryBox(centerX: Float, centerY: Float): CanvasBox {
        val existing = findCanvasBoxByType(canvasBoxes, CanvasBoxType.GEOMETRY)
        if (existing != null) return existing
        val box = addBox(CanvasBoxType.GEOMETRY).copy(x = centerX, y = centerY)
        val container = findBox(canvasBoxes, activeContainerId)
            ?.takeIf { it.type == CanvasBoxType.CONTAINER }
        if (attachToContainerIfInside(container, box)) {
            canvasBoxes = canvasBoxes.filterNot { it.id == box.id }
        } else {
            canvasBoxes = canvasBoxes.map { if (it.id == box.id) box else it }
        }
        return box
    }

    fun groupSelectedBoxes() {
        val selected = canvasBoxes.filter { it.id in groupSelection }
        if (selected.size < 2) return
        val corners = selected.flatMap { box ->
            val angle = Math.toRadians(box.rotation.toDouble())
            listOf(
                -box.width / 2f to -box.height / 2f,
                box.width / 2f to -box.height / 2f,
                box.width / 2f to box.height / 2f,
                -box.width / 2f to box.height / 2f
            ).map { (localX, localY) ->
                (box.x + (localX * kotlin.math.cos(angle) - localY * kotlin.math.sin(angle)).toFloat()) to
                    (box.y + (localX * kotlin.math.sin(angle) + localY * kotlin.math.cos(angle)).toFloat())
            }
        }
        val left = corners.minOf { it.first }
        val right = corners.maxOf { it.first }
        val bottom = corners.minOf { it.second }
        val top = corners.maxOf { it.second }
        val group = CanvasBox(
            id = "B${nextBoxId++}",
            type = CanvasBoxType.CONTAINER,
            x = (left + right) / 2f,
            y = (bottom + top) / 2f,
            width = (right - left + 0.4f).coerceAtLeast(1.4f),
            height = (top - bottom + 0.4f).coerceAtLeast(1.2f),
            children = selected
        )
        canvasBoxes = canvasBoxes.filterNot { it.id in groupSelection } + group
        selectedBoxId = group.id
        activeContainerId = group.id
        groupSelection = emptySet()
        groupSelectionMode = false
    }

    fun tapToGeometry(x: Float, y: Float, box: CanvasBox): Pair<Float, Float>? {
        val radians = Math.toRadians(box.rotation.toDouble())
        val dx = x - box.x
        val dy = y - box.y
        val localX = (dx * kotlin.math.cos(radians) + dy * kotlin.math.sin(radians)).toFloat()
        val localY = (-dx * kotlin.math.sin(radians) + dy * kotlin.math.cos(radians)).toFloat()
        if (kotlin.math.abs(localX) > box.width / 2f ||
            kotlin.math.abs(localY) > box.height / 2f
        ) {
            return null
        }
        val fit = 2f * 4.6f / minOf(box.width, box.height)
        val graphX = localX * fit
        val graphY = localY * fit
        return if (kotlin.math.abs(graphX) <= 4.6f &&
            kotlin.math.abs(graphY) <= 4.6f
        ) {
            graphX to graphY
        } else {
            null
        }
    }

    fun nearestPoint(x: Float, y: Float): GeometryPoint? =
        points.minByOrNull { point ->
            val dx = point.x - x
            val dy = point.y - y
            dx * dx + dy * dy
        }?.takeIf { point ->
            val dx = point.x - x
            val dy = point.y - y
            dx * dx + dy * dy <= 0.55f * 0.55f
        }

    fun onCanvasTap(x: Float, y: Float) {
        when (selectedTool) {
            PlaygroundTool.MOVE -> Unit
            PlaygroundTool.GRAPH, PlaygroundTool.EXPRESSION, PlaygroundTool.TEXT, PlaygroundTool.BOX ->
                placeTool(selectedTool, x, y)
            PlaygroundTool.POINT -> {
                val box = createGeometryBox(x, y)
                val (localX, localY) = tapToGeometry(x, y, box) ?: return
                val point = GeometryPoint(
                    "P${nextPointId++}",
                    snapToGrid(localX),
                    snapToGrid(localY)
                )
                points = points + point
            }
            PlaygroundTool.SEGMENT, PlaygroundTool.CIRCLE -> {
                val box = findCanvasBoxByType(canvasBoxes, CanvasBoxType.GEOMETRY) ?: return
                val (localX, localY) = tapToGeometry(x, y, box) ?: return
                val point = nearestPoint(localX, localY)
                if (point != null) {
                    val pendingId = pendingPointId
                    if (pendingId == null) {
                        pendingPointId = point.id
                    } else if (pendingId != point.id) {
                        objects = objects + GeometryObject(
                            type = if (selectedTool == PlaygroundTool.SEGMENT) {
                                GeometryType.SEGMENT
                            } else {
                                GeometryType.CIRCLE
                            },
                            firstPointId = pendingId,
                            secondPointId = point.id
                        )
                        pendingPointId = null
                    }
                }
            }
        }
    }

    fun placeDraggedTool(tool: PlaygroundTool, windowPosition: Offset) {
        val coordinates = canvasCoordinates ?: return
        if (!coordinates.boundsInWindow().contains(windowPosition)) return
        val localPosition = coordinates.windowToLocal(windowPosition)
        val scale = minOf(coordinates.size.width, coordinates.size.height) /
            (4.6f * 2f) * viewportZoom
        if (scale <= 0f) return
        val x = (localPosition.x - coordinates.size.width / 2f - viewportOffset.x) / scale
        val y = (coordinates.size.height / 2f + viewportOffset.y - localPosition.y) / scale
        when (tool) {
            PlaygroundTool.GRAPH, PlaygroundTool.EXPRESSION, PlaygroundTool.TEXT, PlaygroundTool.BOX ->
                placeTool(tool, x, y)
            PlaygroundTool.POINT, PlaygroundTool.SEGMENT, PlaygroundTool.CIRCLE -> {
                selectedTool = tool
                onCanvasTap(x, y)
            }
            PlaygroundTool.MOVE -> Unit
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Math playground") },
            actions = {
                val selectedBox = findBox(canvasBoxes, selectedBoxId)
                if (selectedBox != null) {
                    if (selectedBox.type in setOf(
                            CanvasBoxType.GRAPH,
                            CanvasBoxType.EXPRESSION,
                            CanvasBoxType.TEXT
                        )
                    ) {
                        IconButton(onClick = { editSelectedBox() }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit selected box")
                        }
                    }
                    IconButton(onClick = { deleteSelectedBox() }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete selected box")
                    }
                }
                if (canvasBoxes.isNotEmpty() || points.isNotEmpty() || objects.isNotEmpty()) {
                    IconButton(onClick = { clearCanvas() }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear canvas")
                    }
                }
                if (groupSelectionMode) {
                    TextButton(onClick = {
                        groupSelectionMode = false
                        groupSelection = emptySet()
                    }) { Text("Cancel") }
                    TextButton(
                        onClick = { groupSelectedBoxes() },
                        enabled = groupSelection.size >= 2
                    ) { Text("Group ${groupSelection.size}") }
                } else if (canvasBoxes.size >= 2) {
                    TextButton(onClick = {
                        groupSelectionMode = true
                        groupSelection = emptySet()
                        selectedBoxId = null
                        selectedTool = PlaygroundTool.MOVE
                        pendingPointId = null
                    }) { Text("Group") }
                }
                TextButton(onClick = { postBuild() }) {
                    Icon(Icons.Filled.Send, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Post")
                }
            }
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            LocusRepository.socialError?.let { error ->
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { LocusRepository.clearSocialError() }
                        .padding(bottom = 8.dp)
                )
            }
            MathCanvas(
                type = BuildType.FUNCTION,
                params = BuildParams(),
                primaryValue = 1f,
                expression = expression,
                geometryPoints = points,
                geometryObjects = objects,
                canvasBoxes = canvasBoxes,
                selectedCanvasObjectIds = if (groupSelectionMode) {
                    groupSelection
                } else if (selectedTool == PlaygroundTool.MOVE) {
                    setOfNotNull(selectedBoxId)
                } else emptySet(),
                selectedFunctionFeatureId = selectedFunctionFeatureId,
                selectedGeometryPointId = pendingPointId,
                showBaseFunction = false,
                onCanvasTap = ::onCanvasTap,
                onFunctionFeatureSelected = { id, coordinates ->
                    selectedFunctionFeatureId = id
                    selectedFunctionFeatureCoordinates = coordinates
                },
                onCanvasObjectMoved = if (
                    selectedTool == PlaygroundTool.MOVE && !groupSelectionMode
                ) {
                    { id, x, y ->
                        points = points.map {
                            if (it.id == id) {
                                it.copy(x = snapToGrid(x), y = snapToGrid(y))
                            } else {
                                it
                            }
                        }
                    }
                } else null,
                onCanvasBoxSelected = { id ->
                    if (groupSelectionMode) {
                        val rootId = id?.let { findRootId(canvasBoxes, it) }
                        if (rootId != null) {
                            groupSelection = if (rootId in groupSelection) {
                                groupSelection - rootId
                            } else {
                                groupSelection + rootId
                            }
                        }
                    } else {
                        selectedBoxId = id
                        activeContainerId = id?.let { selected ->
                            findBox(canvasBoxes, selected)
                                ?.takeIf { it.type == CanvasBoxType.CONTAINER }
                                ?.id
                                ?: findParentContainerId(canvasBoxes, selected)
                        }
                    }
                },
                onCanvasBoxChanged = if (
                    selectedTool == PlaygroundTool.MOVE && !groupSelectionMode
                ) {
                    { changed ->
                        canvasBoxes = updateBoxTree(canvasBoxes, changed.id) { old ->
                            if (old.type == CanvasBoxType.CONTAINER) {
                                changed.copy(
                                    children = moveChildrenWithContainer(
                                        old.children,
                                        old,
                                        changed
                                    )
                                )
                            } else {
                                changed
                            }
                        }
                    }
                } else null,
                viewportZoom = viewportZoom,
                viewportOffset = viewportOffset,
                onViewportChanged = if (groupSelectionMode) null else { zoom, offset ->
                    viewportZoom = zoom
                    viewportOffset = offset
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .onGloballyPositioned { canvasCoordinates = it },
                maintainAspectRatio = false
            )

            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (selectedFunctionFeatureCoordinates != null) {
                            PlaygroundTool.GRAPH.icon
                        } else selectedTool.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            selectedFunctionFeatureCoordinates ?: selectedTool.label,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                        if (selectedFunctionFeatureCoordinates != null) {
                            Text("Selected function point", fontSize = 12.sp)
                        } else {
                            Text(toolHint, fontSize = 12.sp)
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ToolCategory.values().forEach { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = {
                                    selectedCategory = category
                                    pendingPointId = null
                                    selectedFunctionFeatureId = null
                                    selectedFunctionFeatureCoordinates = null
                                    selectedTool = when (category) {
                                        ToolCategory.ARITHMETIC -> PlaygroundTool.GRAPH
                                        ToolCategory.GEOMETRY -> PlaygroundTool.MOVE
                                        ToolCategory.OTHER -> PlaygroundTool.TEXT
                                    }
                                },
                                label = { Text(category.label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    val categoryTools = when (selectedCategory) {
                        ToolCategory.ARITHMETIC -> listOf(
                            PlaygroundTool.GRAPH,
                            PlaygroundTool.EXPRESSION
                        )
                        ToolCategory.GEOMETRY -> listOf(
                            PlaygroundTool.MOVE,
                            PlaygroundTool.POINT,
                            PlaygroundTool.SEGMENT,
                            PlaygroundTool.CIRCLE
                        )
                        ToolCategory.OTHER -> listOf(
                            PlaygroundTool.TEXT,
                            PlaygroundTool.BOX
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categoryTools.forEach { tool ->
                            ToolOption(
                                tool = tool,
                                selected = selectedTool == tool,
                                onSelect = {
                                    pendingPointId = null
                                    selectedFunctionFeatureId = null
                                    selectedFunctionFeatureCoordinates = null
                                    selectedTool = it
                                },
                                onDrop = ::placeDraggedTool
                            )
                        }
                    }
                }
            }
        }

        if (textEditorOpen) {
            AlertDialog(
                onDismissRequest = { textEditorOpen = false },
                title = { Text("Edit text box") },
                text = {
                    OutlinedTextField(
                        value = textDraft,
                        onValueChange = { textDraft = it.take(160) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val boxId = editingBoxId
                        if (boxId != null) {
                            canvasBoxes = updateBoxTree(canvasBoxes, boxId) {
                                it.copy(content = textDraft)
                            }
                        }
                        textEditorOpen = false
                    }) {
                        Text("Apply")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { textEditorOpen = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (mathEditorOpen) {
            ModalBottomSheet(
                onDismissRequest = {
                    mathEditorOpen = false
                    selectedTool = PlaygroundTool.MOVE
                },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 620.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        if (editingGraph) "Graph a function" else "Math expression box",
                        style = MaterialTheme.typography.titleLarge
                    )
                    OutlinedTextField(
                        value = formulaDraft,
                        onValueChange = { if (it.length <= 160) formulaDraft = it },
                        label = {
                            Text(if (editingGraph) "y = f(x)" else "Math notation")
                        },
                        isError = formulaDraftError != null,
                        supportingText = {
                            Text(if (editingGraph) {
                                formulaDraftError
                                    ?: "Graph a function such as x^2 - 2*x or sin(x)."
                            } else {
                                "Write an equation or expression, for example x^2 = 2*x."
                            })
                        },
                        singleLine = editingGraph,
                        minLines = if (editingGraph) 1 else 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    MathKeypad(
                        graphMode = editingGraph,
                        onInput = { token ->
                            formulaDraft = if (token == "⌫") {
                                formulaDraft.dropLast(1)
                            } else if (token == "AC") {
                                ""
                            } else if (formulaDraft.length + token.length <= 160) {
                                formulaDraft + token
                            } else {
                                formulaDraft
                            }
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            mathEditorOpen = false
                            selectedTool = PlaygroundTool.MOVE
                        }) {
                            Text("Cancel")
                        }
                        TextButton(
                            onClick = {
                                val boxId = editingBoxId
                                if (boxId != null) {
                                    canvasBoxes = updateBoxTree(canvasBoxes, boxId) {
                                        it.copy(content = formulaDraft)
                                    }
                                }
                                if (editingGraph) expression = formulaDraft
                                mathEditorOpen = false
                                selectedTool = PlaygroundTool.MOVE
                            },
                            enabled = formulaDraft.isNotBlank() && formulaDraftError == null
                        ) {
                            Text("Apply")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MathKeypad(graphMode: Boolean, onInput: (String) -> Unit) {
    val rows = if (graphMode) {
        listOf(
            listOf("x", "π", "e", "^"),
            listOf("7", "8", "9", "/"),
            listOf("4", "5", "6", "*"),
            listOf("1", "2", "3", "-"),
            listOf("0", ".", "(", "+"),
            listOf(")", "sin(", "cos(", "sqrt("),
            listOf("tan(", "abs(", "ln(", "log("),
            listOf("exp(", "⌫", "AC", "·")
        )
    } else {
        listOf(
            listOf("x", "π", "e", "^"),
            listOf("7", "8", "9", "/"),
            listOf("4", "5", "6", "*"),
            listOf("1", "2", "3", "-"),
            listOf("0", ".", "(", "+"),
            listOf(")", "=", "√(", "²"),
            listOf("³", "≤", "≥", "≠"),
            listOf("sin(", "cos(", "tan(", "abs("),
            listOf("ln(", "log(", "exp(", "⌫"),
            listOf("AC", "·", ",", "−")
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row.forEach { key ->
                    OutlinedButton(
                        onClick = { onInput(if (key == "·") "*" else key) },
                        modifier = Modifier.weight(1f).height(38.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        when (key) {
                            "⌫" -> Icon(Icons.Filled.Backspace, contentDescription = "Delete last character")
                            "AC" -> Icon(Icons.Filled.Clear, contentDescription = "Clear expression")
                            else -> Text(key, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
