package com.locus.app.ui.screens.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Hosts the login and registration pages and switches between them.
 *
 * STUB: nothing is checked against a real account yet. Any form that passes
 * validation calls [onAuthenticated]. When Firebase is wired in, replace the
 * two `onLogin` / `onRegister` lambdas below with real sign-in / sign-up
 * calls, and only invoke [onAuthenticated] when they succeed (pass their
 * progress and errors into the screens' `isLoading` / `serverError`).
 */
@Composable
fun AuthScreen(onAuthenticated: () -> Unit) {
    var showRegister by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (showRegister) {
            // System back on the registration page returns to login instead of closing the app.
            BackHandler { showRegister = false }
            RegisterScreen(
                onRegister = { _, _, _ -> onAuthenticated() },
                onGoToLogin = { showRegister = false }
            )
        } else {
            LoginScreen(
                onLogin = { _, _ -> onAuthenticated() },
                onGoToRegister = { showRegister = true }
            )
        }
    }
}
