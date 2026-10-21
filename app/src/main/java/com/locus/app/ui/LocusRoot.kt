package com.locus.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import com.locus.app.ui.screens.auth.AuthScreen

/**
 * Set to false while you're working on other screens to skip the login
 * pages and launch straight into the app.
 */
private const val REQUIRE_LOGIN = true

@Composable
fun LocusRoot() {
    var signedIn by remember {
        mutableStateOf(!REQUIRE_LOGIN || FirebaseAuth.getInstance().currentUser != null)
    }

    if (signedIn) {
        LocusApp(onSignOut = { signedIn = false })
    } else {
        AuthScreen(onAuthenticated = { signedIn = true })
    }
}
