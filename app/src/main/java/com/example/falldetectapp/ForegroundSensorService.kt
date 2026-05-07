package com.example.falldetectapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.SubscriptionManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.telephony.SmsManager
import kotlin.math.sqrt

class ForegroundSensorService : Service(), SensorEventListener {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_CANCEL_ALERT = "ACTION_CANCEL_ALERT"
        const val ACTION_TEST_ALERT = "ACTION_TEST_ALERT"
        const val EXTRA_ADAPTIVE_MODE = "EXTRA_ADAPTIVE_MODE"

        private const val CHANNEL_ID = "FallDetectionChannelV2"
        private const val NOTIFICATION_ID = 1

        private const val WINDOW_SIZE = 100
        // Slide the window by 50 samples (~1 s at 50 Hz) so we get overlapping
        // inferences instead of dropping context after every prediction.
        private const val WINDOW_STRIDE = 50
        private const val HIGH_RATE = SensorManager.SENSOR_DELAY_GAME
        private const val LOW_RATE = SensorManager.SENSOR_DELAY_NORMAL

        // Raised from 0.80 -> 0.92. The model output is one of two gates;
        // physics validation is the other, so we can afford strict ML
        // confidence without missing real falls.
        private const val FALL_CONFIDENCE_THRESHOLD = 0.92f

        // Two high-confidence inferences within this window count toward
        // ML confirmation. Keeps spurious single-window spikes from
        // triggering an alert on their own.
        private const val ML_CONFIRM_WINDOW_MS = 2_500L

        // Require an impact peak in the raw accelerometer data before even
        // running the model. Matches FallValidator.IMPACT_THRESHOLD.
        private const val PRE_INFERENCE_IMPACT_GATE = FallValidator.IMPACT_THRESHOLD

        // How long to watch for post-impact stillness before deciding.
        private const val POST_IMPACT_OBSERVATION_MS = 2_500L

        private const val ALERT_DELAY_MS = 15_000L
        // Raised from 60 s to 3 min. If a false positive slips through, we
        // do not want it re-firing minutes later.
        private const val ALERT_COOLDOWN_MS = 180_000L

        private const val STATE_LOW = 0
        private const val STATE_HIGH = 1
    }

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    private lateinit var sensorBuffer: SensorBuffer
    private lateinit var tfliteRunner: TFLiteRunner
    private lateinit var wakeDetector: WakeDetector
    private lateinit var metricsLogger: MetricsLogger
    private lateinit var fallValidator: FallValidator

    private lateinit var locationManager: LocationManager

    private val handler = Handler(Looper.getMainLooper())
    private var alertPending = false
    private var alertRunnable: Runnable? = null
    private var currentAlertIsTest = false
    private var alertRingtone: Ringtone? = null
    private var lastAlertTriggerAtMs = 0L

    private var adaptiveMode = false
    private var currentSamplingRate = HIGH_RATE

    // Adaptive state machine
    private var currentState = STATE_LOW
    private var highStateStartTime = 0L
    private var calmStartTime = 0L

    private val MIN_HIGH_DURATION = 8000L
    private val CALM_DURATION_REQUIRED = 3000L

    // Two-phase verification state.
    private var lastHighConfidenceAtMs = 0L
    private var postImpactWatchUntilMs = 0L
    private var postImpactMagnitudes = ArrayList<Float>()
    private var awaitingPostImpactConfirmation = false
    private var pendingConfidence = 0f

    override fun onCreate() {
        super.onCreate()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        sensorBuffer = SensorBuffer(WINDOW_SIZE)
        tfliteRunner = TFLiteRunner(this)
        wakeDetector = WakeDetector()
        metricsLogger = MetricsLogger(this)
        fallValidator = FallValidator()

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        when (intent?.action) {

            ACTION_START -> {
                adaptiveMode = intent.getBooleanExtra(EXTRA_ADAPTIVE_MODE, false)
                startMonitoring()
            }

            ACTION_STOP -> stopMonitoring()

            ACTION_CANCEL_ALERT -> cancelPendingAlert()

            ACTION_TEST_ALERT -> onPossibleFallDetected(1.0f, true)
        }

        return START_STICKY
    }

    private fun startMonitoring() {

        startForegroundServiceInternal()

        currentState = STATE_LOW
        calmStartTime = 0L
        resetVerificationState()

        currentSamplingRate = if (adaptiveMode) LOW_RATE else HIGH_RATE

        registerSensors(currentSamplingRate)
        metricsLogger.startSession(adaptiveMode)

        Log.d("MODE", if (adaptiveMode) "ADAPTIVE" else "CONSTANT")

        AppStatusNotifier.setMonitoringActive(this, true)
    }

    private fun stopMonitoring() {
        unregisterSensors()
        metricsLogger.stopSession()
        stopForeground(true)
        stopSelf()

        AppStatusNotifier.setMonitoringActive(this, false)
    }

    private fun registerSensors(rate: Int) {
        accelerometer?.let {
            sensorManager.registerListener(this, it, rate)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, rate)
        }
    }

    private fun unregisterSensors() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {

        when (event.sensor.type) {

            Sensor.TYPE_ACCELEROMETER -> {

                sensorBuffer.addAccelerometer(event.values.clone())
                metricsLogger.logAccelerometer()

                val magnitude = sqrt(
                    event.values[0] * event.values[0] +
                            event.values[1] * event.values[1] +
                            event.values[2] * event.values[2]
                )

                if (adaptiveMode) {
                    handleAdaptiveLogic(magnitude)
                }

                // While we are watching for post-impact stillness, collect the
                // accelerometer magnitudes that arrive after the detected impact.
                if (awaitingPostImpactConfirmation) {
                    postImpactMagnitudes.add(magnitude)
                    if (System.currentTimeMillis() >= postImpactWatchUntilMs) {
                        evaluatePostImpactStillness()
                    }
                }
            }

            Sensor.TYPE_GYROSCOPE -> {
                sensorBuffer.addGyroscope(event.values.clone())
                metricsLogger.logGyroscope()
            }
        }

        if (sensorBuffer.isFull()) {
            analyzeCurrentWindow()
            // Slide instead of clear so we keep context across windows and
            // still get a fresh inference roughly every WINDOW_STRIDE samples.
            sensorBuffer.advance(WINDOW_STRIDE)
        }
    }

    private fun analyzeCurrentWindow() {
        // Cheap gate #1: don't bother the model if the window never saw a
        // plausible impact. Filters out the vast majority of daily motion.
        val peakMag = sensorBuffer.peakAccelMagnitude()
        if (peakMag < PRE_INFERENCE_IMPACT_GATE) {
            return
        }

        val input: FloatArray = sensorBuffer.getFlattenedWindow()

        // Gate #2: physics validation on the raw window. Requires both an
        // impact peak and a preceding free-fall dip.
        val magnitudes = fallValidator.computeMagnitudes(input, WINDOW_SIZE)
        val impactIdx = fallValidator.findImpactIndex(magnitudes)
        if (impactIdx < 0) return
        if (!fallValidator.hasFreeFallBeforeImpact(magnitudes, impactIdx)) {
            Log.d("INFERENCE", "Impact without free-fall precursor; skipping")
            return
        }

        // Gate #3: ML model.
        val confidence = tfliteRunner.runInference(input)
        metricsLogger.logInference()
        Log.d("INFERENCE", "Confidence=$confidence peak=$peakMag")

        if (confidence < FALL_CONFIDENCE_THRESHOLD) {
            return
        }

        // Gate #4: require two high-confidence hits close together OR one
        // high-confidence hit followed by post-impact stillness. We always
        // proceed to the stillness watch, but if we already saw a recent
        // high-confidence window we fast-track the stillness requirement.
        val now = System.currentTimeMillis()
        val recentMlHit = lastHighConfidenceAtMs > 0L &&
                now - lastHighConfidenceAtMs <= ML_CONFIRM_WINDOW_MS
        lastHighConfidenceAtMs = now

        if (awaitingPostImpactConfirmation) {
            // Already watching for stillness from an earlier impact in this
            // event. Keep the higher confidence value.
            if (confidence > pendingConfidence) pendingConfidence = confidence
            return
        }

        beginPostImpactWatch(confidence, fastTrack = recentMlHit)
    }

    private fun beginPostImpactWatch(confidence: Float, fastTrack: Boolean) {
        awaitingPostImpactConfirmation = true
        pendingConfidence = confidence
        postImpactMagnitudes.clear()
        postImpactWatchUntilMs = System.currentTimeMillis() +
                if (fastTrack) POST_IMPACT_OBSERVATION_MS / 2 else POST_IMPACT_OBSERVATION_MS

        Log.d(
            "INFERENCE",
            "Starting post-impact stillness watch (fastTrack=$fastTrack, confidence=$confidence)"
        )
    }

    private fun evaluatePostImpactStillness() {
        val still = fallValidator.isPostImpactStill(postImpactMagnitudes)
        val confidence = pendingConfidence
        val samples = postImpactMagnitudes.size

        resetVerificationState()

        if (still) {
            Log.d("INFERENCE", "Post-impact stillness confirmed (samples=$samples) -> ALERT")
            onPossibleFallDetected(confidence)
        } else {
            Log.d(
                "INFERENCE",
                "Post-impact motion detected (samples=$samples) -> no alert, likely not a fall"
            )
        }
    }

    private fun resetVerificationState() {
        awaitingPostImpactConfirmation = false
        postImpactWatchUntilMs = 0L
        postImpactMagnitudes.clear()
        pendingConfidence = 0f
    }

    private fun onPossibleFallDetected(confidence: Float, isTest: Boolean = false) {
        val now = System.currentTimeMillis()

        if (lastAlertTriggerAtMs > 0L && now - lastAlertTriggerAtMs < ALERT_COOLDOWN_MS) {
            Log.d("ALERT", "Ignored alert during cooldown window")
            return
        }

        if (alertPending) {
            // Already counting down for an alert, avoid stacking
            return
        }

        alertPending = true
        lastAlertTriggerAtMs = now
        currentAlertIsTest = isTest

        // Explicit buzz + beep when alert window starts
        try {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        600,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(600)
            }

            // Stop any previous alert sound if still playing
            stopAlertSound()

            // Use a louder, more urgent alarm-type sound instead of the normal notification tone
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            alertRingtone = RingtoneManager.getRingtone(applicationContext, notificationUri)
            alertRingtone?.play()
        } catch (e: Exception) {
            Log.w("ALERT", "Failed to play alert sound/vibration", e)
        }

        // Launch main activity so the user can cancel within 15 seconds
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("EXTRA_PENDING_ALERT", true)
        }
        startActivity(activityIntent)

        // Update notification to reflect pending alert
        showPendingAlertNotification()

        alertRunnable = Runnable {
            sendAlertSms(currentAlertIsTest)
            stopAlertSound()
            alertPending = false
            // After sending, go back to normal monitoring notification
            startForegroundServiceInternal()
        }

        handler.postDelayed(alertRunnable!!, ALERT_DELAY_MS)
    }

    private fun cancelPendingAlert() {
        if (!alertPending) return

        alertRunnable?.let { handler.removeCallbacks(it) }
        alertRunnable = null
        alertPending = false

        stopAlertSound()

        // Restore normal monitoring notification
        startForegroundServiceInternal()
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun getLastKnownLocation(): Location? {
        if (!hasLocationPermission()) return null

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        for (provider in providers) {
            try {
                val loc = locationManager.getLastKnownLocation(provider)
                if (loc != null) return loc
            } catch (_: SecurityException) {
                // Permission check already done
            }
        }

        return null
    }

    private fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun sendAlertSms(isTest: Boolean) {
        if (!hasSmsPermission()) {
            Log.w("ALERT", "SMS permission not granted, cannot send alert")
            return
        }

        val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val userName = prefs.getString("user_name", "User") ?: "User"
        val userPhone = prefs.getString("user_phone", "") ?: ""
        val caretakerName = prefs.getString("caretaker_name", "Caretaker") ?: "Caretaker"
        val caretakerPhone = prefs.getString("caretaker_phone", "") ?: ""
        val normalizedCaretakerPhone = normalizePhoneNumber(caretakerPhone)

        if (normalizedCaretakerPhone.isEmpty()) {
            Log.w("ALERT", "Caretaker phone not configured, cannot send alert")
            return
        }

        val location = getLastKnownLocation()

        val locationPart = if (location != null) {
            val lat = location.latitude
            val lon = location.longitude
            "Location: https://maps.google.com/?q=$lat,$lon"
        } else {
            "Location: unavailable"
        }

        val userPhonePart = if (userPhone.isNotEmpty()) {
            "User phone: $userPhone"
        } else {
            ""
        }

        val message = if (isTest) {
            // Keep test SMS short so it stays as one segment on most carriers.
            buildString {
                append("TEST ALERT: FallDetectApp check for $userName. ")
                append(locationPart)
            }
        } else {
            buildString {
                append("Fall detected for $userName. ")
                append("Please check on them immediately.\n")
                append("Caretaker: $caretakerName.\n")
                if (userPhonePart.isNotEmpty()) {
                    append("$userPhonePart\n")
                }
                append(locationPart)
            }
        }

        try {
            val defaultSmsSubId = SubscriptionManager.getDefaultSmsSubscriptionId()
            val smsManager = if (defaultSmsSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                SmsManager.getSmsManagerForSubscriptionId(defaultSmsSubId)
            } else {
                SmsManager.getDefault()
            }

            val messageParts = smsManager.divideMessage(message)
            if (messageParts.size > 1) {
                smsManager.sendMultipartTextMessage(
                    normalizedCaretakerPhone,
                    null,
                    ArrayList(messageParts),
                    null,
                    null
                )
            } else {
                smsManager.sendTextMessage(
                    normalizedCaretakerPhone,
                    null,
                    message,
                    null,
                    null
                )
            }
            Log.d(
                "ALERT",
                "Alert SMS sent to $normalizedCaretakerPhone (isTest=$isTest, parts=${messageParts.size})"
            )
        } catch (e: Exception) {
            Log.e("ALERT", "Failed to send SMS", e)
        }
    }

    private fun normalizePhoneNumber(rawNumber: String): String {
        if (rawNumber.isBlank()) return ""

        val trimmed = rawNumber.trim()
        val hasLeadingPlus = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }

        return if (hasLeadingPlus) "+$digits" else digits
    }

    private fun stopAlertSound() {
        try {
            alertRingtone?.stop()
        } catch (e: Exception) {
            Log.w("ALERT", "Failed to stop alert ringtone", e)
        } finally {
            alertRingtone = null
        }
    }

    private fun handleAdaptiveLogic(magnitude: Float) {

        val now = System.currentTimeMillis()

        when (currentState) {

            STATE_LOW -> {

                if (wakeDetector.isMovementDetected(magnitude)) {

                    currentState = STATE_HIGH
                    highStateStartTime = now

                    unregisterSensors()
                    // Keep existing buffer data: the free-fall dip that
                    // triggered the wake lives in those low-rate samples and
                    // is needed by FallValidator. Only reset verification
                    // state (not the buffer) so we don't lose evidence.
                    resetVerificationState()
                    registerSensors(HIGH_RATE)

                    currentSamplingRate = HIGH_RATE

                    Log.d("ADAPTIVE", "Switched to HIGH")
                }
            }

            STATE_HIGH -> {

                // Never drop to LOW while a post-impact stillness watch is
                // running — that would kill the data stream we need to confirm.
                if (awaitingPostImpactConfirmation) {
                    calmStartTime = 0L
                    return
                }

                val timeInHigh = now - highStateStartTime

                if (timeInHigh >= MIN_HIGH_DURATION) {

                    if (wakeDetector.isCalm(magnitude)) {

                        if (calmStartTime == 0L) {
                            calmStartTime = now
                        }

                        val calmDuration = now - calmStartTime

                        if (calmDuration >= CALM_DURATION_REQUIRED) {

                            currentState = STATE_LOW
                            calmStartTime = 0L

                            unregisterSensors()
                            sensorBuffer.clear()
                            resetVerificationState()
                            registerSensors(LOW_RATE)

                            currentSamplingRate = LOW_RATE

                            Log.d("ADAPTIVE", "Switched to LOW")
                        }

                    } else {
                        calmStartTime = 0L
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundServiceInternal() {

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Fall Detection Running")
            .setContentText("Monitoring sensors...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun showPendingAlertNotification() {

        // Open main activity so user can see countdown and cancel from inside the app
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("EXTRA_PENDING_ALERT", true)
        }
        val tapPendingIntent = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Possible fall detected")
            .setContentText("Alert will be sent in 15 seconds. Open the app to cancel if this is a mistake.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(tapPendingIntent)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Fall Detection",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                enableLights(true)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
