package com.example.falldetectapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Restarts the foreground monitoring service after the device reboots, but only
 * if the user previously had monitoring on. We piggy-back on a SharedPreferences
 * flag the main UI sets when the user taps Start / Stop.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val wasMonitoring = prefs.getBoolean("monitoring_enabled", false)
        if (!wasMonitoring) return

        val adaptive = prefs.getBoolean("adaptive_mode", false)
        val serviceIntent = Intent(context, ForegroundSensorService::class.java).apply {
            this.action = ForegroundSensorService.ACTION_START
            putExtra(ForegroundSensorService.EXTRA_ADAPTIVE_MODE, adaptive)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
