package com.locus.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.locus.app.ui.screens.auth.AuthScreen

/**
 * Set to false while you're working on other screens to skip the login
 * pages and launch straight into the app.
 */
private const val REQUIRE_LOGIN = true

@Composable
fun LocusRoot() {
    // In-memory only for now: the app shows the login page on every fresh launch.
    var signedIn by rememberSaveable { mutableStateOf(!REQUIRE_LOGIN) }

    if (signedIn) {
        LocusApp()
    } else {
        AuthScreen(onAuthenticated = { signedIn = true })
    }
}
