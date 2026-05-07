package com.example.falldetectapp

import kotlin.math.sqrt

class SensorBuffer(private val windowSize: Int) {

    private val accelData = ArrayDeque<FloatArray>()
    private val gyroData = ArrayDeque<FloatArray>()

    fun addAccelerometer(values: FloatArray) {
        if (accelData.size >= windowSize) accelData.removeFirst()
        accelData.addLast(values)
    }

    fun addGyroscope(values: FloatArray) {
        if (gyroData.size >= windowSize) gyroData.removeFirst()
        gyroData.addLast(values)
    }

    fun isFull(): Boolean {
        return accelData.size == windowSize && gyroData.size == windowSize
    }

    fun getFlattenedWindow(): FloatArray {
        val result = FloatArray(windowSize * 6)
        val accIter = accelData.iterator()
        val gyroIter = gyroData.iterator()
        var i = 0
        while (accIter.hasNext() && gyroIter.hasNext()) {
            val acc = accIter.next()
            val gyro = gyroIter.next()
            result[i * 6 + 0] = acc[0]
            result[i * 6 + 1] = acc[1]
            result[i * 6 + 2] = acc[2]
            result[i * 6 + 3] = gyro[0]
            result[i * 6 + 4] = gyro[1]
            result[i * 6 + 5] = gyro[2]
            i++
        }
        return result
    }

    /**
     * Peak accelerometer magnitude in the current buffer. Cheap gate to skip
     * inference on windows that obviously don't contain an impact.
     */
    fun peakAccelMagnitude(): Float {
        var peak = 0f
        for (v in accelData) {
            val m = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
            if (m > peak) peak = m
        }
        return peak
    }

    /**
     * Slide the window forward by [step] samples instead of clearing it.
     * Preserves continuity so the model sees overlapping windows and so we
     * can run validation gates across window boundaries.
     */
    fun advance(step: Int) {
        repeat(step.coerceAtMost(accelData.size)) { accelData.removeFirst() }
        repeat(step.coerceAtMost(gyroData.size)) { gyroData.removeFirst() }
    }

    fun clear() {
        accelData.clear()
        gyroData.clear()
    }
}
