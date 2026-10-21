package com.locus.app.ui.screens.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.UserProfileChangeRequest

/**
 * Hosts the login and registration pages and switches between them.
 */
@Composable
fun AuthScreen(onAuthenticated: () -> Unit) {
    var showRegister by rememberSaveable { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var serverError by remember { mutableStateOf<String?>(null) }
    val auth = remember { FirebaseAuth.getInstance() }

    fun showFailure(exception: Exception?) {
        val firebaseCode = (exception as? FirebaseAuthException)
            ?.errorCode
            ?.lowercase()
            ?.replace('_', '-')
            ?.removePrefix("error-")
        serverError = when (firebaseCode) {
            "invalid-email" -> "Enter a valid email address."
            "invalid-credential", "user-not-found", "wrong-password" ->
                "The email or password is incorrect."
            "email-already-in-use" -> "An account with this email already exists."
            "weak-password" -> "Choose a stronger password."
            "too-many-requests" -> "Too many attempts. Please try again later."
            "network-request-failed" -> "Couldn't connect. Check your internet connection and try again."
            else -> exception?.localizedMessage ?: "Authentication failed. Please try again."
        }
        isLoading = false
    }

    fun login(email: String, password: String) {
        isLoading = true
        serverError = null
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onAuthenticated()
                } else {
                    showFailure(task.exception)
                }
                isLoading = false
            }
    }

    fun register(username: String, email: String, password: String) {
        isLoading = true
        serverError = null
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    showFailure(task.exception)
                    return@addOnCompleteListener
                }

                val user = task.result.user
                if (user == null) {
                    showFailure(IllegalStateException("Firebase created the account without returning a user."))
                    return@addOnCompleteListener
                }
                val profileUpdate = UserProfileChangeRequest.Builder()
                    .setDisplayName(username)
                    .build()
                user.updateProfile(profileUpdate)
                    .addOnCompleteListener { profileTask ->
                        if (profileTask.isSuccessful) {
                            onAuthenticated()
                        } else {
                            val detail = profileTask.exception?.localizedMessage
                                ?: "Please log in to continue."
                            showFailure(
                                IllegalStateException(
                                    "Your account was created, but the username couldn't be saved. $detail"
                                )
                            )
                        }
                        isLoading = false
                    }
            }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (showRegister) {
            // System back on the registration page returns to login instead of closing the app.
            BackHandler { showRegister = false }
            RegisterScreen(
                onRegister = ::register,
                onGoToLogin = {
                    serverError = null
                    showRegister = false
                },
                isLoading = isLoading,
                serverError = serverError
            )
        } else {
            LoginScreen(
                onLogin = ::login,
                onGoToRegister = {
                    serverError = null
                    showRegister = true
                },
                isLoading = isLoading,
                serverError = serverError
            )
        }
    }
}
