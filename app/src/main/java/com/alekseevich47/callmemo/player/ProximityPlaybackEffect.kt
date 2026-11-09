package com.alekseevich47.callmemo.player

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * While [active], holding the phone to the ear routes playback to the earpiece and
 * engages a proximity screen-off wake lock (like a phone call).
 *
 * Safety notes (OEM crash sources avoided):
 * - Sensor callbacks always marshalled onto the main looper.
 * - No manual [android.view.WindowManager.LayoutParams.screenBrightness] changes —
 *   combining them with [PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK] crashes on
 *   several Transsion/Tecno builds.
 * - Wake-lock acquire/release wrapped in runCatching.
 */
@Composable
fun ProximityPlaybackEffect(
    active: Boolean,
    onNearChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    DisposableEffect(active) {
        if (!active) {
            onNearChanged(false)
            return@DisposableEffect onDispose { }
        }

        val appContext = context.applicationContext
        val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val proximity = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (proximity == null) {
            onNearChanged(false)
            return@DisposableEffect onDispose { }
        }

        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wakeLock = runCatching {
            powerManager.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "callmemo:earpiece",
            ).apply { setReferenceCounted(false) }
        }.getOrNull()

        var near = false
        val mainHandler = Handler(Looper.getMainLooper())

        fun releaseLock() {
            val lock = wakeLock ?: return
            runCatching {
                if (lock.isHeld) lock.release()
            }
        }

        fun acquireLock() {
            val lock = wakeLock ?: return
            runCatching {
                if (!lock.isHeld) {
                    // Cap hold time so a stuck near-state cannot drain the battery.
                    lock.acquire(10 * 60 * 1000L)
                }
            }
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val values = event?.values ?: return
                if (values.isEmpty()) return
                val max = proximity.maximumRange.coerceAtLeast(0.1f)
                // Binary proximity sensors report maxRange when far; near when below threshold.
                val threshold = max.coerceAtMost(5f)
                val isNear = values[0] < threshold
                if (isNear == near) return
                near = isNear

                // Ensure UI / AudioManager work stays on main (some OEMs deliver off-main).
                mainHandler.post {
                    onNearChanged(isNear)
                    if (isNear) acquireLock() else releaseLock()
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        val registered = runCatching {
            sensorManager.registerListener(
                listener,
                proximity,
                SensorManager.SENSOR_DELAY_NORMAL,
                mainHandler,
            )
        }.getOrDefault(false)

        if (!registered) {
            onNearChanged(false)
            return@DisposableEffect onDispose { }
        }

        onDispose {
            runCatching { sensorManager.unregisterListener(listener) }
            mainHandler.removeCallbacksAndMessages(null)
            releaseLock()
            onNearChanged(false)
        }
    }
}
