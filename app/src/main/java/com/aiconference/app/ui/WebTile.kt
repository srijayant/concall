package com.aiconference.app.ui

import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.aiconference.app.tiles.Tile
import com.aiconference.app.tiles.TileManager
import com.aiconference.app.web.WebViewHost

/**
 * Hosts a pooled WebView inside the grid. The WebView is reparented in and
 * out (rather than recreated) so the AI session survives tile reordering,
 * grid resizes, and round-trips through the pop-out window.
 */
@Composable
fun WebTile(tile: Tile, manager: TileManager, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val container = remember(tile.id) { FrameLayout(context) }

    AndroidView(
        factory = { container },
        modifier = modifier.fillMaxSize(),
        update = {
            val webView = manager.webViewFor(tile)
            if (webView.parent !== container) {
                WebViewHost.attach(webView, container, context)
            }
        },
    )

    DisposableEffect(tile.id) {
        onDispose {
            manager.peekWebView(tile)?.let { WebViewHost.detach(it, container, appContext) }
        }
    }
}
