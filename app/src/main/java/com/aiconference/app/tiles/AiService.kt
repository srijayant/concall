package com.aiconference.app.tiles

import androidx.compose.ui.graphics.Color

/**
 * The AI participants. No API keys are used anywhere: each service is loaded
 * as its web app inside a WebView, or (optionally) its native Android app is
 * launched in a separate flexible/freeform window.
 */
enum class AiService(
    val label: String,
    val url: String,
    val accent: Color,
    /** Known native app package names, in order of preference. */
    val nativePackages: List<String>,
) {
    CHATGPT(
        label = "ChatGPT",
        url = "https://chatgpt.com/",
        accent = Color(0xFF74AA9C),
        nativePackages = listOf("com.openai.chatgpt"),
    ),
    GEMINI(
        label = "Gemini",
        url = "https://gemini.google.com/app",
        accent = Color(0xFF4F8EF7),
        nativePackages = listOf("com.google.android.apps.bard"),
    ),
    CLAUDE(
        label = "Claude",
        url = "https://claude.ai/new",
        accent = Color(0xFFD97757),
        nativePackages = listOf("com.anthropic.claude"),
    ),
    GROK(
        label = "Grok",
        url = "https://grok.com/",
        accent = Color(0xFFBBBBBB),
        nativePackages = listOf("ai.x.grok"),
    ),
}
