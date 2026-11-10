package com.mcpai.chat.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Material 3 sizes with Arabic-aware line spacing. Arabic needs a taller
 * line box than Latin because of ascenders, descenders and tashkeel, so
 * body text keeps a 1.35–1.4 multiplier and labels 1.25.
 *
 * Line heights are spelled out in sp (not computed) so the Compose text
 * engine reports the same density as the legacy XML styles.
 */
val McpAiChatTypography = Typography(
    displaySmall = TextStyle(
        fontSize = 36.sp,
        lineHeight = 41.4f.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontSize = 22.sp,
        lineHeight = 27.5f.sp,
        letterSpacing = 0.sp,
        fontWeight = FontWeight.Bold,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 20.8f.sp,
        letterSpacing = 0.16f.sp,
        fontWeight = FontWeight.Bold,
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp,
        lineHeight = 18.2f.sp,
        letterSpacing = 0.14f.sp,
        fontWeight = FontWeight.Bold,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.4f.sp,
        letterSpacing = 0.16f.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 19.6f.sp,
        letterSpacing = 0.14f.sp,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.2f.sp,
        letterSpacing = 0.24f.sp,
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 17.5f.sp,
        letterSpacing = 0.28f.sp,
        fontWeight = FontWeight.Bold,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.36f.sp,
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 13.75f.sp,
        letterSpacing = 0.44f.sp,
    ),
)

/** Bubble body text — a touch denser than body copy, Arabic-safe. */
val BubbleTextStyle = TextStyle(
    fontSize = 15.sp,
    lineHeight = 20.7f.sp,
    letterSpacing = 0.sp,
)

/** Monospace code text used in markdown code blocks and tool schemas. */
val CodeTextStyle = TextStyle(
    fontSize = 12.sp,
    lineHeight = 16.8f.sp,
    fontFamily = FontFamily.Monospace,
)
