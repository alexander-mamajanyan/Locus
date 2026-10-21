package com.locus.app.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList

enum class BuildType { LINEAR, QUADRATIC, SINE, CIRCLE, FUNCTION }
enum class ShapeType { CIRCLE, TRIANGLE, POINT }
enum class GeometryType { SEGMENT, CIRCLE }
enum class CanvasBoxType { GRAPH, EXPRESSION, TEXT, GEOMETRY, CONTAINER }

data class CanvasBox(
    val id: String,
    val type: CanvasBoxType,
    val x: Float,
    val y: Float,
    val width: Float = 3.4f,
    val height: Float = 2.5f,
    val rotation: Float = 0f,
    val content: String = "",
    val children: List<CanvasBox> = emptyList()
)

data class GeometryPoint(val id: String, val x: Float, val y: Float)

data class CanvasText(
    val id: String,
    val text: String,
    val x: Float,
    val y: Float
)

data class GeometryObject(
    val type: GeometryType,
    val firstPointId: String,
    val secondPointId: String
)

/**
 * The "coordinates" of a build. Which field is the live/editable one
 * depends on [BuildType] (see [MathBuild.sliderValue], [sliderLabelFor]):
 * sine & quadratic vary `a`, linear varies `b`, circle varies `r`.
 * The other fields stay fixed once a build is posted.
 */
data class BuildParams(
    val a: Float = 1f,
    val b: Float = 1f,
    val c: Float = 0f,
    val r: Float = 2f
)

/**
 * One math "build" — the equivalent of an Instagram post, except the
 * post is a live, re-drawable object instead of a flat image.
 */
class MathBuild(
    val id: String,
    username: String,
    val ownerId: String = "",
    val verified: Boolean = false,
    val time: String = "now",
    caption: String,
    val type: BuildType,
    val params: BuildParams,
    val shapes: List<ShapeType> = emptyList(),
    val expression: String = "sin(x)",
    val geometryPoints: List<GeometryPoint> = emptyList(),
    val geometryObjects: List<GeometryObject> = emptyList(),
    val canvasTexts: List<CanvasText> = emptyList(),
    val canvasBoxes: List<CanvasBox> = emptyList(),
    val showBaseFunction: Boolean = true,
    val remixOf: String? = null,
    likes: Int = 0,
    comments: Int = 0,
    reposts: Int = 0
) {
    var username by mutableStateOf(username)
    var caption by mutableStateOf(caption)
    var likes by mutableStateOf(likes)
    var comments by mutableStateOf(comments)
    var reposts by mutableStateOf(reposts)
    var liked by mutableStateOf(false)
    var saved by mutableStateOf(false)
    var reposted by mutableStateOf(false)

    /** The single live-editable dimension shown as a slider on this build's card. */
    var sliderValue by mutableStateOf(
        when (type) {
            BuildType.SINE, BuildType.QUADRATIC -> params.a
            BuildType.LINEAR -> params.b
            BuildType.CIRCLE -> params.r
            BuildType.FUNCTION -> params.a
        }
    )
}

fun MathBuild.formulaText(): String = when (type) {
    BuildType.SINE -> "y = %.1f\u00B7sin(%.1fx + %.1f)".format(sliderValue, params.b, params.c)
    BuildType.QUADRATIC -> "y = %.1fx\u00B2 + %.1fx + %.1f".format(sliderValue, params.b, params.c)
    BuildType.LINEAR -> "y = %.1fx + %.1f".format(params.a, sliderValue)
    BuildType.CIRCLE -> "x\u00B2 + y\u00B2 = %.1f\u00B2".format(sliderValue)
    BuildType.FUNCTION -> "y = $expression"
}

fun sliderRangeFor(type: BuildType): ClosedFloatingPointRange<Float> = when (type) {
    BuildType.SINE -> 0.3f..3f
    BuildType.QUADRATIC -> -2f..2f
    BuildType.LINEAR -> -3f..3f
    BuildType.CIRCLE -> 0.5f..4f
    BuildType.FUNCTION -> -3f..3f
}

fun sliderLabelFor(type: BuildType): String = when (type) {
    BuildType.SINE, BuildType.QUADRATIC -> "a"
    BuildType.LINEAR -> "b"
    BuildType.CIRCLE -> "r"
    BuildType.FUNCTION -> "a"
}

fun BuildType.label(): String = when (this) {
    BuildType.SINE -> "Sine"
    BuildType.LINEAR -> "Linear"
    BuildType.QUADRATIC -> "Quadratic"
    BuildType.CIRCLE -> "Circle"
    BuildType.FUNCTION -> "Formula"
}

data class ChatMessage(
    val fromMe: Boolean,
    val text: String? = null,
    val isMath: Boolean = false,
    val sharedBuildId: String? = null
)

data class PostComment(
    val id: String,
    val authorId: String,
    val username: String,
    val text: String,
    val time: String,
    val parentId: String? = null,
    val likes: Int = 0,
    val replies: Int = 0,
    val liked: Boolean = false
)

class Conversation(
    val id: String,
    val username: String,
    unread: Boolean = false,
    val time: String = "now",
    val messages: SnapshotStateList<ChatMessage>
) {
    var unread by mutableStateOf(unread)
    var preview by mutableStateOf(messages.lastOrNull()?.text ?: "Shared a build")
}

class UserProfile(
    username: String,
    bio: String,
    builds: Int,
    followers: Int,
    following: Int,
    isFollowing: Boolean = false,
    uid: String = "",
    photoUrl: String = ""
) {
    var username by mutableStateOf(username)
    var uid by mutableStateOf(uid)
    var bio by mutableStateOf(bio)
    var photoUrl by mutableStateOf(photoUrl)
    var builds by mutableStateOf(builds)
    var followers by mutableStateOf(followers)
    var following by mutableStateOf(following)
    var isFollowing by mutableStateOf(isFollowing)
}
