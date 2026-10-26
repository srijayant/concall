package com.aiconference.app.popout

import android.app.Activity
import android.os.Bundle
import android.widget.FrameLayout
import com.aiconference.app.ConferenceApp
import com.aiconference.app.web.WebViewHost

/**
 * Hosts a single tile's WebView in its own task so OxygenOS can show it as a
 * flexible/freeform window. The WebView instance is the SAME object as in the
 * grid (reparented), so the session, scroll position, and any ongoing voice
 * chat continue seamlessly; closing the window returns the tile to the grid.
 */
class PopOutActivity : Activity() {

    companion object {
        const val EXTRA_TILE_ID = "tile_id"

        private val instances = LinkedHashMap<String, PopOutActivity>()

        /** Stock OxygenOS renders one flexible window: enforce focus mode. */
        fun closeAllExcept(tileId: String?) {
            instances.values.toList().forEach { act ->
                if (act.tileId != tileId) act.finishAndRemoveTask()
            }
        }

        fun closeFor(tileId: String) {
            instances[tileId]?.finishAndRemoveTask()
        }
    }

    private var tileId: String? = null
    private lateinit var container: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tileId = intent.getStringExtra(EXTRA_TILE_ID)
        val manager = ConferenceApp.tileManager(this)
        val tile = tileId?.let { manager.tileById(it) }
        if (tile == null) {
            finishAndRemoveTask()
            return
        }
        title = tile.label
        instances[tile.id] = this
        tile.poppedOut = true

        container = FrameLayout(this)
        setContentView(container)
        WebViewHost.attach(manager.webViewFor(tile), container, this)
    }

    override fun onDestroy() {
        val id = tileId
        if (id != null && instances[id] === this) {
            instances.remove(id)
            val manager = ConferenceApp.tileManager(this)
            manager.tileById(id)?.let { tile ->
                manager.peekWebView(tile)?.let { WebViewHost.detach(it, container, applicationContext) }
                tile.poppedOut = false
            }
        }
        super.onDestroy()
    }
}
