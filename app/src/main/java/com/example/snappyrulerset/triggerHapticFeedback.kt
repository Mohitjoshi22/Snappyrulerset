// Fixed haptic feedback function
package com.example.snappyrulerset
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresPermission
import android.Manifest

@RequiresPermission(Manifest.permission.VIBRATE)
fun triggerHapticFeedback(context: Context) {
    try {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        val vibrator = vibratorManager?.defaultVibrator
            ?: context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(50)
            }
        }
    } catch (e: Exception) {
        // Haptic feedback failed, continue silently
    }
}