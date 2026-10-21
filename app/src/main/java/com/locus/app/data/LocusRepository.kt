package com.locus.app.data

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.Timestamp
import com.google.firebase.storage.FirebaseStorage
import com.locus.app.model.BuildParams
import com.locus.app.model.BuildType
import com.locus.app.model.CanvasBox
import com.locus.app.model.CanvasBoxType
import com.locus.app.model.ChatMessage
import com.locus.app.model.Conversation
import com.locus.app.model.GeometryObject
import com.locus.app.model.GeometryPoint
import com.locus.app.model.GeometryType
import com.locus.app.model.MathBuild
import com.locus.app.model.PostComment
import com.locus.app.model.ShapeType
import com.locus.app.model.UserProfile
import java.util.Date
import java.util.concurrent.TimeUnit

object LocusRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val users = db.collection("users")
    private val posts = db.collection("posts")
    private val userProfiles = mutableStateMapOf<String, UserProfile>()
    private val registrations = mutableListOf<ListenerRegistration>()
    private var followingUserIds by mutableStateOf(emptySet<String>())
    private var likedPostIds by mutableStateOf(emptySet<String>())
    private var repostedPostIds by mutableStateOf(emptySet<String>())
    private var savedPostIds by mutableStateOf(emptySet<String>())
    private var listeningUid: String? = null

    val currentUid: String?
        get() = auth.currentUser?.uid

    val currentUsername: String
        get() = auth.currentUser?.displayName
            ?.takeIf(String::isNotBlank)
            ?: auth.currentUser?.email?.substringBefore("@")
            ?: "user"

    val currentEmail: String?
        get() = auth.currentUser?.email

    val myProfile: UserProfile
        get() = profileFor(currentUid ?: currentUsername)

    var socialError by mutableStateOf<String?>(null)
        private set

    val feed = mutableStateListOf<MathBuild>()
    val conversations = mutableStateListOf<Conversation>()
    val profiles: List<UserProfile>
        get() = userProfiles.values.toList()
    val savedBuilds: List<MathBuild>
        get() = feed.filter { it.id in savedPostIds }

    fun clearSocialError() {
        socialError = null
    }

    fun reportProfileError(error: Exception) {
        reportError(error)
    }

    fun start() {
        val user = auth.currentUser ?: run {
            reportError(IllegalStateException("Sign in to load posts and profiles."))
            return
        }
        if (listeningUid == user.uid) return
        stop()
        followingUserIds = emptySet()
        likedPostIds = emptySet()
        repostedPostIds = emptySet()
        savedPostIds = emptySet()
        listeningUid = user.uid
        ensureUserDocument(user.uid, currentUsername)

        registrations += posts
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    reportError(error)
                    return@addSnapshotListener
                }
                val builds = snapshot?.documents.orEmpty().mapNotNull { document ->
                    runCatching { buildFromDocument(document.id, document.data.orEmpty()) }
                        .onFailure { error ->
                            reportError(
                                if (error is Exception) error
                                else IllegalStateException("Couldn't read post ${document.id}.", error)
                            )
                        }
                        .getOrNull()
                }
                feed.clear()
                builds.forEach(::applyPostEngagementState)
                feed.addAll(builds)
            }

        registrations += users.addSnapshotListener { snapshot, error ->
            if (error != null) {
                reportError(error)
                return@addSnapshotListener
            }
            snapshot?.documents.orEmpty().forEach { document ->
                cacheProfile(document)
            }
        }

        registrations += users.document(user.uid)
            .collection("following")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    reportError(error)
                    return@addSnapshotListener
                }
                followingUserIds = snapshot?.documents.orEmpty().map { it.id }.toSet()
                userProfiles.values.forEach { profile ->
                    profile.isFollowing = profile.uid in followingUserIds
                }
            }

        registrations += users.document(user.uid)
            .collection("likes")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    reportError(error)
                    return@addSnapshotListener
                }
                likedPostIds = snapshot?.documents.orEmpty().map { it.id }.toSet()
                feed.forEach(::applyPostEngagementState)
            }

        registrations += users.document(user.uid)
            .collection("reposts")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    reportError(error)
                    return@addSnapshotListener
                }
                repostedPostIds = snapshot?.documents.orEmpty().map { it.id }.toSet()
                feed.forEach(::applyPostEngagementState)
            }

        registrations += users.document(user.uid)
            .collection("savedPosts")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    reportError(error)
                    return@addSnapshotListener
                }
                savedPostIds = snapshot?.documents.orEmpty().map { it.id }.toSet()
                feed.forEach(::applyPostEngagementState)
            }
    }

    fun stop() {
        registrations.forEach(ListenerRegistration::remove)
        registrations.clear()
        listeningUid = null
    }

    private fun applyPostEngagementState(build: MathBuild) {
        build.liked = build.id in likedPostIds
        build.reposted = build.id in repostedPostIds
        build.saved = build.id in savedPostIds
        userProfiles[build.ownerId]?.let { build.username = it.username }
    }

    fun profileFor(userIdentity: String): UserProfile =
        userProfiles[userIdentity]
            ?: userProfiles.values.firstOrNull { it.username == userIdentity }
            ?: UserProfile(userIdentity, "", 0, 0, 0, uid = userIdentity)
                .also { userProfiles[userIdentity] = it }

    fun addBuild(build: MathBuild, onSuccess: () -> Unit) {
        socialError = null
        val user = auth.currentUser ?: return reportError(
            IllegalStateException("Sign in before creating a post.")
        )
        val userRef = users.document(user.uid)
        val postRef = posts.document(build.id)
        val postData = mapOf(
            "ownerId" to user.uid,
            "username" to currentUsername,
            "createdAt" to FieldValue.serverTimestamp(),
            "caption" to build.caption,
            "type" to build.type.name,
            "params" to mapOf(
                "a" to build.params.a,
                "b" to build.params.b,
                "c" to build.params.c,
                "r" to build.params.r
            ),
            "shapes" to build.shapes.map(ShapeType::name),
            "expression" to build.expression,
            "geometryPoints" to build.geometryPoints.map {
                mapOf("id" to it.id, "x" to it.x, "y" to it.y)
            },
            "geometryObjects" to build.geometryObjects.map {
                mapOf(
                    "type" to it.type.name,
                    "firstPointId" to it.firstPointId,
                    "secondPointId" to it.secondPointId
                )
            },
            "canvasBoxes" to build.canvasBoxes.map(::canvasBoxToMap),
            "showBaseFunction" to build.showBaseFunction,
            "likes" to 0,
            "comments" to 0,
            "reposts" to 0
        )
        val batch = db.batch()
        batch.set(postRef, postData)
        batch.set(
            userRef,
            mapOf(
                "username" to currentUsername,
                "bio" to userProfiles[user.uid]?.bio.orEmpty(),
                "builds" to FieldValue.increment(1L),
                "followersCount" to FieldValue.increment(0L),
                "followingCount" to FieldValue.increment(0L)
            ),
            SetOptions.merge()
        )
        batch.commit()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(::reportError)
    }

    fun toggleLike(build: MathBuild) {
        socialError = null
        val userId = currentUid ?: return reportError(
            IllegalStateException("Sign in before liking posts.")
        )
        val userLikeRef = users.document(userId).collection("likes").document(build.id)
        val postRef = posts.document(build.id)
        val postLikeRef = postRef.collection("likes").document(userId)

        db.runTransaction { transaction ->
            val userLike = transaction.get(userLikeRef)
            val post = transaction.get(postRef)
            check(post.exists()) { "This post no longer exists." }
            val likes = (post.getLong("likes") ?: 0L).coerceAtLeast(0)
            if (userLike.exists()) {
                transaction.delete(userLikeRef)
                transaction.delete(postLikeRef)
                transaction.update(postRef, "likes", (likes - 1).coerceAtLeast(0))
            } else {
                transaction.set(userLikeRef, mapOf("postId" to build.id))
                transaction.set(postLikeRef, mapOf("userId" to userId))
                transaction.update(postRef, "likes", likes + 1)
            }
            !userLike.exists()
        }.addOnSuccessListener { liked ->
            build.liked = liked
        }.addOnFailureListener { error ->
            reportError(error)
        }
    }

    fun toggleRepost(build: MathBuild) {
        socialError = null
        val userId = currentUid ?: return reportError(
            IllegalStateException("Sign in before reposting.")
        )
        val postRef = posts.document(build.id)
        val userRepostRef = users.document(userId).collection("reposts").document(build.id)
        val postRepostRef = postRef.collection("reposts").document(userId)
        db.runTransaction { transaction ->
            val post = transaction.get(postRef)
            val userRepost = transaction.get(userRepostRef)
            check(post.exists()) { "This post no longer exists." }
            val count = (post.getLong("reposts") ?: 0L).coerceAtLeast(0L)
            if (userRepost.exists()) {
                transaction.delete(userRepostRef)
                transaction.delete(postRepostRef)
                transaction.update(postRef, "reposts", (count - 1L).coerceAtLeast(0L))
                false
            } else {
                val repost = mapOf(
                    "postId" to build.id,
                    "userId" to userId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                transaction.set(userRepostRef, repost)
                transaction.set(postRepostRef, repost)
                transaction.update(postRef, "reposts", count + 1L)
                true
            }
        }.addOnSuccessListener { reposted ->
            build.reposted = reposted
        }.addOnFailureListener(::reportError)
    }

    fun toggleSave(build: MathBuild) {
        socialError = null
        val userId = currentUid ?: return reportError(
            IllegalStateException("Sign in before saving posts.")
        )
        val postRef = posts.document(build.id)
        val savedRef = users.document(userId).collection("savedPosts").document(build.id)
        db.runTransaction { transaction ->
            val post = transaction.get(postRef)
            val saved = transaction.get(savedRef)
            check(post.exists()) { "This post no longer exists." }
            if (saved.exists()) {
                transaction.delete(savedRef)
                false
            } else {
                transaction.set(
                    savedRef,
                    mapOf(
                        "postId" to build.id,
                        "savedAt" to FieldValue.serverTimestamp()
                    )
                )
                true
            }
        }.addOnSuccessListener { saved ->
            build.saved = saved
        }.addOnFailureListener(::reportError)
    }

    fun listenToComments(
        postId: String,
        onComments: (List<PostComment>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        val uid = currentUid ?: run {
            onError(IllegalStateException("Sign in to view comments."))
            return object : ListenerRegistration {
                override fun remove() = Unit
            }
        }
        var commentDocuments = emptyList<com.google.firebase.firestore.DocumentSnapshot>()
        var likedCommentIds = emptySet<String>()
        fun publish() {
            onComments(
                commentDocuments.mapNotNull { document ->
                    val authorId = document.getString("authorId") ?: return@mapNotNull null
                    val username = document.getString("username") ?: return@mapNotNull null
                    val text = document.getString("text") ?: return@mapNotNull null
                    val time = document.getTimestamp("createdAt")?.toDate()?.let(::timeAgo) ?: "now"
                    PostComment(
                        id = document.id,
                        authorId = authorId,
                        username = username,
                        text = text,
                        time = time,
                        parentId = document.getString("parentId"),
                        likes = (document.getLong("likes") ?: 0L).toInt(),
                        replies = (document.getLong("replies") ?: 0L).toInt(),
                        liked = document.id in likedCommentIds
                    )
                }
            )
        }
        val commentsRegistration = posts.document(postId)
            .collection("comments")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                commentDocuments = snapshot?.documents.orEmpty()
                publish()
            }
        val likesRegistration = users.document(uid)
            .collection("commentLikes")
            .whereEqualTo("postId", postId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                likedCommentIds = snapshot?.documents.orEmpty()
                    .mapNotNull { it.getString("commentId") }
                    .toSet()
                publish()
            }
        return object : ListenerRegistration {
            override fun remove() {
                commentsRegistration.remove()
                likesRegistration.remove()
            }
        }
    }

    fun addComment(
        postId: String,
        text: String,
        parentId: String? = null,
        onComplete: (Exception?) -> Unit
    ) {
        val content = text.trim()
        if (content.isEmpty() || content.length > 1000) {
            onComplete(IllegalArgumentException("Comments must be between 1 and 1000 characters."))
            return
        }
        val user = auth.currentUser
        if (user == null) {
            onComplete(IllegalStateException("Sign in before commenting."))
            return
        }

        val postRef = posts.document(postId)
        val userRef = users.document(user.uid)
        val commentRef = postRef.collection("comments").document()
        val parentRef = parentId?.let { postRef.collection("comments").document(it) }
        db.runTransaction { transaction ->
            val post = transaction.get(postRef)
            check(post.exists()) { "This post no longer exists." }
            val profile = transaction.get(userRef)
            val parent = parentRef?.let { transaction.get(it) }
            if (parentRef != null) {
                check(parent?.exists() == true && parent.getString("parentId") == null) {
                    "Replies can only be added to a top-level comment."
                }
            }
            val username = profile.getString("username")
                ?.takeIf(String::isNotBlank)
                ?: currentUsername
            if (!profile.exists()) {
                transaction.set(
                    userRef,
                    mapOf(
                        "username" to username,
                        "bio" to "",
                        "photoUrl" to "",
                        "builds" to 0,
                        "followersCount" to 0,
                        "followingCount" to 0
                    )
                )
            }
            transaction.set(
                commentRef,
                mapOf(
                    "authorId" to user.uid,
                    "username" to username,
                    "text" to content,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "likes" to 0,
                    "replies" to 0,
                    "parentId" to parentId
                )
            )
            if (parentRef != null) {
                transaction.update(
                    parentRef,
                    mapOf(
                        "replies" to ((parent?.getLong("replies") ?: 0L) + 1L),
                        "lastReplyId" to commentRef.id
                    )
                )
            }
            transaction.update(
                postRef,
                mapOf(
                    "comments" to ((post.getLong("comments") ?: 0L) + 1L),
                    "lastCommentId" to commentRef.id
                )
            )
        }.addOnSuccessListener {
            onComplete(null)
        }.addOnFailureListener { error ->
            onComplete(error)
        }
    }

    fun toggleCommentLike(postId: String, commentId: String, onComplete: (Exception?) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onComplete(IllegalStateException("Sign in before liking comments."))
            return
        }
        val commentRef = posts.document(postId).collection("comments").document(commentId)
        val commentLikeRef = commentRef.collection("likes").document(user.uid)
        val userLikeRef = users.document(user.uid).collection("commentLikes").document(commentId)
        db.runTransaction { transaction ->
            val comment = transaction.get(commentRef)
            check(comment.exists()) { "This comment no longer exists." }
            val commentLike = transaction.get(commentLikeRef)
            val count = (comment.getLong("likes") ?: 0L).coerceAtLeast(0L)
            if (commentLike.exists()) {
                transaction.delete(commentLikeRef)
                transaction.delete(userLikeRef)
                transaction.update(commentRef, "likes", (count - 1L).coerceAtLeast(0L))
            } else {
                transaction.set(commentLikeRef, mapOf("userId" to user.uid))
                transaction.set(
                    userLikeRef,
                    mapOf("postId" to postId, "commentId" to commentId)
                )
                transaction.update(commentRef, "likes", count + 1L)
            }
        }.addOnSuccessListener {
            onComplete(null)
        }.addOnFailureListener { error ->
            onComplete(error)
        }
    }

    fun toggleFollow(userIdentity: String) {
        socialError = null
        val userId = currentUid ?: return reportError(
            IllegalStateException("Sign in before following users.")
        )
        val profile = profileFor(userIdentity)
        val targetId = profile.uid
        if (targetId.isBlank()) {
            users.document(userIdentity).get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        if (cacheProfile(snapshot)) toggleFollow(snapshot.id)
                    } else {
                        users.whereEqualTo("username", userIdentity).limit(2).get()
                            .addOnSuccessListener { matches ->
                                val match = matches.documents.singleOrNull()
                                if (match == null) {
                                    reportError(
                                        IllegalStateException(
                                            if (matches.documents.isEmpty()) {
                                                "This user profile is not available yet."
                                            } else {
                                                "This username matches more than one profile."
                                            }
                                        )
                                    )
                                } else {
                                    if (cacheProfile(match)) toggleFollow(match.id)
                                }
                            }
                            .addOnFailureListener(::reportError)
                    }
                }
                .addOnFailureListener(::reportError)
            return
        }
        if (targetId == userId) return

        val followingRef = users.document(userId).collection("following").document(targetId)
        val followerRef = users.document(targetId).collection("followers").document(userId)
        val ownProfileRef = users.document(userId)
        val targetProfileRef = users.document(targetId)

        db.runTransaction { transaction ->
            val following = transaction.get(followingRef)
            val ownProfile = transaction.get(ownProfileRef)
            val targetProfile = transaction.get(targetProfileRef)
            check(targetProfile.exists()) { "This user profile no longer exists." }
            val wasFollowing = following.exists()
            val ownCount = (ownProfile.getLong("followingCount") ?: 0L).coerceAtLeast(0)
            val targetCount = (targetProfile.getLong("followersCount") ?: 0L).coerceAtLeast(0)
            if (wasFollowing) {
                transaction.delete(followingRef)
                transaction.delete(followerRef)
                transaction.update(
                    ownProfileRef,
                    mapOf(
                        "lastFollowingUserId" to targetId,
                        "followingCount" to (ownCount - 1).coerceAtLeast(0)
                    )
                )
                transaction.update(targetProfileRef, "followersCount", (targetCount - 1).coerceAtLeast(0))
            } else {
                transaction.set(followingRef, mapOf("userId" to targetId))
                transaction.set(followerRef, mapOf("userId" to userId))
                transaction.update(
                    ownProfileRef,
                    mapOf(
                        "lastFollowingUserId" to targetId,
                        "followingCount" to ownCount + 1
                    )
                )
                transaction.update(targetProfileRef, "followersCount", targetCount + 1)
            }
            !wasFollowing
        }.addOnSuccessListener { following ->
            profile.isFollowing = following
        }.addOnFailureListener(::reportError)
    }

    fun listenToUserPostIds(
        userId: String,
        collection: String,
        onPostIds: (Set<String>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        require(collection == "reposts") { "Unsupported profile collection." }
        return users.document(userId).collection(collection)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                onPostIds(snapshot?.documents.orEmpty().map { it.id }.toSet())
            }
    }

    fun listenToRelatedProfiles(
        userId: String,
        relation: String,
        onProfiles: (List<UserProfile>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        require(relation == "followers" || relation == "following") {
            "Unsupported profile relationship."
        }
        return users.document(userId).collection(relation)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                val ids = snapshot?.documents.orEmpty().map { it.id }
                if (ids.isEmpty()) {
                    onProfiles(emptyList())
                    return@addSnapshotListener
                }
                val loadedProfiles = mutableMapOf<String, UserProfile>()
                val completedIds = mutableSetOf<String>()
                ids.forEach { relatedUid ->
                    users.document(relatedUid).get()
                        .addOnSuccessListener { document ->
                            if (document.exists() && cacheProfile(document)) {
                                loadedProfiles[relatedUid] = userProfiles.getValue(relatedUid)
                            }
                            completedIds += relatedUid
                            if (completedIds.size == ids.size) {
                                onProfiles(ids.mapNotNull(loadedProfiles::get))
                            }
                        }
                        .addOnFailureListener(onError)
                }
            }
    }

    fun updateProfile(
        username: String,
        bio: String,
        photoUrl: String,
        onComplete: (Exception?) -> Unit
    ) {
        val user = auth.currentUser
        if (user == null) {
            onComplete(IllegalStateException("Sign in to edit your profile."))
            return
        }
        val normalizedUsername = username.trim()
        val normalizedBio = bio.trim()
        if (!normalizedUsername.matches(Regex("[A-Za-z0-9_]{3,20}"))) {
            onComplete(IllegalArgumentException("Username must be 3–20 letters, numbers, or underscores."))
            return
        }
        if (normalizedBio.length > 160) {
            onComplete(IllegalArgumentException("Bio must be 160 characters or fewer."))
            return
        }

        users.whereEqualTo("username", normalizedUsername).get()
            .addOnSuccessListener { matches ->
                if (matches.documents.any { it.id != user.uid }) {
                    onComplete(IllegalArgumentException("That username is already taken."))
                    return@addOnSuccessListener
                }
                users.document(user.uid).set(
                    mapOf(
                        "username" to normalizedUsername,
                        "bio" to normalizedBio,
                        "photoUrl" to photoUrl
                    ),
                    SetOptions.merge()
                ).addOnSuccessListener {
                    val request = UserProfileChangeRequest.Builder()
                        .setDisplayName(normalizedUsername)
                        .setPhotoUri(photoUrl.takeIf(String::isNotBlank)?.let(Uri::parse))
                        .build()
                    user.updateProfile(request).addOnCompleteListener { task ->
                        val profile = userProfiles[user.uid]
                        if (profile != null) {
                            profile.username = normalizedUsername
                            profile.bio = normalizedBio
                            profile.photoUrl = photoUrl
                        }
                        feed.filter { it.ownerId == user.uid }
                            .forEach { it.username = normalizedUsername }
                        if (task.isSuccessful) {
                            onComplete(null)
                        } else {
                            onComplete(
                                task.exception
                                    ?: IllegalStateException("Profile saved, but Firebase Auth didn't update the username.")
                            )
                        }
                    }
                }.addOnFailureListener(onComplete)
            }
            .addOnFailureListener(onComplete)
    }

    fun uploadProfilePhoto(uri: Uri, onComplete: (String?, Exception?) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onComplete(null, IllegalStateException("Sign in to change your profile photo."))
            return
        }
        val photoRef = storage.reference.child("profilePictures/${user.uid}/profile")
        photoRef.putFile(uri)
            .continueWithTask { task ->
                if (!task.isSuccessful) {
                    throw task.exception ?: IllegalStateException("Photo upload failed.")
                }
                photoRef.downloadUrl
            }
            .addOnSuccessListener { downloadUri -> onComplete(downloadUri.toString(), null) }
            .addOnFailureListener { error -> onComplete(null, error) }
    }

    fun sendPasswordReset(onComplete: (Exception?) -> Unit) {
        val email = currentEmail
        if (email.isNullOrBlank()) {
            onComplete(IllegalStateException("This account doesn't have an email address."))
            return
        }
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { onComplete(null) }
            .addOnFailureListener(onComplete)
    }

    fun signOut() {
        stop()
        auth.signOut()
    }

    fun conversationFor(username: String): Conversation =
        conversations.find { it.username == username }
            ?: Conversation(
                id = "c-$username",
                username = username,
                messages = mutableStateListOf()
            ).also { conversations.add(0, it) }

    fun sendMessage(username: String, text: String, isMath: Boolean) {
        val convo = conversationFor(username)
        convo.messages.add(ChatMessage(fromMe = true, text = text, isMath = isMath))
        convo.preview = text
        convo.unread = false
    }

    fun buildById(id: String): MathBuild? = feed.find { it.id == id }

    private fun ensureUserDocument(uid: String, username: String) {
        val reference = users.document(uid)
        db.runTransaction { transaction ->
            if (!transaction.get(reference).exists()) {
                transaction.set(
                    reference,
                    mapOf(
                        "username" to username,
                        "bio" to "",
                        "photoUrl" to "",
                        "builds" to 0,
                        "followersCount" to 0,
                        "followingCount" to 0
                    )
                )
            }
        }.addOnFailureListener(::reportError)
    }

    private fun cacheProfile(snapshot: com.google.firebase.firestore.DocumentSnapshot): Boolean {
        val username = snapshot.getString("username")
        if (username == null) {
            reportError(IllegalStateException("This user profile is incomplete."))
            return false
        }
        val profile = userProfiles[snapshot.id]
            ?: userProfiles.values.firstOrNull { it.username == username }
            ?: UserProfile(username, "", 0, 0, 0, uid = snapshot.id)
        userProfiles[snapshot.id] = profile
        profile.uid = snapshot.id
        profile.username = username
        profile.bio = snapshot.getString("bio").orEmpty()
        profile.photoUrl = snapshot.getString("photoUrl").orEmpty()
        profile.builds = (snapshot.getLong("builds") ?: 0L).toInt()
        profile.followers = (snapshot.getLong("followersCount") ?: 0L).toInt()
        profile.following = (snapshot.getLong("followingCount") ?: 0L).toInt()
        profile.isFollowing = snapshot.id in followingUserIds
        feed.filter { it.ownerId == snapshot.id }.forEach { it.username = username }
        return true
    }

    private fun buildFromDocument(id: String, data: Map<String, Any>): MathBuild {
        val params = data["params"] as? Map<*, *> ?: emptyMap<Any, Any>()
        val type = data["type"] as? String
            ?: throw IllegalArgumentException("Post $id is missing its build type.")
        val createdAt = (data["createdAt"] as? Timestamp)?.toDate()
        val ownerId = data["ownerId"] as? String ?: ""
        val username = data["username"] as? String ?: "unknown"
        val build = MathBuild(
            id = id,
            username = username,
            ownerId = ownerId,
            time = createdAt?.let(::timeAgo) ?: "now",
            caption = data["caption"] as? String ?: "",
            type = BuildType.valueOf(type),
            params = BuildParams(
                a = params.float("a", 1f),
                b = params.float("b", 1f),
                c = params.float("c", 0f),
                r = params.float("r", 2f)
            ),
            shapes = (data["shapes"] as? List<*>)?.mapNotNull {
                (it as? String)?.let { name -> runCatching { ShapeType.valueOf(name) }.getOrNull() }
            }.orEmpty(),
            expression = data["expression"] as? String ?: "sin(x)",
            geometryPoints = (data["geometryPoints"] as? List<*>)?.mapNotNull { item ->
                val fields = item as? Map<*, *> ?: return@mapNotNull null
                val pointId = fields["id"] as? String ?: return@mapNotNull null
                GeometryPoint(pointId, fields.float("x", 0f), fields.float("y", 0f))
            }.orEmpty(),
            geometryObjects = (data["geometryObjects"] as? List<*>)?.mapNotNull { item ->
                val fields = item as? Map<*, *> ?: return@mapNotNull null
                val objectType = (fields["type"] as? String)
                    ?.let { runCatching { GeometryType.valueOf(it) }.getOrNull() }
                    ?: return@mapNotNull null
                val first = fields["firstPointId"] as? String ?: return@mapNotNull null
                val second = fields["secondPointId"] as? String ?: return@mapNotNull null
                GeometryObject(objectType, first, second)
            }.orEmpty(),
            canvasBoxes = (data["canvasBoxes"] as? List<*>)
                ?.mapNotNull(::canvasBoxFromMap).orEmpty(),
            showBaseFunction = data["showBaseFunction"] as? Boolean ?: true,
            likes = (data["likes"] as? Number)?.toInt() ?: 0,
            comments = (data["comments"] as? Number)?.toInt() ?: 0,
            reposts = (data["reposts"] as? Number)?.toInt() ?: 0
        )
        applyPostEngagementState(build)
        return build
    }

    private fun Map<*, *>.float(key: String, default: Float): Float =
        (this[key] as? Number)?.toFloat() ?: default

    private fun canvasBoxToMap(box: CanvasBox): Map<String, Any> = mapOf(
        "id" to box.id,
        "type" to box.type.name,
        "x" to box.x,
        "y" to box.y,
        "width" to box.width,
        "height" to box.height,
        "rotation" to box.rotation,
        "content" to box.content,
        "children" to box.children.map(::canvasBoxToMap)
    )

    private fun canvasBoxFromMap(item: Any?): CanvasBox? {
        val fields = item as? Map<*, *> ?: return null
        val boxId = fields["id"] as? String ?: return null
        val boxType = (fields["type"] as? String)
            ?.let { runCatching { CanvasBoxType.valueOf(it) }.getOrNull() }
            ?: return null
        return CanvasBox(
            id = boxId,
            type = boxType,
            x = fields.float("x", 0f),
            y = fields.float("y", 0f),
            width = fields.float("width", 3.4f),
            height = fields.float("height", 2.5f),
            rotation = fields.float("rotation", 0f),
            content = fields["content"] as? String ?: "",
            children = (fields["children"] as? List<*>)
                ?.mapNotNull(::canvasBoxFromMap).orEmpty()
        )
    }

    private fun timeAgo(date: Date): String {
        val elapsed = (System.currentTimeMillis() - date.time).coerceAtLeast(0)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed)
        return when {
            minutes < 1 -> "now"
            minutes < 60 -> "${minutes}m"
            minutes < 24 * 60 -> "${TimeUnit.MILLISECONDS.toHours(elapsed)}h"
            else -> "${TimeUnit.MILLISECONDS.toDays(elapsed)}d"
        }
    }

    private fun reportError(error: Exception) {
        socialError = error.localizedMessage ?: "Couldn't complete that action. Please try again."
    }
}
