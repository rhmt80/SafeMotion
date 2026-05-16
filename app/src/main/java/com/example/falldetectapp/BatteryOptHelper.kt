package com.example.falldetectapp

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Many Android OEMs (Samsung, Xiaomi, Realme, OPPO) aggressively kill foreground
 * services even when they're tagged "health". Without battery-optimization
 * exemption, monitoring stops after a few hours and the user never knows.
 *
 * Show the dialog once. The user can dismiss it and never see it again.
 */
object BatteryOptHelper {
    private const val PREF_NAME = "user_prefs"
    private const val PREF_KEY = "battery_opt_prompted"

    fun isExempt(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun shouldPrompt(context: Context): Boolean {
        if (isExempt(context)) return false
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return !prefs.getBoolean(PREF_KEY, false)
    }

    fun markPrompted(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(PREF_KEY, true).apply()
    }

    @SuppressLint("BatteryLife")
    fun showPrompt(context: Context) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.battery_opt_title)
            .setMessage(R.string.battery_opt_body)
            .setCancelable(false)
            .setPositiveButton(R.string.battery_opt_open) { _, _ ->
                markPrompted(context)
                // ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS shows the system
                // confirmation dialog. SafeMotion is a health app, this is allowed.
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:${context.packageName}"))
                runCatching { context.startActivity(intent) }
                    .onFailure {
                        // Fallback: open the general battery-opt settings list.
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                    }
            }
            .setNegativeButton(R.string.battery_opt_skip) { _, _ ->
                markPrompted(context)
            }
            .show()
    }
}
