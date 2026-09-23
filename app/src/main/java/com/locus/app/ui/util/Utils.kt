package com.locus.app.ui.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

private val AvatarPalette = listOf(
    Color(0xFFB8863B), Color(0xFF2F6B4F), Color(0xFF4A5F8C),
    Color(0xFFA6423A), Color(0xFF6B5B95), Color(0xFF3E7C7A)
)

/** Deterministic avatar color from a username, so the same person always looks the same. */
fun colorForUsername(username: String): Color {
    val hash = username.fold(0) { acc, c -> acc * 31 + c.code }
    return AvatarPalette[Math.floorMod(hash, AvatarPalette.size)]
}

fun Int.toShortString(): String =
    if (this >= 1000) "%.1fk".format(this / 1000f).replace(".0k", "k") else this.toString()

fun buildAnnotatedCaption(username: String, caption: String): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("@$username ") }
    append(caption)
}

private val superscriptDigits = mapOf(
    '0' to '\u2070', '1' to '\u00B9', '2' to '\u00B2', '3' to '\u00B3', '4' to '\u2074',
    '5' to '\u2075', '6' to '\u2076', '7' to '\u2077', '8' to '\u2078', '9' to '\u2079'
)

/**
 * Lightweight "live math" text transform for chat: turns `x^2` into `x²`,
 * `pi`/`theta` into symbols, `sqrt(x)` into `√(x)`, and so on. It's a
 * stand-in for real LaTeX rendering, good enough for a base app.
 */
fun mathify(input: String): String {
    var text = Regex("\\^(\\d+)").replace(input) { match ->
        match.groupValues[1].map { superscriptDigits[it] ?: it }.joinToString("")
    }
    text = text
        .replace(Regex("\\bpi\\b", RegexOption.IGNORE_CASE), "\u03C0")
        .replace(Regex("\\btheta\\b", RegexOption.IGNORE_CASE), "\u03B8")
        .replace(Regex("\\balpha\\b", RegexOption.IGNORE_CASE), "\u03B1")
        .replace(Regex("sqrt\\(([^)]+)\\)"), "\u221A($1)")
        .replace("<=", "\u2264")
        .replace(">=", "\u2265")
        .replace("*", "\u00B7")
    return text
}
