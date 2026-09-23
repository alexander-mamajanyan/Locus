package com.locus.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Swap these for bundled fonts (res/font + FontFamily(Font(...))) once you
// want the exact Fraunces / IBM Plex Sans / IBM Plex Mono look from the
// web prototype. System fallbacks keep this base app buildable with zero
// extra font assets.
val DisplayFont = FontFamily.Serif
val BodyFont = FontFamily.SansSerif
val MathFont = FontFamily.Monospace

val LocusTypography = Typography(
    headlineSmall = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = MathFont, fontWeight = FontWeight.Normal, fontSize = 12.sp)
)
