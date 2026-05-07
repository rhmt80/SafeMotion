package com.example.falldetectapp

/**
 * Gates the switch between LOW and HIGH sampling rates in adaptive mode.
 *
 * Thresholds deliberately sit above ordinary daily motion so that walking,
 * picking up the phone, or putting it down does not constantly kick the
 * service into HIGH rate (and does not constantly reset the calm timer).
 */
class WakeDetector {

    private val movementThreshold = 18.0f
    private val calmThreshold = 12.0f

    fun isMovementDetected(magnitude: Float): Boolean {
        return magnitude > movementThreshold
    }

    fun isCalm(magnitude: Float): Boolean {
        return magnitude < calmThreshold
    }
}
