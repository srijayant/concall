package com.aiconference.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
            launchNativeApp = { service -> FreeformLauncher.launchNativeApp(this, service) },
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

    private fun refreshPermissionState() {
        hasCameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        hasMicPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }
}
