package com.aiconference.app.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/**
 * Device-level audio routing. Tile-level mute/unmute is JS-driven (see
 * TileWebViewFactory); this class only decides where mixed output goes.
 */
class AudioController(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    var speakerOn: Boolean = true
        private set

    fun applyDefaultRouting() {
        setSpeaker(true)
    }

    fun toggleSpeaker(): Boolean {
        setSpeaker(!speakerOn)
        return speakerOn
    }

    fun setSpeaker(on: Boolean) {
        speakerOn = on
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (on) {
                audioManager.availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    ?.let { audioManager.setCommunicationDevice(it) }
            } else {
                audioManager.clearCommunicationDevice()
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = on
        }
    }

    fun release() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
        }
    }
}
