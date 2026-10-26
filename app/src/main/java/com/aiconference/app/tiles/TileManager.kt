package com.aiconference.app.tiles

import android.content.Context
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aiconference.app.web.TileWebViewFactory
import java.util.UUID

/**
 * App-scoped state for the conference: the list of tiles, the WebView pool
 * (so sessions survive recomposition / pop-out moves), and mic arbitration.
 *
 * Mic policy: at most ONE web tile holds a live microphone at a time
 * ("talking to"). Everything plays audio out through the shared app session,
 * so all participants are heard; the single-mic rule prevents four voice
 * modes from capturing each other's speech through the speaker.
 */
class TileManager(private val appContext: Context) : TileWebViewFactory.MicArbiter {

    companion object {
        const val MAX_TILES = 6
    }

    val tiles = mutableStateListOf<Tile>()

    /** Tile currently allowed to capture mic audio, or null = nobody. */
    var activeMicTileId by mutableStateOf<String?>(null)
        private set

    /** Global user mute (push-to-talk off). Applies on top of the active tile. */
    var micOpen by mutableStateOf(true)
        private set

    private val webViews = HashMap<String, WebView>()

    init {
        // Default roster: the four AIs plus the user's camera tile.
        addCameraTile()
        AiService.entries.forEach { addWebTile(it) }
    }

    fun canAddTile(): Boolean = tiles.size < MAX_TILES

    fun hasCameraTile(): Boolean = tiles.any { it.kind == TileKind.CAMERA }

    fun addWebTile(service: AiService): Tile? {
        if (!canAddTile()) return null
        val tile = Tile(UUID.randomUUID().toString(), TileKind.WEB, service)
        tiles.add(tile)
        return tile
    }

    fun addCameraTile(): Tile? {
        if (!canAddTile() || hasCameraTile()) return null
        val tile = Tile(UUID.randomUUID().toString(), TileKind.CAMERA, null)
        tiles.add(tile)
        return tile
    }

    fun removeTile(tile: Tile) {
        tiles.remove(tile)
        if (activeMicTileId == tile.id) activeMicTileId = null
        webViews.remove(tile.id)?.let { wv ->
            (wv.parent as? android.view.ViewGroup)?.removeView(wv)
            wv.destroy()
        }
    }

    fun tileById(id: String): Tile? = tiles.firstOrNull { it.id == id }

    /** Get or lazily create the WebView backing a web tile. */
    fun webViewFor(tile: Tile): WebView =
        webViews.getOrPut(tile.id) { TileWebViewFactory.create(appContext, tile, this) }

    fun peekWebView(tile: Tile): WebView? = webViews[tile.id]

    fun reload(tile: Tile) {
        webViews[tile.id]?.reload()
    }

    /** Select which tile the user is talking to (null = mute everyone's mic). */
    fun setActiveMicTile(tileId: String?) {
        activeMicTileId = tileId
        applyMicState()
    }

    fun setUserMicOpen(open: Boolean) {
        micOpen = open
        applyMicState()
    }

    fun setOutputMuted(tile: Tile, muted: Boolean) {
        tile.outputMuted = muted
        webViews[tile.id]?.let { TileWebViewFactory.setOutputEnabled(it, !muted) }
    }

    private fun applyMicState() {
        tiles.filter { it.kind == TileKind.WEB }.forEach { tile ->
            val enabled = micOpen && tile.id == activeMicTileId
            webViews[tile.id]?.let { TileWebViewFactory.setMicEnabled(it, enabled) }
        }
    }

    // ---- MicArbiter ----

    override fun mayCaptureAudio(tileId: String): Boolean = tileId == activeMicTileId

    override fun onPageReady(tileId: String) {
        val tile = tileById(tileId) ?: return
        val wv = webViews[tileId] ?: return
        // Re-assert per-tile state after navigation resets the page's JS world.
        TileWebViewFactory.setMicEnabled(wv, micOpen && tileId == activeMicTileId)
        TileWebViewFactory.setOutputEnabled(wv, !tile.outputMuted)
    }
}
