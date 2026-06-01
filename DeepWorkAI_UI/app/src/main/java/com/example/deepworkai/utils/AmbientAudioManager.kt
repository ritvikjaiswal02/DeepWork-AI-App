package com.example.deepworkai.utils

import android.content.Context
import android.media.MediaPlayer

class AmbientAudioManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    var isPlaying = false
        private set
    var currentSoundResId: Int? = null
        private set
    var volume = 0.5f // 0.0 to 1.0
        set(value) {
            field = value
            mediaPlayer?.setVolume(value, value)
        }

    /**
     * Callback fired when the currently playing track finishes.
     * If set, the player will NOT loop the same track; the caller is expected to advance
     * to the next track (e.g. via playSound() on the next resId).
     */
    var onTrackComplete: (() -> Unit)? = null

    fun playSound(soundResId: Int) {
        // Always replace, even if same resId — allows "restart current track" behaviour
        mediaPlayer?.release()

        val player = MediaPlayer.create(context, soundResId) ?: return // null = resource failed
        // If a completion callback is set, do not loop — let onCompletion advance us.
        player.isLooping = onTrackComplete == null
        player.setVolume(volume, volume)
        if (onTrackComplete != null) {
            player.setOnCompletionListener {
                isPlaying = false
                onTrackComplete?.invoke()
            }
        }
        player.start()

        mediaPlayer = player
        currentSoundResId = soundResId
        isPlaying = true
    }

    fun pause() {
        if (isPlaying) {
            mediaPlayer?.pause()
            isPlaying = false
        }
    }

    fun resume() {
        if (!isPlaying && currentSoundResId != null) {
            mediaPlayer?.start()
            isPlaying = true
        }
    }

    fun stopAndRelease() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
        currentSoundResId = null
    }
}
