package com.alekseevich47.callmemo.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.alekseevich47.callmemo.MainActivity
import com.alekseevich47.callmemo.R
import com.alekseevich47.callmemo.util.TimeFormat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : Service() {

    @Inject
    lateinit var controller: CallPlaybackController

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> controller.toggle()
            ACTION_STOP, ACTION_DISMISS -> {
                controller.hide()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_OPEN_DETAIL -> {
                val callId = controller.state.value.callId
                val open = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    action = MainActivity.ACTION_OPEN_CALL_DETAIL
                    if (callId != null) putExtra(MainActivity.EXTRA_CALL_ID, callId)
                }
                startActivity(open)
            }
        }
        val state = controller.state.value
        if (!state.visible) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification(state))
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep playing in notification when app is backgrounded.
        super.onTaskRemoved(rootIntent)
    }

    private fun buildNotification(state: PlaybackUiState): Notification {
        val title = state.contactName.ifBlank { state.number }
        val duration = state.durationMs.coerceAtLeast(1L)
        val progress = state.positionMs.coerceIn(0L, duration).toInt()
        val max = duration.toInt().coerceAtLeast(1)

        val content = RemoteViews(packageName, R.layout.notification_playback)
        content.setTextViewText(R.id.notif_name, title)
        content.setTextViewText(R.id.notif_number, state.number)
        content.setTextViewText(R.id.notif_position, TimeFormat.duration(state.positionMs / 1000))
        content.setTextViewText(R.id.notif_duration, TimeFormat.duration(state.durationMs / 1000))
        content.setProgressBar(R.id.notif_progress, max, progress, false)
        content.setImageViewResource(
            R.id.notif_play,
            if (state.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
        )
        content.setOnClickPendingIntent(R.id.notif_play, actionPending(ACTION_TOGGLE, 1))
        content.setOnClickPendingIntent(R.id.notif_ai, actionPending(ACTION_OPEN_DETAIL, 2))

        val delete = actionPending(ACTION_DISMISS, 3)
        val contentIntent = PendingIntent.getActivity(
            this,
            4,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                action = MainActivity.ACTION_OPEN_CALL_DETAIL
                state.callId?.let { putExtra(MainActivity.EXTRA_CALL_ID, it) }
            },
            pendingFlags(),
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(state.number)
            .setCustomContentView(content)
            .setCustomBigContentView(content)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(contentIntent)
            .setDeleteIntent(delete)
            .setOnlyAlertOnce(true)
            .setOngoing(state.playing)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun actionPending(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, PlaybackService::class.java).setAction(action)
        return PendingIntent.getService(this, requestCode, intent, pendingFlags())
    }

    private fun pendingFlags(): Int {
        return PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Воспроизведение записи",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Управление прослушиванием звонка"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "callmemo_playback"
        const val NOTIFICATION_ID = 42
        const val ACTION_SYNC = "com.alekseevich47.callmemo.playback.SYNC"
        const val ACTION_TOGGLE = "com.alekseevich47.callmemo.playback.TOGGLE"
        const val ACTION_STOP = "com.alekseevich47.callmemo.playback.STOP"
        const val ACTION_DISMISS = "com.alekseevich47.callmemo.playback.DISMISS"
        const val ACTION_OPEN_DETAIL = "com.alekseevich47.callmemo.playback.OPEN_DETAIL"
    }
}
