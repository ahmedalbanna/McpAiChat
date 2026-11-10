package com.mcpai.chat.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ---- Brand palette (static fallback below API 31) -------------------------

val BrandPrimary = Color(0xFF23313D)
val BrandPrimaryDarkMode = Color(0xFF8FD3CB)
val BrandAccent = Color(0xFF7BFFF0)
val BrandAccentDark = Color(0xFF4DB6AC)

// ---- App-specific semantic colors -----------------------------------------

/**
 * Colors no Material scheme can express: bubble fills, composer controls and
 * tool/status accents. Dynamic color (API 31+) never touches these, so the
 * chat keeps its own identity regardless of the wallpaper.
 */
@Immutable
data class ChatColors(
    val bubbleUser: Color,
    val bubbleUserText: Color,
    val bubbleAi: Color,
    val bubbleAiText: Color,
    val bubbleAiStroke: Color,
    val codeBlockBg: Color,
    val reasoningBg: Color,
    val divider: Color,
    val composerIconBg: Color,
    val composerIconFg: Color,
    val sendBackground: Color,
    val sendForeground: Color,
    val sendDisabledBackground: Color,
    val sendDisabledForeground: Color,
    val voiceBackground: Color,
    val voiceOn: Color,
    val voiceContainer: Color,
    val voiceForeground: Color,
    val toolAccent: Color,
    val toolOk: Color,
    val statusIdle: Color,
)

val LightChatColors = ChatColors(
    bubbleUser = Color(0xFF23313D),
    bubbleUserText = Color(0xFFFFFFFF),
    bubbleAi = Color(0xFFD5F5F1),
    bubbleAiText = Color(0xFF1B1B1B),
    bubbleAiStroke = Color(0x14000000),
    codeBlockBg = Color(0x11000000),
    reasoningBg = Color(0x1A000000),
    divider = Color(0x1A000000),
    composerIconBg = Color(0xFFEFEEEA),
    composerIconFg = Color(0xFF5B5B58),
    sendBackground = Color(0xFF202123),
    sendForeground = Color(0xFFFFFFFF),
    sendDisabledBackground = Color(0xFFE8E8E6),
    sendDisabledForeground = Color(0xFFA9A9A5),
    voiceBackground = Color(0xFFD64545),
    voiceOn = Color(0xFFFFFFFF),
    voiceContainer = Color(0xFFFFE7E7),
    voiceForeground = Color(0xFFD64545),
    toolAccent = Color(0xFFA3404B),
    toolOk = Color(0xFF018786),
    statusIdle = Color(0xFF8A8A84),
)

val DarkChatColors = ChatColors(
    bubbleUser = Color(0xFF2F3A44),
    bubbleUserText = Color(0xFFECECEC),
    bubbleAi = Color(0xFF232323),
    bubbleAiText = Color(0xFFECECEC),
    bubbleAiStroke = Color(0x1FFFFFFF),
    codeBlockBg = Color(0x1FFFFFFF),
    reasoningBg = Color(0x1FFFFFFF),
    divider = Color(0x1FFFFFFF),
    composerIconBg = Color(0xFF2F2F2F),
    composerIconFg = Color(0xFFB4B4B4),
    sendBackground = Color(0xFFECECF1),
    sendForeground = Color(0xFF202123),
    sendDisabledBackground = Color(0xFF2F2F2F),
    sendDisabledForeground = Color(0xFF626262),
    voiceBackground = Color(0xFFD64545),
    voiceOn = Color(0xFFFFFFFF),
    voiceContainer = Color(0xFF402626),
    voiceForeground = Color(0xFFFF7B7B),
    toolAccent = Color(0xFFFF7B7B),
    toolOk = Color(0xFF4DB6AC),
    statusIdle = Color(0xFF777771),
)

val LocalChatColors = staticCompositionLocalOf { LightChatColors }
