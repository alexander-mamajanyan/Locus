package com.locus.app.data

import androidx.compose.runtime.mutableStateListOf
import com.locus.app.model.BuildParams
import com.locus.app.model.BuildType
import com.locus.app.model.ChatMessage
import com.locus.app.model.Conversation
import com.locus.app.model.MathBuild
import com.locus.app.model.ShapeType
import com.locus.app.model.UserProfile

/**
 * Single in-memory source of truth for this base app.
 *
 * This is intentionally NOT a real backend — there's no network call and
 * nothing survives a process restart. Swap it out for a Firestore/Room
 * repository (or both, with this as the local cache) once you're ready to
 * go further; the screens only depend on this object's public shape, not
 * on how it's implemented underneath.
 */
object LocusRepository {

    const val currentUsername = "lena.codes"

    val myProfile = UserProfile(
        username = currentUsername,
        bio = "AI + math class \u00B7 building things that move",
        builds = 2,
        followers = 284,
        following = 96
    )

    private val otherProfiles = mutableMapOf(
        "theo" to UserProfile("theo", "parabolas & projectiles", 18, 1200, 140),
        "maya_k" to UserProfile("maya_k", "sound, mapped to math", 34, 9800, 210, isFollowing = true),
        "priya" to UserProfile("priya", "geometry nerd, circle enjoyer", 22, 3100, 88),
        "sam" to UserProfile("sam", "unit circle propagandist", 11, 640, 120),
        "jorge" to UserProfile("jorge", "remix culture, but for equations", 9, 410, 75),
        "ana" to UserProfile("ana", "linear functions, mostly", 7, 290, 60)
    )

    fun profileFor(username: String): UserProfile =
        if (username == currentUsername) myProfile
        else otherProfiles.getOrPut(username) { UserProfile(username, "", 0, 0, 0) }

    fun toggleFollow(username: String) {
        val profile = profileFor(username)
        profile.isFollowing = !profile.isFollowing
        profile.followers += if (profile.isFollowing) 1 else -1
    }

    val feed = mutableStateListOf(
        MathBuild(
            id = "f0b", username = currentUsername, time = "1d",
            caption = "circle with a point marking where I want the tangent line",
            type = BuildType.CIRCLE, params = BuildParams(r = 2f),
            shapes = listOf(ShapeType.POINT),
            likes = 12, comments = 2, reposts = 0
        ),
        MathBuild(
            id = "f0a", username = currentUsername, time = "2d",
            caption = "first build on here, a sine wave to warm up",
            type = BuildType.SINE, params = BuildParams(a = 1.6f, b = 1f, c = 0f),
            likes = 31, comments = 4, reposts = 1
        ),
        MathBuild(
            id = "f1", username = "maya_k", verified = true, time = "2h",
            caption = "tuned the amplitude until it matched my guitar string",
            type = BuildType.SINE, params = BuildParams(a = 1.4f, b = 1.4f, c = 0f),
            likes = 341, comments = 28, reposts = 9
        ),
        MathBuild(
            id = "f2", username = "theo", time = "4h",
            caption = "projectile motion, but make it satisfying",
            type = BuildType.QUADRATIC, params = BuildParams(a = 0.6f, b = 0f, c = -2f),
            likes = 212, comments = 14, reposts = 4
        ),
        MathBuild(
            id = "f3", username = "priya", verified = true, time = "6h",
            caption = "when a triangle finally fits perfectly inside the circle",
            type = BuildType.CIRCLE, params = BuildParams(r = 2.2f),
            shapes = listOf(ShapeType.TRIANGLE),
            likes = 503, comments = 41, reposts = 22
        ),
        MathBuild(
            id = "f4", username = "sam", time = "9h",
            caption = "still the cleanest sine I've ever tuned",
            type = BuildType.SINE, params = BuildParams(a = 1f, b = 2f, c = 0f),
            likes = 178, comments = 9, reposts = 3
        ),
        MathBuild(
            id = "f5", username = "jorge", time = "12h",
            caption = "remixed this \u2014 steeper this time", remixOf = "theo",
            type = BuildType.QUADRATIC, params = BuildParams(a = 1.9f, b = 0f, c = -2.5f),
            likes = 96, comments = 6, reposts = 2
        ),
        MathBuild(
            id = "f6", username = "ana", time = "1d",
            caption = "slope = vibes",
            type = BuildType.LINEAR, params = BuildParams(a = 0.8f, b = 0.5f),
            likes = 64, comments = 3, reposts = 0
        )
    )

    fun addBuild(build: MathBuild) {
        feed.add(0, build)
        myProfile.builds += 1
    }

    val conversations = mutableStateListOf(
        Conversation(
            id = "c1", username = "theo", unread = true, time = "2m",
            messages = mutableStateListOf(
                ChatMessage(fromMe = false, text = "ok but does this actually match a thrown ball though"),
                ChatMessage(fromMe = true, text = "try lowering a, watch the curve flatten"),
                ChatMessage(fromMe = false, sharedBuildId = "f2")
            )
        ),
        Conversation(
            id = "c2", username = "maya_k", unread = true, time = "40m",
            messages = mutableStateListOf(
                ChatMessage(fromMe = false, text = "been tuning this for an hour lol"),
                ChatMessage(fromMe = false, sharedBuildId = "f1"),
                ChatMessage(fromMe = true, text = "a=1.4 sounds right for the low E string")
            )
        ),
        Conversation(
            id = "c3", username = "priya", unread = false, time = "3h",
            messages = mutableStateListOf(
                ChatMessage(fromMe = false, text = "took me way too long to get this inscribed right"),
                ChatMessage(fromMe = true, text = "worth it though, looks great")
            )
        )
    )

    fun conversationFor(username: String): Conversation =
        conversations.find { it.username == username }
            ?: Conversation(id = "c-$username", username = username, messages = mutableStateListOf()).also {
                conversations.add(0, it)
            }

    fun sendMessage(username: String, text: String, isMath: Boolean) {
        val convo = conversationFor(username)
        convo.messages.add(ChatMessage(fromMe = true, text = text, isMath = isMath))
        convo.preview = text
        convo.unread = false
    }

    fun buildById(id: String): MathBuild? = feed.find { it.id == id }
}
