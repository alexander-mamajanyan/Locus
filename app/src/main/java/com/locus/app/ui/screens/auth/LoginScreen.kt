package com.locus.app.ui.screens.auth

import android.util.Patterns
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Login page. It only validates the form shape (non-empty, looks like an
 * email) and then hands the values to [onLogin] — checking them against a
 * real account is the caller's job.
 *
 * [isLoading] and [serverError] exist so a real backend can drive the UI
 * later (spinner on the button, "wrong password" message) without touching
 * this file again.
 */
@Composable
fun LoginScreen(
    onLogin: (email: String, password: String) -> Unit,
    onGoToRegister: () -> Unit,
    isLoading: Boolean = false,
    serverError: String? = null
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }

    fun emailProblem(): String? = when {
        email.isBlank() -> "Enter your email."
        !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "That doesn't look like an email address."
        else -> null
    }

    fun passwordProblem(): String? =
        if (password.isEmpty()) "Enter your password." else null

    fun submit() {
        submitted = true
        if (emailProblem() == null && passwordProblem() == null) {
            onLogin(email.trim(), password)
        }
    }

    AuthColumn {
        Spacer(Modifier.height(24.dp))
        AuthHeader(subtitle = "Welcome back")
        Spacer(Modifier.height(32.dp))

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
            errorText = if (submitted) passwordProblem() else null,
            enabled = !isLoading,
            onDone = { submit() }
        )

        FormError(serverError)

        Spacer(Modifier.height(20.dp))
        AuthButton(text = "Log in", loading = isLoading, onClick = { submit() })

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "New to Locus?",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
            )
            TextButton(onClick = onGoToRegister, enabled = !isLoading) {
                Text("Create an account")
            }
        }
    }
}
