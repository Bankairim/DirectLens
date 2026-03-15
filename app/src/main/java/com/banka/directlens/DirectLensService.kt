package com.banka.directlens

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Display
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class DirectLensService : AccessibilityService() {

    companion object {
        @SuppressLint("StaticFieldLeak")
        var instance: DirectLensService? = null
    }

    private var windowManager: WindowManager? = null
    private val overlayViews = mutableListOf<View>()
    private var feedbackView: View? = null
    private val executor: Executor = Executors.newSingleThreadExecutor()
    private lateinit var configManager: OverlayConfigurationManager
    private val prefs: SharedPreferences by lazy { getSharedPreferences("directlens_prefs", Context.MODE_PRIVATE) }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> 
        if (key == "overlay_config" || key == "master_enabled") updateOverlay() 
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        configManager = OverlayConfigurationManager(this)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        updateOverlay()
    }

    private fun updateOverlay() {
        Handler(Looper.getMainLooper()).post {
            val config = configManager.getConfig()
            val masterEnabled = prefs.getBoolean("master_enabled", true)

            overlayViews.forEach { try { windowManager?.removeView(it) } catch (e: Exception) {} }
            overlayViews.clear()

            if (!masterEnabled || !config.isEnabled) return@post

            config.segments.forEachIndexed { index, segment ->
                if (!segment.isEnabled) return@forEachIndexed

                val view = View(this)
                val params = WindowManager.LayoutParams(
                    if (segment.width <= 0) resources.displayMetrics.widthPixels else segment.width,
                    segment.height,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                )

                params.gravity = Gravity.TOP or Gravity.START
                params.x = segment.xOffset
                params.y = segment.yOffset

                if (config.isVisible) {
                    val colors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.MAGENTA)
                    val color = colors[index % colors.size]
                    val drawable = android.graphics.drawable.GradientDrawable().apply {
                        setColor(Color.argb(100, Color.red(color), Color.green(color), Color.blue(color)))
                        if (index == config.activeSegmentIndex) setStroke(5, Color.WHITE)
                    }
                    view.background = drawable
                } else {
                    view.setBackgroundColor(Color.TRANSPARENT)
                }

                attachTouchListener(view, segment)

                try {
                    windowManager?.addView(view, params)
                    overlayViews.add(view)
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    private fun showRainbowFlash() {
        if (!prefs.getBoolean("rainbow_flash_enabled", true)) return
        removeFeedbackView()

        val googleColors = intArrayOf(
            Color.parseColor("#4285F4"), // Blue
            Color.parseColor("#EA4335"), // Red
            Color.parseColor("#FBBC05"), // Yellow
            Color.parseColor("#34A853"), // Green
            Color.parseColor("#4285F4")  // Repeat Blue
        )

        val flashView = object : View(this) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            private val shaderMatrix = Matrix()
            var scanProgress = 0f
                set(value) {
                    field = value
                    invalidate()
                }

            override fun onDraw(canvas: Canvas) {
                val w = width.toFloat()
                val h = height.toFloat()
                if (w == 0f || h == 0f) return
                if (paint.shader == null) {
                    paint.shader = LinearGradient(0f, 0f, w * 0.8f, h * 0.8f, googleColors, null, Shader.TileMode.REPEAT)
                }
                shaderMatrix.setTranslate(scanProgress * w * 2.5f - w, scanProgress * h * 2.5f - h)
                paint.shader?.setLocalMatrix(shaderMatrix)
                canvas.drawRect(0f, 0f, w, h, paint)
            }
        }
        flashView.alpha = 0.15f
        feedbackView = flashView

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        try {
            windowManager?.addView(flashView, params)
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1500
                addUpdateListener { animator ->
                    val p = animator.animatedValue as Float
                    flashView.scanProgress = p
                    if (p > 0.6f) flashView.alpha = 0.15f * (1f - (p - 0.6f) * 2.5f)
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) { removeFeedbackView() }
                })
                start()
            }
        } catch (e: Exception) {}
    }

    private fun removeFeedbackView() {
        feedbackView?.let {
            try { windowManager?.removeViewImmediate(it) } catch (e: Exception) {}
            feedbackView = null
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachTouchListener(view: View, segment: OverlaySegment) {
        val handler = Handler(Looper.getMainLooper())
        var isTouching = false
        var hasTriggered = false
        var startX = 0f
        var startY = 0f

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (!hasTriggered) propagateSingleTap(view, e.rawX, e.rawY)
                return true
            }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (vy < -500 && !hasTriggered) { 
                    isTouching = false
                    handler.removeCallbacksAndMessages(null)
                    return false 
                }
                return super.onFling(e1, e2, vx, vy)
            }
        })

        val startVibrationRunnable = Runnable {
            if (!isTouching || hasTriggered) return@Runnable
            val hapticStrength = prefs.getInt("haptic_strength", 50)
            if (hapticStrength == 0) return@Runnable
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            val mult = (hapticStrength / 50f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    val composition = VibrationEffect.startComposition()
                    for (i in 0 until 25) {
                        val progress = i.toFloat() / 24f
                        val scale = (0.05f + (0.35f * progress * progress)) * mult
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, scale.coerceIn(0.01f, 1.0f), 15)
                    }
                    vibrator.vibrate(composition.compose())
                } catch (e: Exception) {}
            }
        }

        val triggerRunnable = Runnable {
            if (!isTouching || hasTriggered) return@Runnable
            hasTriggered = true
            (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).cancel()
            executeActionSequence()
        }

        view.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isTouching = true; hasTriggered = false; startX = event.rawX; startY = event.rawY
                    BitmapCache.bitmap = null 
                    handler.postDelayed(startVibrationRunnable, 50)
                    handler.postDelayed(triggerRunnable, 500)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (Math.abs(event.rawX - startX) > 80 || Math.abs(event.rawY - startY) > 80) {
                        if (!hasTriggered) {
                            isTouching = false
                            handler.removeCallbacksAndMessages(null)
                            (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).cancel()
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!hasTriggered) {
                        isTouching = false
                        handler.removeCallbacksAndMessages(null)
                        (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).cancel()
                    }
                }
            }
            true
        }
    }

    internal fun executeActionSequence(skipVibration: Boolean = false) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        takeScreenshot(Display.DEFAULT_DISPLAY, executor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val hwBuffer = result.hardwareBuffer
                val bitmap = Bitmap.wrapHardwareBuffer(hwBuffer, result.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                hwBuffer.close()
                Handler(Looper.getMainLooper()).post {
                    if (bitmap != null) {
                        BitmapCache.bitmap = bitmap
                        showRainbowFlash() 
                        
                        if (!skipVibration) {
                            val hapticStrength = prefs.getInt("haptic_strength", 50)
                            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                            if (hapticStrength > 0) {
                                val amp = (hapticStrength * 2.55f).toInt().coerceIn(1, 255)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(40, amp))
                                else vibrator.vibrate(40)
                            }
                        }

                        Handler(Looper.getMainLooper()).postDelayed({
                            startActivity(Intent(this@DirectLensService, TrampolineActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                            })
                        }, 120)
                    }
                }
            }
            override fun onFailure(errorCode: Int) {}
        })
    }

    private fun propagateSingleTap(view: View, x: Float, y: Float) {
        val params = view.layoutParams as WindowManager.LayoutParams
        val originalFlags = params.flags
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        try { windowManager?.updateViewLayout(view, params) } catch (e: Exception) {}
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { restoreFlags() }
            override fun onCancelled(gestureDescription: GestureDescription?) { restoreFlags() }
            fun restoreFlags() {
                Handler(Looper.getMainLooper()).postDelayed({
                    params.flags = originalFlags
                    try { windowManager?.updateViewLayout(view, params) } catch (e: Exception) {}
                }, 50)
            }
        }, null)
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        overlayViews.forEach { try { windowManager?.removeView(it) } catch (e: Exception) {} }
        removeFeedbackView()
    }
}
