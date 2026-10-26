package com.aiconference.app.web

import android.content.Context
import android.content.MutableContextWrapper
import android.view.ViewGroup
import android.webkit.WebView

/**
 * Moves a pooled WebView between hosts (grid tile <-> pop-out window).
 * The WebView was created with a [MutableContextWrapper], so we can swap its
 * base context to the current host activity and back to the application
 * context when detached, avoiding activity leaks.
 */
object WebViewHost {

    fun attach(webView: WebView, container: ViewGroup, hostContext: Context) {
        (webView.context as? MutableContextWrapper)?.baseContext = hostContext
        (webView.parent as? ViewGroup)?.removeView(webView)
        container.addView(
            webView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        webView.onResume()
    }

    fun detach(webView: WebView, expectedParent: ViewGroup, appContext: Context) {
        if (webView.parent === expectedParent) {
            expectedParent.removeView(webView)
            (webView.context as? MutableContextWrapper)?.baseContext = appContext.applicationContext
        }
    }
}
