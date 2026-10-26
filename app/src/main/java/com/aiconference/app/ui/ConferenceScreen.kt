package com.aiconference.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aiconference.app.tiles.AiService
import com.aiconference.app.tiles.Tile
import com.aiconference.app.tiles.TileKind
import com.aiconference.app.tiles.TileManager

/** Callbacks the screen needs from the hosting activity. */
class ConferenceActions(
    val popOutTile: (Tile) -> Unit,
    val bringBackTile: (Tile) -> Unit,
    val launchNativeApp: (AiService) -> Boolean,
    val isNativeAppInstalled: (AiService) -> Boolean,
    val toggleSpeaker: () -> Boolean,
    val requestPermissions: () -> Unit,
)

@Composable
fun ConferenceScreen(
    manager: TileManager,
    actions: ConferenceActions,
    hasCameraPermission: Boolean,
    speakerOn: Boolean,
) {
    var showAddSheet by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF101014))) {
        HeaderBar(manager)
        TileGrid(
            manager = manager,
            actions = actions,
            hasCameraPermission = hasCameraPermission,
            modifier = Modifier.weight(1f),
        )
        ControlBar(
            manager = manager,
            speakerOn = speakerOn,
            onToggleSpeaker = actions.toggleSpeaker,
            onAdd = { showAddSheet = true },
        )
    }

    if (showAddSheet) {
        AddParticipantSheet(
            manager = manager,
            actions = actions,
            onDismiss = { showAddSheet = false },
        )
    }
}

@Composable
private fun HeaderBar(manager: TileManager) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "AI Conference",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        val talking = manager.activeMicTileId?.let { manager.tileById(it)?.label }
        Text(
            text = when {
                !manager.micOpen -> "Mic muted"
                talking != null -> "Talking to $talking"
                else -> "Tap a tile's voice icon to talk"
            },
            color = Color(0xFF9A9AA5),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun TileGrid(
    manager: TileManager,
    actions: ConferenceActions,
    hasCameraPermission: Boolean,
    modifier: Modifier = Modifier,
) {
    val tiles = manager.tiles.toList()
    val columns = if (tiles.size <= 2) 1 else 2
    val rows = tiles.chunked(columns)

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        rows.forEach { rowTiles ->
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rowTiles.forEach { tile ->
                    TileCard(
                        tile = tile,
                        manager = manager,
                        actions = actions,
                        hasCameraPermission = hasCameraPermission,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                }
                // Keep cells square-ish when a row is not full.
                repeat(columns - rowTiles.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TileCard(
    tile: Tile,
    manager: TileManager,
    actions: ConferenceActions,
    hasCameraPermission: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = tile.service?.accent ?: Color(0xFF34C77B)
    val isLiveMic = manager.micOpen && manager.activeMicTileId == tile.id

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1A1A20))
            .border(
                width = if (isLiveMic) 2.dp else 1.dp,
                color = if (isLiveMic) accent else Color(0xFF2A2A32),
                shape = RoundedCornerShape(14.dp),
            ),
    ) {
        when {
            tile.poppedOut -> PoppedOutPlaceholder(tile) { actions.bringBackTile(tile) }
            tile.kind == TileKind.WEB -> WebTile(tile, manager)
            else -> CameraTile(
                tile = tile,
                hasCameraPermission = hasCameraPermission,
                onRequestPermission = actions.requestPermissions,
            )
        }

        // Name badge.
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xB3101014))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(6.dp))
            Text(tile.label, color = Color.White, style = MaterialTheme.typography.labelSmall)
            if (isLiveMic) {
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Filled.Mic, "live mic", tint = accent, modifier = Modifier.size(12.dp))
            }
        }

        // Per-tile controls.
        if (!tile.poppedOut) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xB3101014)),
            ) {
                if (tile.kind == TileKind.WEB) {
                    TileAction(
                        icon = Icons.Filled.RecordVoiceOver,
                        description = "talk to ${tile.label}",
                        tint = if (manager.activeMicTileId == tile.id) accent else Color.White,
                    ) {
                        manager.setActiveMicTile(
                            if (manager.activeMicTileId == tile.id) null else tile.id
                        )
                    }
                    TileAction(
                        icon = if (tile.outputMuted) Icons.AutoMirrored.Filled.VolumeOff
                        else Icons.AutoMirrored.Filled.VolumeUp,
                        description = "mute ${tile.label}",
                        tint = if (tile.outputMuted) Color(0xFFE05B5B) else Color.White,
                    ) { manager.setOutputMuted(tile, !tile.outputMuted) }
                    TileAction(Icons.AutoMirrored.Filled.OpenInNew, "pop out") {
                        actions.popOutTile(tile)
                    }
                    TileAction(Icons.Filled.Refresh, "reload") { manager.reload(tile) }
                } else {
                    TileAction(Icons.Filled.Cameraswitch, "flip camera") {
                        tile.frontCamera = !tile.frontCamera
                    }
                }
                TileAction(Icons.Filled.Close, "remove tile") { manager.removeTile(tile) }
            }
        }
    }
}

@Composable
private fun RowScope.TileAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun PoppedOutPlaceholder(tile: Tile, onBringBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().clickable(onClick = onBringBack).padding(12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            null,
            tint = Color(0xFF9A9AA5),
            modifier = Modifier.size(28.dp),
        )
        Text(
            "${tile.label} is in a floating window",
            color = Color(0xFF9A9AA5),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "Tap to bring back",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun ControlBar(
    manager: TileManager,
    speakerOn: Boolean,
    onToggleSpeaker: () -> Boolean,
    onAdd: () -> Unit,
) {
    var speaker by remember { mutableStateOf(speakerOn) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(
            onClick = { manager.setUserMicOpen(!manager.micOpen) },
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (manager.micOpen) Color(0xFF2A2A32) else Color(0xFFE05B5B),
            ),
            modifier = Modifier.size(52.dp),
        ) {
            Icon(
                if (manager.micOpen) Icons.Filled.Mic else Icons.Filled.MicOff,
                "toggle microphone",
                tint = Color.White,
            )
        }
        FilledTonalIconButton(
            onClick = { speaker = onToggleSpeaker() },
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = Color(0xFF2A2A32),
            ),
            modifier = Modifier.size(52.dp),
        ) {
            Icon(
                if (speaker) Icons.AutoMirrored.Filled.VolumeUp else Icons.Filled.PhoneInTalk,
                "toggle speaker",
                tint = Color.White,
            )
        }
        FilledTonalIconButton(
            onClick = onAdd,
            enabled = manager.canAddTile(),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = Color(0xFF2A2A32),
            ),
            modifier = Modifier.size(52.dp),
        ) {
            Icon(Icons.Filled.Add, "add participant", tint = Color.White)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddParticipantSheet(
    manager: TileManager,
    actions: ConferenceActions,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF1A1A20)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(
                "Add participant (${manager.tiles.size}/${TileManager.MAX_TILES})",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            AiService.entries.forEach { service ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(service.accent))
                    Spacer(Modifier.width(10.dp))
                    Text(service.label, color = Color.White, modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = { manager.addWebTile(service); onDismiss() },
                        enabled = manager.canAddTile(),
                    ) { Text("Add tile") }
                    if (actions.isNativeAppInstalled(service)) {
                        TextButton(onClick = { actions.launchNativeApp(service); onDismiss() }) {
                            Text("Native window")
                        }
                    }
                }
            }
            if (!manager.hasCameraTile()) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Videocam, null, tint = Color(0xFF34C77B), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Your camera", color = Color.White, modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = { manager.addCameraTile(); onDismiss() },
                        enabled = manager.canAddTile(),
                    ) { Text("Add tile") }
                }
            }
        }
    }
}
