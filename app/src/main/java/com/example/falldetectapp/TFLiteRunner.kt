package com.example.falldetectapp

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Loads model.tflite and applies the train-time StandardScaler before inference.
 *
 * The training pipeline z-scores every sample channel using statistics fit on
 * the training subjects only. The model's distributional assumptions only hold
 * if we apply the same transform on-device — feeding raw m/s² + rad/s gives
 * meaningless probabilities. Mean / scale are loaded from
 * assets/deploy_config.json which is produced alongside model.tflite.
 *
 * Input layout: FloatArray of length 600, channel-interleaved per timestep
 * (acc_x, acc_y, acc_z, gyro_x, gyro_y, gyro_z) × 100 timesteps. This matches
 * what SensorBuffer produces and what the training pipeline standardizes.
 */
class TFLiteRunner(private val context: Context) {

    companion object {
        private const val WINDOW_SIZE = 100
        private const val CHANNELS = 6
        private const val MODEL_FILE = "model.tflite"
        private const val CONFIG_FILE = "deploy_config.json"
    }

    private val interpreter: Interpreter
    private val mean: FloatArray
    private val scale: FloatArray

    /** Threshold tuned on val set; exposed so the service uses the model's own threshold. */
    val threshold: Float

    init {
        interpreter = Interpreter(loadModelFile())
        val (m, s, t) = loadScaler()
        mean = m
        scale = s
        threshold = t
        Log.i("TFLiteRunner", "Loaded model with threshold=$threshold mean=${mean.toList()} scale=${scale.toList()}")
    }

    private fun loadModelFile(): ByteBuffer {
        val fd = context.assets.openFd(MODEL_FILE)
        FileInputStream(fd.fileDescriptor).use { fis ->
            return fis.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        }
    }

    private fun loadScaler(): Triple<FloatArray, FloatArray, Float> {
        val text = context.assets.open(CONFIG_FILE).bufferedReader().use { it.readText() }
        val obj = JSONObject(text)
        val m = obj.getJSONArray("scaler_mean")
        val s = obj.getJSONArray("scaler_scale")
        require(m.length() == CHANNELS && s.length() == CHANNELS) {
            "deploy_config.json scaler must have $CHANNELS entries"
        }
        val mean = FloatArray(CHANNELS) { m.getDouble(it).toFloat() }
        val scale = FloatArray(CHANNELS) { s.getDouble(it).toFloat() }
        for (i in 0 until CHANNELS) {
            require(scale[i] > 0f) { "scaler_scale[$i] must be > 0" }
        }
        val thr = obj.getDouble("threshold").toFloat()
        return Triple(mean, scale, thr)
    }

    /**
     * @param input length-600 FloatArray, raw acc(m/s²) + gyro(rad/s), channel-interleaved.
     * @return sigmoid probability ∈ [0, 1] that the window contains a fall.
     */
    fun runInference(input: FloatArray): Float {
        require(input.size == WINDOW_SIZE * CHANNELS) {
            "Expected ${WINDOW_SIZE * CHANNELS} samples, got ${input.size}"
        }
        val buf = ByteBuffer.allocateDirect(WINDOW_SIZE * CHANNELS * 4).order(ByteOrder.nativeOrder())
        for (i in input.indices) {
            val ch = i % CHANNELS
            buf.putFloat((input[i] - mean[ch]) / scale[ch])
        }
        buf.rewind()
        val output = Array(1) { FloatArray(1) }
        interpreter.run(buf, output)
        return output[0][0]
    }
}
