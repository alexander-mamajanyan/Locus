package com.locus.app.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList

enum class BuildType { LINEAR, QUADRATIC, SINE, CIRCLE }
enum class ShapeType { CIRCLE, TRIANGLE, POINT }

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
    val username: String,
    val verified: Boolean = false,
    val time: String = "now",
    caption: String,
    val type: BuildType,
    val params: BuildParams,
    val shapes: List<ShapeType> = emptyList(),
    val remixOf: String? = null,
    likes: Int = 0,
    comments: Int = 0,
    reposts: Int = 0
) {
    var caption by mutableStateOf(caption)
    var likes by mutableStateOf(likes)
    var comments by mutableStateOf(comments)
    var reposts by mutableStateOf(reposts)
    var liked by mutableStateOf(false)
    var saved by mutableStateOf(false)

    /** The single live-editable dimension shown as a slider on this build's card. */
    var sliderValue by mutableStateOf(
        when (type) {
            BuildType.SINE, BuildType.QUADRATIC -> params.a
            BuildType.LINEAR -> params.b
            BuildType.CIRCLE -> params.r
        }
    )
}

fun MathBuild.formulaText(): String = when (type) {
    BuildType.SINE -> "y = %.1f\u00B7sin(%.1fx + %.1f)".format(sliderValue, params.b, params.c)
    BuildType.QUADRATIC -> "y = %.1fx\u00B2 + %.1fx + %.1f".format(sliderValue, params.b, params.c)
    BuildType.LINEAR -> "y = %.1fx + %.1f".format(params.a, sliderValue)
    BuildType.CIRCLE -> "x\u00B2 + y\u00B2 = %.1f\u00B2".format(sliderValue)
}

fun sliderRangeFor(type: BuildType): ClosedFloatingPointRange<Float> = when (type) {
    BuildType.SINE -> 0.3f..3f
    BuildType.QUADRATIC -> -2f..2f
    BuildType.LINEAR -> -3f..3f
    BuildType.CIRCLE -> 0.5f..4f
}

fun sliderLabelFor(type: BuildType): String = when (type) {
    BuildType.SINE, BuildType.QUADRATIC -> "a"
    BuildType.LINEAR -> "b"
    BuildType.CIRCLE -> "r"
}

fun BuildType.label(): String = when (this) {
    BuildType.SINE -> "Sine"
    BuildType.LINEAR -> "Linear"
    BuildType.QUADRATIC -> "Quadratic"
    BuildType.CIRCLE -> "Circle"
}

data class ChatMessage(
    val fromMe: Boolean,
    val text: String? = null,
    val isMath: Boolean = false,
    val sharedBuildId: String? = null
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
    val username: String,
    val bio: String,
    builds: Int,
    followers: Int,
    following: Int,
    isFollowing: Boolean = false
) {
    var builds by mutableStateOf(builds)
    var followers by mutableStateOf(followers)
    var following by mutableStateOf(following)
    var isFollowing by mutableStateOf(isFollowing)
}
