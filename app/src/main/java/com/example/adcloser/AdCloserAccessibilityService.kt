package com.example.adcloser

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class AdCloserAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "FrogsAutoService"
        private const val PREFS_NAME = "ad_closer_prefs"
        private const val KEY_ENABLED = "service_enabled"
        private const val KEY_APPLY_ALL = "apply_all_apps"
        private const val KEY_TARGET_PACKAGES = "target_packages"
        private const val KEY_AI_ENABLED = "ai_detection_enabled"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        const val FROGS_PACKAGE = "com.outloud.idlefrog"
    }

    private lateinit var prefs: SharedPreferences
    private val pollHandler = Handler(Looper.getMainLooper())
    private val pollIntervalMs = 2200L
    private var isAiBusy = false
    private var lastAiStepTime = 0L
    private val aiStepCooldownMs = 2000L
    private var lastActivePackage: String = ""

    private val excludedPackages = mutableSetOf(
        "com.android.systemui",
        "com.example.adcloser"
    )

    private val aiHelper by lazy { GeminiVisionHelper(this) }

    private val pollRunnable = object : Runnable {
        override fun run() {
            try {
                if (isEnabled()) {
                    performAutoPlayTick()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi khi chạy vòng lặp Auto: ${e.message}")
            } finally {
                pollHandler.postDelayed(this, pollIntervalMs)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Ensure default target package contains Frogs Kitchen
        if (!prefs.contains(KEY_TARGET_PACKAGES)) {
            prefs.edit().putStringSet(KEY_TARGET_PACKAGES, setOf(FROGS_PACKAGE)).apply()
        }

        // Exclude system home launcher
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
            val resolveInfo = packageManager.resolveActivity(homeIntent, 0)
            resolveInfo?.activityInfo?.packageName?.let { excludedPackages.add(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Không xác định được launcher package: ${e.message}")
        }

        pollHandler.removeCallbacks(pollRunnable)
        pollHandler.post(pollRunnable)
        Log.i(TAG, "Frogs Kitchen Auto Service connected!")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString()
        if (!pkg.isNullOrBlank() && pkg != "com.example.adcloser" && pkg != "com.android.systemui") {
            lastActivePackage = pkg
        }
    }

    private fun isEnabled(): Boolean {
        return ::prefs.isInitialized && prefs.getBoolean(KEY_ENABLED, false)
    }

    private fun isPackageAllowed(pkg: String): Boolean {
        if (pkg.isBlank()) return true
        if (excludedPackages.contains(pkg)) return false
        if (pkg == FROGS_PACKAGE) return true
        // Also allow common ad SDK activities or Google Play overlay inside the game
        val applyAll = prefs.getBoolean(KEY_APPLY_ALL, false)
        if (applyAll) return true
        val targets = prefs.getStringSet(KEY_TARGET_PACKAGES, setOf(FROGS_PACKAGE)) ?: setOf(FROGS_PACKAGE)
        return targets.contains(pkg)
    }

    private fun performAutoPlayTick() {
        val root = rootInActiveWindow
        val currentPkg = root?.packageName?.toString() ?: lastActivePackage

        if (currentPkg.isNotBlank() && !isPackageAllowed(currentPkg)) {
            return
        }

        // 1. Check if there is an obvious native ad close button on screen first (instant close)
        if (root != null) {
            val closeNode = findNativeCloseOrActionNode(root)
            if (closeNode != null) {
                clickNode(closeNode)
                return
            }
        }

        // 2. Run AI 100% Auto-Player for Frogs Kitchen
        if (isAiBusy) return
        val now = System.currentTimeMillis()
        if (now - lastAiStepTime < aiStepCooldownMs) return

        val apiKey = prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
        if (apiKey.isBlank()) {
            Toast.makeText(
                this,
                "⚠️ Hãy nhập Gemini API Key trong app để AI tự chơi Frogs Kitchen!",
                Toast.LENGTH_SHORT
            ).show()
            lastAiStepTime = now + 4000L
            return
        }

        if (!aiHelper.hasCaptureMethod()) {
            Toast.makeText(
                this,
                "⚠️ Cần cấp quyền chụp màn hình trong app cho Android dưới 11",
                Toast.LENGTH_SHORT
            ).show()
            lastAiStepTime = now + 4000L
            return
        }

        isAiBusy = true
        lastAiStepTime = now
        aiHelper.analyzeAndPlayGame(apiKey) { _ ->
            isAiBusy = false
            lastAiStepTime = System.currentTimeMillis()
        }
    }

    private fun findNativeCloseOrActionNode(node: AccessibilityNodeInfo, depth: Int = 0): AccessibilityNodeInfo? {
        if (depth > 25) return null
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val combined = "$text $desc $viewId"

        val keywords = listOf(
            "close_button", "ad_close", "interstitial_close", "btn_close",
            "skip ad", "bỏ qua quảng cáo", "close ad", "đóng quảng cáo"
        )
        if (keywords.any { combined.contains(it) }) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNativeCloseOrActionNode(child, depth + 1)
            if (found != null) return found
        }
        return null
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() > 0 && rect.height() > 0) {
            val cx = rect.exactCenterX()
            val cy = rect.exactCenterY()
            val path = Path().apply { moveTo(cx, cy) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, 100L))
                .build()
            return dispatchGesture(gesture, null, null)
        }
        return false
    }

    override fun onInterrupt() {
        pollHandler.removeCallbacks(pollRunnable)
    }

    override fun onDestroy() {
        pollHandler.removeCallbacks(pollRunnable)
        super.onDestroy()
    }
}
