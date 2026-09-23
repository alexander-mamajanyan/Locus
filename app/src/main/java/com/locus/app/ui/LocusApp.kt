package com.locus.app.ui

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.locus.app.data.LocusRepository
import com.locus.app.ui.components.LocusBottomBar
import com.locus.app.ui.components.LocusTab
import com.locus.app.ui.screens.builder.BuilderScreen
import com.locus.app.ui.screens.home.HomeScreen
import com.locus.app.ui.screens.messages.MessagesScreen
import com.locus.app.ui.screens.messages.ThreadScreen
import com.locus.app.ui.screens.profile.ProfileScreen

private val TOP_LEVEL_ROUTES = setOf("home", "messages", "builder", "profile/{username}")

@Composable
fun LocusApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in TOP_LEVEL_ROUTES

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                val currentTab = when {
                    currentRoute == "home" -> LocusTab.Home
                    currentRoute == "messages" -> LocusTab.Messages
                    currentRoute == "builder" -> LocusTab.Builder
                    currentRoute?.startsWith("profile") == true -> LocusTab.Profile
                    else -> LocusTab.Home
                }
                LocusBottomBar(current = currentTab) { tab ->
                    val route = when (tab) {
                        LocusTab.Home -> "home"
                        LocusTab.Messages -> "messages"
                        LocusTab.Builder -> "builder"
                        LocusTab.Profile -> "profile/${LocusRepository.currentUsername}"
                    }
                    navController.navigate(route) {
                        popUpTo("home") { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("home") {
                HomeScreen(onOpenProfile = { username -> navController.navigate("profile/$username") })
            }
            composable("messages") {
                MessagesScreen(onOpenThread = { username -> navController.navigate("thread/$username") })
            }
            composable(
                route = "thread/{username}",
                arguments = listOf(navArgument("username") { type = NavType.StringType })
            ) { entry ->
                val username = entry.arguments?.getString("username") ?: return@composable
                ThreadScreen(username = username, onBack = { navController.popBackStack() })
            }
            composable("builder") {
                BuilderScreen(onPosted = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                })
            }
            composable(
                route = "profile/{username}",
                arguments = listOf(navArgument("username") { type = NavType.StringType })
            ) { entry ->
                val username = entry.arguments?.getString("username") ?: LocusRepository.currentUsername
                ProfileScreen(
                    username = username,
                    onBack = if (username != LocusRepository.currentUsername) {
                        { navController.popBackStack() }
                    } else null,
                    onOpenThread = { navController.navigate("thread/$username") }
                )
            }
        }
    }
}
