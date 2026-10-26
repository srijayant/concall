package com.aiconference.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.app.AlertDialog
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.aiconference.app.ConferenceApp
import com.aiconference.app.audio.AudioController
import com.aiconference.app.popout.FreeformLauncher
import com.aiconference.app.popout.PopOutActivity
import com.aiconference.app.service.CallGridService
import com.aiconference.app.tiles.AiService

class MainActivity : ComponentActivity() {

    private lateinit var audio: AudioController

    private var hasCameraPermission by mutableStateOf(false)
    private var hasMicPermission by mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshPermissionState()
            CallGridService.start(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = ConferenceApp.tileManager(this)
        audio = AudioController(this)
        audio.applyDefaultRouting()
        refreshPermissionState()
        requestPermissions()

        val actions = ConferenceActions(
            popOutTile = { tile -> FreeformLauncher.popOutTile(this, tile) },
            bringBackTile = { tile -> PopOutActivity.closeFor(tile.id) },
            launchNativeApp = { service -> promptAndLaunchNative(service) },
            isNativeAppInstalled = { service -> FreeformLauncher.isNativeAppInstalled(this, service) },
            toggleSpeaker = { audio.toggleSpeaker() },
            requestPermissions = { requestPermissions() },
        )

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                ConferenceScreen(
                    manager = manager,
                    actions = actions,
                    hasCameraPermission = hasCameraPermission,
                    speakerOn = audio.speakerOn,
                )
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            audio.release()
            CallGridService.stop(this)
        }
        super.onDestroy()
    }

    private fun requestPermissions() {
        val wanted = buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (wanted.isEmpty()) {
            CallGridService.start(this)
        } else {
            permissionLauncher.launch(wanted.toTypedArray())
        }
    }

    private fun promptAndLaunchNative(service: AiService): Boolean {
        if (!FreeformLauncher.isNativeAppInstalled(this, service)) return false
        AlertDialog.Builder(this)
            .setTitle("Opens ${service.label} as a separate app")
            .setMessage(
                "Android cannot put another app inside a tile, so ${service.label} " +
                    "will open on its own. This phone usually opens it full screen.\n\n" +
                    "To make it a small floating window:\n" +
                    "1. Open Recents (the square / swipe-up-and-hold)\n" +
                    "2. Long-press ${service.label}\n" +
                    "3. Tap Floating window\n\n" +
                    "Then switch back here — the grid stays running.",
            )
            .setPositiveButton("Open ${service.label}") { _, _ ->
                FreeformLauncher.launchNativeApp(this, service)
                Toast.makeText(
                    this,
                    "If it’s full screen: Recents → long-press → Floating window",
                    Toast.LENGTH_LONG,
                ).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
        return true
    }

    private fun refreshPermissionState() {
        hasCameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        hasMicPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }
}
