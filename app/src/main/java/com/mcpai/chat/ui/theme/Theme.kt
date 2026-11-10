package com.mcpai.chat.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext

private val LightScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = BrandAccent,
    onSecondary = androidx.compose.ui.graphics.Color.Black,
    tertiary = BrandAccentDark,
    onTertiary = androidx.compose.ui.graphics.Color.White,
)

private val DarkScheme = darkColorScheme(
    // On dark surfaces the mint accent reads better than the dark navy.
    primary = BrandPrimaryDarkMode,
    onPrimary = androidx.compose.ui.graphics.Color(0xFF00393E),
    secondary = BrandAccentDark,
    onSecondary = androidx.compose.ui.graphics.Color.Black,
    tertiary = BrandAccent,
    onTertiary = androidx.compose.ui.graphics.Color.Black,
)

/**
 * App theme. Material You (dynamic color) drives surfaces, primary and on-*
 * roles on API 31+; the brand palette remains the static fallback below it.
 * App-specific semantics (bubbles, composer, tool accents) always come from
 * [ChatColors] via [LocalChatColors] — dynamic color never touches them.
 */
@Composable
fun McpAiChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkScheme
        else -> LightScheme
    }

    CompositionLocalProvider(
        LocalChatColors provides if (darkTheme) DarkChatColors else LightChatColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = McpAiChatTypography,
            content = content,
        )
    }
}

/** Shortcut to the current [ChatColors]. */
val chatColors: ChatColors
    @Composable
    @ReadOnlyComposable
    get() = LocalChatColors.current
