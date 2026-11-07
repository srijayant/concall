package com.aiconference.app.web

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Opens a service in Chrome Custom Tabs, which share the phone's real Chrome
 * sign-in. Gemini has no email login, and Google blocks its sign-in page
 * inside a tile, so Chrome is the path that is already signed in.
 */
object ChromeSignIn {

    fun open(context: Context, url: String) {
        val tabs = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .build()
        val uri = Uri.parse(url)
        runCatching { tabs.launchUrl(context, uri) }.onFailure {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }
}
