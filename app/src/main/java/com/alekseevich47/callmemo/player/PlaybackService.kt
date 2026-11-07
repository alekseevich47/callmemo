package com.alekseevich47.callmemo.player

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Placeholder foreground media service for sticky player background playback.
 * Wired in later with MediaSession + ExoPlayer/Media3.
 */
class PlaybackService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
