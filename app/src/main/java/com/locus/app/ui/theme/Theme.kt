package com.locus.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = InkPrimary,
    onPrimary = PaperBg,
    secondary = AccentGold,
    tertiary = AccentGreen,
    background = PaperBg,
    surface = PaperSurface,
    surfaceVariant = PaperSurfaceAlt,
    onBackground = InkPrimary,
    onSurface = InkPrimary,
    outline = BorderLight,
    error = AccentRed
)

private val DarkColors = darkColorScheme(
    primary = ChalkInk,
    onPrimary = ChalkBg,
    secondary = ChalkGold,
    tertiary = ChalkGreen,
    background = ChalkBg,
    surface = ChalkSurface,
    surfaceVariant = ChalkSurfaceAlt,
    onBackground = ChalkInk,
    onSurface = ChalkInk,
    outline = ChalkBorder,
    error = ChalkRed
)

/**
 * "Notebook" in light mode, "chalkboard" in dark mode by default — the
 * same duality used in the web prototype.
 *
 * On Android 12+ (API 31+), [dynamicColor] (on by default) pulls the
 * palette straight from the phone's wallpaper/system theme instead
 * (Material You), the same way the phone's own apps do. It falls back to
 * the notebook/chalkboard palette on older Android versions, or if you
 * pass `dynamicColor = false` to keep the custom brand look everywhere.
 */
@Composable
fun LocusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = LocusTypography,
        content = content
    )
}
