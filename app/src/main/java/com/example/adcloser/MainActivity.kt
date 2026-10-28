package com.example.adcloser

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.adcloser.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var pendingBubbleRequest = false

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Toast.makeText(this, "Đã cấp quyền, AI có thể tự chơi ngay bây giờ!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Bạn chưa cấp quyền chụp màn hình", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences("ad_closer_prefs", Context.MODE_PRIVATE)
        if (!prefs.contains("target_packages")) {
            prefs.edit().putStringSet("target_packages", setOf("com.outloud.idlefrog")).apply()
        }
        if (!prefs.contains("ai_detection_enabled")) {
            prefs.edit().putBoolean("ai_detection_enabled", true).apply()
        }

        binding.btnOpenAccessibilitySettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.btnLaunchGame.setOnClickListener {
            val launchIntent = packageManager.getLaunchIntentForPackage("com.outloud.idlefrog")
            if (launchIntent != null) {
                startActivity(launchIntent)
            } else {
                try {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/apps/details?id=com.outloud.idlefrog")
                        )
                    )
                } catch (_: Exception) {
                }
            }
        }

        binding.btnSelectApps.setOnClickListener {
            startActivity(Intent(this, AppSelectionActivity::class.java))
        }

        binding.switchApplyAll.isChecked = prefs.getBoolean("apply_all_apps", false)
        binding.switchApplyAll.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("apply_all_apps", isChecked).apply()
        }

        val savedKey = prefs.getString("gemini_api_key", "") ?: ""
        binding.etGeminiApiKey.setText(savedKey)

        binding.btnSaveApiKey.setOnClickListener {
            val key = binding.etGeminiApiKey.text.toString().trim()
            prefs.edit().putString("gemini_api_key", key).apply()
            Toast.makeText(this, "✅ Đã lưu Gemini API Key!", Toast.LENGTH_SHORT).show()
        }

        binding.switchAiDetection.isChecked = prefs.getBoolean("ai_detection_enabled", true)
        binding.switchAiDetection.setOnCheckedChangeListener { _, isChecked ->
            val key = binding.etGeminiApiKey.text.toString().trim()
            if (isChecked && key.isBlank()) {
                Toast.makeText(this, "Hãy nhập và lưu Gemini API Key trước!", Toast.LENGTH_SHORT).show()
                binding.switchAiDetection.isChecked = false
                return@setOnCheckedChangeListener
            }
            prefs.edit().putBoolean("ai_detection_enabled", isChecked).apply()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            binding.btnGrantScreenCapture.visibility = View.GONE
            binding.tvScreenCaptureHint.text = "✅ Máy bạn chạy Android 11+ nên AI tự động chụp màn hình trực tiếp qua dịch vụ Trợ năng (không cần cấp quyền quay màn hình)."
        } else {
            binding.btnGrantScreenCapture.setOnClickListener {
                val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                screenCaptureLauncher.launch(mgr.createScreenCaptureIntent())
            }
        }

        binding.switchBubble.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!Settings.canDrawOverlays(this)) {
                    pendingBubbleRequest = true
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } else {
                    startBubbleService()
                }
            } else {
                stopService(Intent(this, OverlayService::class.java))
            }
        }
    }

    private fun startBubbleService() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "🐸 Bong bóng điều khiển Auto đã hiển thị!", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        updateStatusText()
        if (pendingBubbleRequest) {
            pendingBubbleRequest = false
            if (Settings.canDrawOverlays(this)) {
                binding.switchBubble.isChecked = true
                startBubbleService()
            } else {
                binding.switchBubble.isChecked = false
            }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = "$packageName/${AdCloserAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':').any { it.equals(expectedComponentName, ignoreCase = true) }
    }

    private fun updateStatusText() {
        if (isAccessibilityServiceEnabled()) {
            binding.tvStatus.setText(R.string.status_service_on)
        } else {
            binding.tvStatus.setText(R.string.status_service_off)
        }
    }
}
