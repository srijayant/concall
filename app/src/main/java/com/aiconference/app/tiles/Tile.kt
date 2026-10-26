package com.aiconference.app.tiles

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class TileKind { WEB, CAMERA }

/**
 * One participant in the conference grid. Web tiles own a WebView held in
 * [TileManager]'s pool so the session survives recomposition, reordering,
 * and moving between the grid and the pop-out window.
 */
class Tile(
    val id: String,
    val kind: TileKind,
    val service: AiService?,
) {
    /** Tile's media elements are muted via injected JS. */
    var outputMuted by mutableStateOf(false)

    /** Tile currently lives in a separate pop-out window, grid shows a chip. */
    var poppedOut by mutableStateOf(false)

    /** Camera tile only: which lens the self-view uses. */
    var frontCamera by mutableStateOf(true)

    val label: String get() = service?.label ?: "You"
}
