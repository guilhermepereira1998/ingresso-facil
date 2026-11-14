package com.wildrift.aicoach.overlay

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.wildrift.aicoach.R
import com.wildrift.aicoach.brain.TacticalCallout
import com.wildrift.aicoach.brain.TacticalPriority
import com.wildrift.aicoach.capture.ScreenCaptureService
import com.wildrift.aicoach.voice.VoiceCoachManager

class FloatingOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var params: WindowManager.LayoutParams
    private var testVoiceCoach: VoiceCoachManager? = null

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    override fun onCreate() {
        super.onCreate()
        startForegroundOverlay()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_bubble, null)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 260
        }

        val bubbleRoot = overlayView.findViewById<FrameLayout>(R.id.bubbleRoot)
        val miniPanel = overlayView.findViewById<LinearLayout>(R.id.miniPanel)
        val tvStatus = overlayView.findViewById<TextView>(R.id.tvOverlayStatus)
        val btnTestVoice = overlayView.findViewById<Button>(R.id.btnOverlayTestVoice)
        val btnClose = overlayView.findViewById<Button>(R.id.btnOverlayClose)

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var downTime = 0L

        bubbleRoot.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    downTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(overlayView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = kotlin.math.abs(event.rawX - initialTouchX)
                    val dy = kotlin.math.abs(event.rawY - initialTouchY)
                    val elapsed = System.currentTimeMillis() - downTime

                    if (dx < 12 && dy < 12) {
                        if (elapsed >= 450L) {
                            miniPanel.visibility = if (miniPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                        } else {
                            ScreenCaptureService.isPaused = !ScreenCaptureService.isPaused
                            val paused = ScreenCaptureService.isPaused
                            bubbleRoot.setBackgroundResource(
                                if (paused) R.drawable.bg_bubble_paused else R.drawable.bg_bubble_active
                            )
                            tvStatus.text = if (paused) "Coach: PAUSADO" else "Coach: ATIVO (1 FPS)"
                        }
                    }
                    true
                }
                else -> false
            }
        }

        btnTestVoice.setOnClickListener {
            if (testVoiceCoach == null) testVoiceCoach = VoiceCoachManager(this)
            testVoiceCoach?.enqueue(
                TacticalCallout(
                    ruleId = "TEST_VOICE",
                    message = "Áudio tático calibrado. Bom jogo!",
                    priority = TacticalPriority.URGENTE
                )
            )
        }

        btnClose.setOnClickListener {
            stopService(Intent(this, ScreenCaptureService::class.java))
            stopSelf()
        }

        windowManager.addView(overlayView, params)
    }

    private fun startForegroundOverlay() {
        val channelId = "wr_overlay_channel"
        val channel = NotificationChannel(
            channelId,
            getString(R.string.channel_overlay_name),
            NotificationManager.IMPORTANCE_MIN
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Botão Flutuante AI Coach")
            .setSmallIcon(R.drawable.ic_coach)
            .build()

        startForeground(1002, notification)
    }

    override fun onDestroy() {
        testVoiceCoach?.shutdown()
        if (::overlayView.isInitialized) {
            runCatching { windowManager.removeView(overlayView) }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
