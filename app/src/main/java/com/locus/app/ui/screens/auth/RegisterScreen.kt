package com.locus.app.ui.screens.auth

import android.util.Patterns
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val USERNAME_REGEX = Regex("^[A-Za-z0-9._]{3,20}\$")

/**
 * Registration page. Validates the form shape locally (username format,
 * email format, password length, matching confirmation) and then hands
 * the values to [onRegister]. Creating a real account is the caller's job.
 *
 * [isLoading] and [serverError] let a real backend drive the UI later
 * ("email already in use", spinner on the button) with no changes here.
 */
@Composable
fun RegisterScreen(
    onRegister: (username: String, email: String, password: String) -> Unit,
    onGoToLogin: () -> Unit,
    isLoading: Boolean = false,
    serverError: String? = null
) {
    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }

    fun usernameProblem(): String? =
        if (!USERNAME_REGEX.matches(username.trim())) {
            "3-20 characters: letters, numbers, . and _"
        } else null

    fun emailProblem(): String? = when {
        email.isBlank() -> "Enter your email."
        !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "That doesn't look like an email address."
        else -> null
    }

    fun passwordProblem(): String? =
        if (password.length < 6) "Use at least 6 characters." else null

    fun confirmProblem(): String? =
        if (confirm != password) "Passwords don't match." else null

    fun submit() {
        submitted = true
        val allGood = usernameProblem() == null &&
            emailProblem() == null &&
            passwordProblem() == null &&
            confirmProblem() == null
        if (allGood) {
            onRegister(username.trim(), email.trim(), password)
        }
    }

    AuthColumn {
        Spacer(Modifier.height(16.dp))
        AuthHeader(subtitle = "Build, share, and remix math")
        Spacer(Modifier.height(28.dp))

        AuthTextField(
            value = username,
            onValueChange = { username = it },
            label = "Username",
            errorText = if (submitted) usernameProblem() else null,
            enabled = !isLoading
        )
        Spacer(Modifier.height(12.dp))
        AuthTextField(
            value = email,
            onValueChange = { email = it },
            label = "Email",
            keyboardType = KeyboardType.Email,
            errorText = if (submitted) emailProblem() else null,
            enabled = !isLoading
        )
        Spacer(Modifier.height(12.dp))
        PasswordTextField(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            imeAction = ImeAction.Next,
            errorText = if (submitted) passwordProblem() else null,
            enabled = !isLoading
        )
        Spacer(Modifier.height(12.dp))
        PasswordTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = "Confirm password",
            errorText = if (submitted) confirmProblem() else null,
            enabled = !isLoading,
            onDone = { submit() }
        )

        FormError(serverError)

        Spacer(Modifier.height(20.dp))
        AuthButton(text = "Create account", loading = isLoading, onClick = { submit() })

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Already have an account?",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
            )
            TextButton(onClick = onGoToLogin, enabled = !isLoading) {
                Text("Log in")
            }
        }
    }
}
