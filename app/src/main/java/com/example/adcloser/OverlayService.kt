package com.example.adcloser

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class OverlayService : Service() {

    companion object {
        private const val CHANNEL_ID = "adcloser_overlay_channel"
        private const val NOTIF_ID = 1001
        private const val PREFS_NAME = "ad_closer_prefs"
        private const val KEY_ENABLED = "service_enabled"
        private const val KEY_BUBBLE_X = "bubble_x"
        private const val KEY_BUBBLE_Y = "bubble_y"
        const val FROGS_PACKAGE = "com.outloud.idlefrog"
    }

    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
        showBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showBubble() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_bubble, null)
        bubbleView = view

        val bubbleIcon = view.findViewById<ImageView>(R.id.bubbleIcon)
        val stopIcon = view.findViewById<ImageView>(R.id.stopIcon)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt(KEY_BUBBLE_X, 24)
            y = prefs.getInt(KEY_BUBBLE_Y, 220)
        }
        layoutParams = params

        updateBubbleIcon(bubbleIcon, prefs.getBoolean(KEY_ENABLED, false))

        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var isDragging = false

        bubbleIcon.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        isDragging = true
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager?.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        val current = prefs.getBoolean(KEY_ENABLED, false)
                        val next = !current
                        prefs.edit()
                            .putBoolean(KEY_ENABLED, next)
                            .putBoolean("ai_detection_enabled", true)
                            .apply()
                        updateBubbleIcon(bubbleIcon, next)
                        if (next) {
                            Toast.makeText(
                                this,
                                "🐸 ĐÃ BẮT ĐẦU AUTO FROGS KITCHEN 100%!",
                                Toast.LENGTH_SHORT
                            ).show()
                            launchFrogsKitchenIfInstalled()
                        } else {
                            Toast.makeText(
                                this,
                                "⏸ Đã tạm dừng Auto Frogs Kitchen",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        prefs.edit()
                            .putInt(KEY_BUBBLE_X, params.x)
                            .putInt(KEY_BUBBLE_Y, params.y)
                            .apply()
                    }
                    true
                }
                else -> false
            }
        }

        stopIcon.setOnClickListener {
            savePositionAndStop()
        }

        windowManager?.addView(view, params)
    }

    private fun launchFrogsKitchenIfInstalled() {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(FROGS_PACKAGE)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
            }
        } catch (_: Exception) {
        }
    }

    private fun updateBubbleIcon(bubbleIcon: ImageView, enabled: Boolean) {
        if (enabled) {
            bubbleIcon.setImageResource(R.drawable.ic_pause_white)
            bubbleView?.alpha = 1.0f
        } else {
            bubbleIcon.setImageResource(R.drawable.ic_play_white)
            bubbleView?.alpha = 0.8f
        }
    }

    private fun savePositionAndStop() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        layoutParams?.let {
            prefs.edit()
                .putInt(KEY_BUBBLE_X, it.x)
                .putInt(KEY_BUBBLE_Y, it.y)
                .putBoolean(KEY_ENABLED, false)
                .apply()
        }
        stopSelf()
    }

    private fun startForegroundWithNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Frogs Kitchen Auto Bong bóng nổi",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Frogs Kitchen Auto AI đang chạy")
            .setContentText("Ấn nút Play trên bong bóng nổi để AI tự chơi 100%")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(NOTIF_ID, notification)
    }

    override fun onDestroy() {
        bubbleView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {
            }
        }
        bubbleView = null
        super.onDestroy()
    }
}
