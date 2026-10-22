package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.GeminiFlashClient
import com.example.data.QuizPreset
import com.example.data.QuizPresets
import com.example.data.SolveResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Foreground Service hosting the Floating HUD Widget over other quiz apps.
 * Implements the dual-state master switch (Active / تشغيل vs Standby / إيقاف),
 * instant single-letter flash answer popup, and pure asynchronous Coroutine execution.
 */
class FloatingSolverService : Service() {

    companion object {
        const val CHANNEL_ID = "flashsolve_channel"
        const val NOTIFICATION_ID = 2001

        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()

        private val _isSolverActive = MutableStateFlow(true)
        val isSolverActive = _isSolverActive.asStateFlow()

        private val _latestResult = MutableStateFlow<SolveResponse?>(null)
        val latestResult = _latestResult.asStateFlow()

        var customApiKey: String? = null
        var activeQuestionPrompt: String = QuizPresets.items.first().questionPrompt
    }

    private var windowManager: WindowManager? = null
    private var overlayRoot: View? = null
    private var windowLayoutParams: WindowManager.LayoutParams? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var solveJob: Job? = null

    // UI Elements inside floating layout
    private lateinit var statusIndicatorDot: View
    private lateinit var statusTextView: TextView
    private lateinit var toggleModeButton: LinearLayout
    private lateinit var toggleModeLabel: TextView
    private lateinit var solveButton: LinearLayout
    private lateinit var solveButtonLabel: TextView
    private lateinit var flashAnswerCard: LinearLayout
    private lateinit var answerBadgeView: TextView
    private lateinit var latencyBadgeView: TextView
    private lateinit var explanationView: TextView

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        startForegroundServiceWithNotification()
        setupFloatingWindowManager()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        _isRunning.value = false
        overlayRoot?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {}
        }
        overlayRoot = null
    }

    private fun startForegroundServiceWithNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FlashSolve Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the real-time floating quiz solver active"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FlashSolve Floating Assistant")
            .setContentText("Tap overlay on any quiz to solve instantly with Gemini Flash")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingWindowManager() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 250
        }
        windowLayoutParams = layoutParams

        val rootLayout = buildOverlayView()
        overlayRoot = rootLayout

        // Draggable touch handler
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        rootLayout.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    if (abs(deltaX) > 12 || abs(deltaY) > 12) {
                        isDragging = true
                        layoutParams.x = initialX + deltaX
                        layoutParams.y = initialY + deltaY
                        windowManager?.updateViewLayout(rootLayout, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // Considered a click if not dragged
                        rootLayout.performClick()
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(rootLayout, layoutParams)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to show overlay: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Programmatically constructs the sleek Cyberpunk Tactical HUD floating view.
     * Programmatic construction ensures sub-millisecond layout inflation and zero dropped frames.
     */
    private fun buildOverlayView(): View {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        // Outer Container
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E60D1322")) // Translucent Cyber Midnight
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.parseColor("#00E676")) // Neon emerald border
            }
            elevation = dp(10).toFloat()
        }

        // Header Bar: Drag Handle + Master Switch + Close Button
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }

        // State Indicator Dot
        statusIndicatorDot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply {
                marginEnd = dp(6)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#00E676")) // Active Green
            }
        }

        statusTextView = TextView(this).apply {
            text = "Active / جاهز"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        // Master Switch Button: [Start / تشغيل] or [Stop / إيقاف]
        toggleModeButton = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1F293D"))
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.parseColor("#475569"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(6)
            }
        }

        toggleModeLabel = TextView(this).apply {
            text = "إيقاف (Stop)"
            setTextColor(Color.parseColor("#EF4444")) // Crimson
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
        }
        toggleModeButton.addView(toggleModeLabel)

        // Close Overlay Button
        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(4), dp(2), dp(4), dp(2))
            setOnClickListener {
                stopSelf()
            }
        }

        header.addView(statusIndicatorDot)
        header.addView(statusTextView)
        header.addView(toggleModeButton)
        header.addView(closeBtn)
        container.addView(header)

        // Master Switch Toggle Click
        toggleModeButton.setOnClickListener {
            vibrateFeedback()
            val newState = !_isSolverActive.value
            _isSolverActive.value = newState
            updateMasterStateUI(newState)
        }

        // Action Section: Trigger Solve Button
        solveButton = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#00E676")) // Vibrant Emerald
                cornerRadius = dp(12).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        solveButtonLabel = TextView(this).apply {
            text = "⚡ SOLVE / حل الآن"
            setTextColor(Color.BLACK)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }
        solveButton.addView(solveButtonLabel)
        container.addView(solveButton)

        // Instant Flash Answer Card (Hidden by default)
        flashAnswerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#131B2A"))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.parseColor("#F59E0B")) // Amber highlight
            }
            layoutParams = LinearLayout.LayoutParams(
                dp(220),
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
            }
        }

        val answerHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        answerBadgeView = TextView(this).apply {
            text = "[ B ]"
            setTextColor(Color.parseColor("#F59E0B")) // Bright Amber
            textSize = 24f
            typeface = Typeface.MONOSPACE
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        latencyBadgeView = TextView(this).apply {
            text = "⚡ 750ms"
            setTextColor(Color.parseColor("#00E676"))
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
        }

        answerHeaderRow.addView(answerBadgeView)
        answerHeaderRow.addView(latencyBadgeView)
        flashAnswerCard.addView(answerHeaderRow)

        explanationView = TextView(this).apply {
            text = "Explanation appears here..."
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 12f
            setPadding(0, dp(6), 0, 0)
        }
        flashAnswerCard.addView(explanationView)

        // Tap answer card to dismiss
        flashAnswerCard.setOnClickListener {
            flashAnswerCard.visibility = View.GONE
        }

        container.addView(flashAnswerCard)

        // Solve Button Click Listener
        solveButton.setOnClickListener {
            handleSolveTrigger()
        }

        return container
    }

    private fun updateMasterStateUI(isActive: Boolean) {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        if (isActive) {
            statusIndicatorDot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#00E676")) // Emerald Green
            }
            statusTextView.text = "Active / جاهز"
            statusTextView.setTextColor(Color.WHITE)
            toggleModeLabel.text = "إيقاف (Stop)"
            toggleModeLabel.setTextColor(Color.parseColor("#EF4444"))
            solveButton.background = GradientDrawable().apply {
                setColor(Color.parseColor("#00E676"))
                cornerRadius = dp(12).toFloat()
            }
            solveButtonLabel.setTextColor(Color.BLACK)
            solveButtonLabel.text = "⚡ SOLVE / حل الآن"
        } else {
            statusIndicatorDot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#EF4444")) // Crimson Red
            }
            statusTextView.text = "Standby / متوقف"
            statusTextView.setTextColor(Color.parseColor("#94A3B8"))
            toggleModeLabel.text = "تشغيل (Start)"
            toggleModeLabel.setTextColor(Color.parseColor("#00E676"))
            solveButton.background = GradientDrawable().apply {
                setColor(Color.parseColor("#334155")) // Muted Slate
                cornerRadius = dp(12).toFloat()
            }
            solveButtonLabel.setTextColor(Color.parseColor("#94A3B8"))
            solveButtonLabel.text = "⏸ Paused / متوقف"
        }
    }

    private fun handleSolveTrigger() {
        if (!_isSolverActive.value) {
            vibrateFeedback()
            Toast.makeText(this, "Solver is in Standby. Tap 'تشغيل (Start)' to activate.", Toast.LENGTH_SHORT).show()
            return
        }

        vibrateFeedback()
        solveButtonLabel.text = "⏳ Solving with Flash..."
        flashAnswerCard.visibility = View.GONE

        solveJob?.cancel()
        solveJob = serviceScope.launch {
            val response = GeminiFlashClient.solveText(activeQuestionPrompt, customApiKey)
            _latestResult.value = response

            solveButtonLabel.text = "⚡ SOLVE / حل الآن"
            flashAnswerCard.visibility = View.VISIBLE

            if (response.isSuccess) {
                answerBadgeView.text = "[ ${response.answer} ]"
                answerBadgeView.setTextColor(Color.parseColor("#F59E0B"))
                latencyBadgeView.text = "⚡ ${response.latencyMs}ms"
                explanationView.text = response.explanation
            } else {
                answerBadgeView.text = "[ ! ]"
                answerBadgeView.setTextColor(Color.parseColor("#EF4444"))
                latencyBadgeView.text = "${response.latencyMs}ms"
                explanationView.text = response.explanation
            }
        }
    }

    private fun vibrateFeedback() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(35)
            }
        } catch (_: Exception) {}
    }
}
