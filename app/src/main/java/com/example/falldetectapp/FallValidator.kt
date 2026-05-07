package com.example.falldetectapp

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Physics-based validation gates that complement the ML model to eliminate
 * false positives. A real fall has a distinctive three-phase signature:
 *
 *   1. Free-fall: acceleration magnitude briefly dips toward 0 (weightless).
 *   2. Impact: a sharp spike well above 1g (typically >2.5g).
 *   3. Post-impact stillness: magnitude settles near gravity with low variance.
 *
 * Ordinary activities (walking, sitting, phone bumps) almost never satisfy all
 * three phases together, so requiring them as hard gates around the ML
 * inference dramatically reduces false alerts.
 */
class FallValidator {

    companion object {
        private const val GRAVITY = 9.81f

        // Free-fall: near-weightless dip. Real falls drop well below ~6 m/s^2.
        const val FREE_FALL_THRESHOLD = 6.0f

        // Impact: ~2.55g peak. Vigorous walking rarely exceeds ~15 m/s^2,
        // hard sitting ~20 m/s^2, so 25 cleanly separates falls.
        const val IMPACT_THRESHOLD = 25.0f

        // How far back (in samples) to look for a free-fall dip before impact.
        // At ~50 Hz that's roughly 0.6 s, which covers a typical pre-impact
        // free-fall window.
        private const val FREE_FALL_LOOKBACK = 30

        // Post-impact stillness thresholds.
        private const val STILLNESS_STD_THRESHOLD = 1.8f
        private const val STILLNESS_MEAN_TOLERANCE = 2.5f
    }

    fun computeMagnitudes(flatWindow: FloatArray, windowSize: Int): FloatArray {
        val mags = FloatArray(windowSize)
        for (i in 0 until windowSize) {
            val ax = flatWindow[i * 6 + 0]
            val ay = flatWindow[i * 6 + 1]
            val az = flatWindow[i * 6 + 2]
            mags[i] = sqrt(ax * ax + ay * ay + az * az)
        }
        return mags
    }

    /** Returns the index of the largest magnitude if it clears IMPACT_THRESHOLD, else -1. */
    fun findImpactIndex(magnitudes: FloatArray): Int {
        var peakIdx = -1
        var peakVal = 0f
        for (i in magnitudes.indices) {
            if (magnitudes[i] > peakVal) {
                peakVal = magnitudes[i]
                peakIdx = i
            }
        }
        return if (peakVal >= IMPACT_THRESHOLD) peakIdx else -1
    }

    fun hasFreeFallBeforeImpact(magnitudes: FloatArray, impactIdx: Int): Boolean {
        if (impactIdx <= 0) return false
        val start = maxOf(0, impactIdx - FREE_FALL_LOOKBACK)
        for (i in start until impactIdx) {
            if (magnitudes[i] < FREE_FALL_THRESHOLD) return true
        }
        return false
    }

    /**
     * True when the collected post-impact magnitudes indicate the person is
     * on the ground and not moving: mean stays near gravity and standard
     * deviation is low. A person who tripped but recovered will keep moving
     * and fail this check.
     */
    fun isPostImpactStill(postImpactMagnitudes: List<Float>): Boolean {
        if (postImpactMagnitudes.size < 20) return false

        var sum = 0f
        for (v in postImpactMagnitudes) sum += v
        val mean = sum / postImpactMagnitudes.size

        var variance = 0f
        for (v in postImpactMagnitudes) {
            val d = v - mean
            variance += d * d
        }
        variance /= postImpactMagnitudes.size
        val std = sqrt(variance)

        val meanNearGravity = abs(mean - GRAVITY) < STILLNESS_MEAN_TOLERANCE
        val lowVariance = std < STILLNESS_STD_THRESHOLD

        return meanNearGravity && lowVariance
    }
}
