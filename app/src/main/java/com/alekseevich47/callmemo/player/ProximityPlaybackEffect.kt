package com.alekseevich47.callmemo.player

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * While [active], holding the phone to the ear turns the screen off (proximity wake-lock)
 * and routes playback to the earpiece like a phone call.
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
        val activity = context as? Activity
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val proximity = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
            "callmemo:earpiece",
        )
        var near = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val value = event?.values?.firstOrNull() ?: return
                val max = proximity?.maximumRange ?: 5f
                val isNear = value < max.coerceAtMost(5f)
                if (isNear == near) return
                near = isNear
                onNearChanged(isNear)
                if (isNear) {
                    if (!wakeLock.isHeld) wakeLock.acquire(10 * 60 * 1000L)
                    activity?.window?.attributes = activity.window.attributes.apply {
                        screenBrightness = 0.01f
                    }
                } else {
                    if (wakeLock.isHeld) wakeLock.release()
                    activity?.window?.attributes = activity.window.attributes.apply {
                        screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (proximity != null) {
            sensorManager.registerListener(listener, proximity, SensorManager.SENSOR_DELAY_NORMAL)
        }
        onDispose {
            sensorManager.unregisterListener(listener)
            if (wakeLock.isHeld) wakeLock.release()
            onNearChanged(false)
            activity?.window?.attributes = activity.window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }
}
