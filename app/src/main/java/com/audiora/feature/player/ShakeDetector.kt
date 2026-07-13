package com.audiora.feature.player

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume

/**
 * Listens for a shake gesture using the accelerometer.
 *
 * Matches Voice's ShakeDetectorImpl pattern.
 * Returns true if a shake is detected, false if cancelled or unavailable.
 */
class ShakeDetector(private val context: Context) {

    /**
     * Suspends until a shake gesture is detected (2 significant movements within 1 second).
     */
    suspend fun detect(): Boolean = suspendCancellableCoroutine { cont ->
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        if (sensorManager == null) {
            cont.resume(false)
            return@suspendCancellableCoroutine
        }
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer == null) {
            cont.resume(false)
            return@suspendCancellableCoroutine
        }

        var lastShakeTime = 0L
        var lastX = 0f
        var lastY = 0f
        var lastZ = 0f
        var shakeCount = 0
        var hasValues = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val now = System.currentTimeMillis()
                if (!hasValues) {
                    lastX = event.values[0]
                    lastY = event.values[1]
                    lastZ = event.values[2]
                    hasValues = true
                    return
                }
                val deltaX = kotlin.math.abs(event.values[0] - lastX)
                val deltaY = kotlin.math.abs(event.values[1] - lastY)
                val deltaZ = kotlin.math.abs(event.values[2] - lastZ)
                lastX = event.values[0]
                lastY = event.values[1]
                lastZ = event.values[2]

                // Significant movement threshold (Voice uses 12)
                if (deltaX > 12f || deltaY > 12f || deltaZ > 12f) {
                    if (now - lastShakeTime > 200L) {
                        shakeCount++
                        lastShakeTime = now
                        if (shakeCount >= 2 && !cont.isCompleted) {
                            Timber.d("ShakeDetector: shake detected!")
                            cont.resume(true)
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(
            listener,
            accelerometer,
            SensorManager.SENSOR_DELAY_GAME
        )

        cont.invokeOnCancellation {
            sensorManager.unregisterListener(listener)
        }
    }
}
