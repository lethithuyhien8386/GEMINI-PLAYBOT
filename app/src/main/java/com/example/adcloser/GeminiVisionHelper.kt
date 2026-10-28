package com.example.adcloser

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.Display
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class GeminiVisionHelper(private val service: AccessibilityService) {

    companion object {
        private const val TAG = "FrogsAutoAI"
    }

    data class GameAction(
        val type: String, // "TAP", "HOLD", "SWIPE"
        val x: Float,
        val y: Float,
        val endX: Float = x,
        val endY: Float = y,
        val durationMs: Long = 140L,
        val reason: String = ""
    )

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val stepHistory = ArrayDeque<String>()

    private val promptText = """
Bạn là AI chuyên gia chơi game "Frogs Kitchen" (Frog Kitchen Tycoon: Idle Venture - package: com.outloud.idlefrog) tự động 100% từ lúc mới mở game, qua các màn và leo lên level/thế giới cao hơn.
Hãy quan sát kỹ ảnh chụp màn hình hiện tại và quyết định chuỗi thao tác tối ưu nhất để game tiến triển nhanh nhất.

QUY TẮC ƯU TIÊN CHIẾN THUẬT (theo thứ tự từ trên xuống):
1. MÀN HÌNH BẮT ĐẦU / LOADING / HƯỚNG DẪN (TUTORIAL):
   - Nếu thấy nút "Play", "Start", "Tap to Start", "Continue", "Accept", "OK", "Got it", "Claim", hoặc bàn tay/mũi tên chỉ dẫn của game -> Bấm (TAP) ngay vào vị trí được chỉ dẫn hoặc nút đó.
2. ĐÓNG QUẢNG CÁO / POPUP CỬA HÀNG / OFFLINE REWARD:
   - Nếu đang ở màn hình quảng cáo xen kẽ -> Tìm nút "X", "Close", "Skip", "Bỏ qua" ở các góc và TAP vào đó.
   - Nếu có bảng thưởng Offline / Daily Reward / Free Chest / Túi quà (Bag) -> TAP vào nút nhận miễn phí ("Claim", "Collect", "Open", "Skip animation" hoặc vùng trống để mở túi) hoặc nếu bắt xem quảng cáo/mua bằng tiền thật thì TAP nút "X" / "No thanks" để đóng popup và tiếp tục chơi.
3. CHUYỂN MÀN / LÊN LEVEL / NÂNG CẤP NHÀ HÀNG MỚI (QUAN TRỌNG NHẤT ĐỂ QUA MÀN):
   - Kiểm tra biểu tượng Tiến độ Màn / Nâng cấp Nhà hàng / Qua Thế giới mới (thường ở góc trên bên trái, góc dưới, hoặc nút có chữ "Next Level", "Renovate", "Expand", "New Kitchen", "Fly / Travel / Next Stage" khi các trạm đã đạt max level). Nếu nút này sáng/khả dụng -> TAP vào đó và TAP nút xác nhận trong bảng hiện ra để QUA MÀN MỚI!
4. MỞ TRẠM NẤU ĂN MỚI & MỞ HỘP GỖ (UNLOCK STATIONS & UNBOX):
   - Trên sàn bếp, nếu thấy nút mở khóa trạm nấu ăn mới (biểu tượng đồng tiền vàng + giá tiền có thể mua được) -> TAP vào để mua trạm mới.
   - Nếu thấy thùng/hộp gỗ hoặc thiết bị mới vừa mua đang đóng gói trên sàn bếp -> TAP thẳng vào thùng đó để mở khóa trạm nấu ăn hoặc đầu bếp ếch!
5. NÂNG CẤP TRẠM NẤU ĂN (STATION UPGRADES - LEO LEVEL):
   - Các trạm nấu ăn (bàn chế biến có đầu bếp ếch đứng nấu) có thanh level và mũi tên xanh lá cây hướng lên (hoặc biểu tượng có chấm đỏ/xanh báo đủ tiền nâng cấp):
     + Nếu bảng nâng cấp của trạm ĐANG MỞ (có nút lớn ghi giá vàng để nâng cấp level trạm) -> Dùng lệnh "HOLD" (giữ 1200ms) vào chính giữa nút nâng cấp đó để tăng hàng chục level liên tục!
     + Nếu chưa mở bảng nâng cấp trạm -> TAP vào trạm nấu ăn (ưu tiên trạm có mũi tên xanh báo đủ tiền nâng cấp) để mở bảng nâng cấp của nó.
6. NÂNG CẤP CHUNG & THUÊ ĐẦU BẾP ẾCH (GLOBAL UPGRADES / CHEFS / CUSTOMERS):
   - Nhìn góc dưới bên phải (biểu tượng mũi tên lên / cuốn sách / đầu bếp có chấm đỏ hoặc dấu chấm than):
     + Nếu bảng Upgrades đang mở -> TAP liên tục vào các nút mua màu xanh/vàng sáng ở đầu danh sách (Hire Frog Chef, Faster Cooking, More Customers, Dish Profit x2...) rồi đóng bảng lại nếu hết tiền.
     + Nếu chưa mở mà thấy chấm đỏ ở nút Upgrade góc dưới phải -> TAP vào nút đó để mở và mua nâng cấp.
7. THU TIỀN TIP & KHÁCH HÀNG ĐẶC BIỆT (TIPS / SNACK FEVER / VIP):
   - Nếu trên bàn hoặc quầy có tiền vàng (Tips) rơi ra, hộp quà bay, hoặc nút kích hoạt "Snack Fever" -> TAP vào để thu hoạch.
8. DI CHUYỂN CAMERA (SWIPE) KHI CẦN:
   - Nếu các trạm hiện tại trên màn hình đều đã kịch trần hoặc cần tìm trạm mới ở phía dưới/trên nhà hàng -> Dùng lệnh "SWIPE" vuốt dọc màn hình để khám phá toàn bộ nhà bếp.

ĐỊNH DẠNG TRẢ LỜI BẮT BUỘC:
CHỈ trả lời duy nhất 1 chuỗi JSON hợp lệ (không bọc giải thích dài dòng), theo cấu trúc:
{
  "state": "<mô tả ngắn gọn màn hình hiện tại dưới 12 từ>",
  "actions": [
    {
      "type": "TAP",
      "x": <số từ 0 đến 1000>,
      "y": <số từ 0 đến 1000>,
      "duration": 150,
      "reason": "<lý do ngắn gọn>"
    }
  ]
}
Lưu ý về tọa độ và kiểu thao tác:
- Hệ tọa độ chuẩn hóa từ 0 đến 1000: x=0 là mép trái, x=1000 là mép phải; y=0 là mép trên cùng, y=1000 là mép dưới cùng.
- "type" nhận 1 trong 3 giá trị:
  + "TAP": chạm 1 lần vào (x, y).
  + "HOLD": nhấn giữ tại (x, y) với "duration" từ 1000 đến 1500 (rất hiệu quả khi giữ nút nâng cấp level trạm nấu ăn trong Frogs Kitchen).
  + "SWIPE": vuốt từ (x, y) tới ("endX", "endY") với "duration" khoảng 400.
- Bạn có thể trả về từ 1 đến 3 action trong mảng "actions" nếu các nút hiển thị rõ ràng trên cùng một màn hình (ví dụ: thu tiền tip + bấm nâng cấp).
""".trimIndent()

    fun hasCaptureMethod(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R || ScreenCaptureService.isRunning
    }

    fun analyzeAndPlayGame(apiKey: String, onDone: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            captureViaTakeScreenshot(apiKey, onDone)
        } else {
            captureViaMediaProjection(apiKey, onDone)
        }
    }

    private fun captureViaTakeScreenshot(apiKey: String, callback: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            callback(false)
            return
        }
        try {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        val hardwareBuffer = screenshot.hardwareBuffer
                        val colorSpace = screenshot.colorSpace
                        val hardwareBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                        hardwareBuffer.close()

                        if (hardwareBitmap == null) {
                            callback(false)
                            return
                        }
                        val bitmap = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
                        hardwareBitmap.recycle()

                        val screenWidth = bitmap.width
                        val screenHeight = bitmap.height

                        executor.execute {
                            val actions = callGeminiApi(bitmap, apiKey)
                            bitmap.recycle()
                            mainHandler.post {
                                if (!actions.isNullOrEmpty()) {
                                    executeActionSequence(actions, 0, screenWidth, screenHeight, callback)
                                } else {
                                    callback(false)
                                }
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.w(TAG, "takeScreenshot thất bại (code=$errorCode), thử fallback MediaProjection")
                        if (ScreenCaptureService.isRunning) {
                            captureViaMediaProjection(apiKey, callback)
                        } else {
                            callback(false)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi gọi takeScreenshot: ${e.message}")
            callback(false)
        }
    }

    private fun captureViaMediaProjection(apiKey: String, callback: (Boolean) -> Unit) {
        val latest = ScreenCaptureService.latestBitmap
        if (latest == null) {
            showToast("⚠️ Chưa có ảnh màn hình - hãy cấp quyền chụp màn hình")
            callback(false)
            return
        }
        val bitmap = latest.copy(Bitmap.Config.ARGB_8888, false)
        val screenWidth = bitmap.width
        val screenHeight = bitmap.height

        executor.execute {
            val actions = callGeminiApi(bitmap, apiKey)
            bitmap.recycle()
            mainHandler.post {
                if (!actions.isNullOrEmpty()) {
                    executeActionSequence(actions, 0, screenWidth, screenHeight, callback)
                } else {
                    callback(false)
                }
            }
        }
    }

    private fun callGeminiApi(bitmap: Bitmap, apiKey: String): List<GameAction>? {
        try {
            // Scale bitmap down slightly for faster upload and lower latency while preserving text clarity
            val maxDim = 1024
            val scale = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                maxDim.toFloat() / maxOf(bitmap.width, bitmap.height).toFloat()
            } else 1.0f
            val sendBitmap = if (scale < 1.0f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else bitmap

            val stream = ByteArrayOutputStream()
            sendBitmap.compress(Bitmap.CompressFormat.JPEG, 78, stream)
            if (sendBitmap != bitmap) sendBitmap.recycle()

            val base64Image = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)

            val historyNote = synchronized(stepHistory) {
                if (stepHistory.isEmpty()) ""
                else "\nLịch sử các bước vừa thực hiện gần đây (tránh lặp lại vô ích nếu màn hình không đổi): " +
                        stepHistory.joinToString(" -> ")
            }

            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", promptText + historyNote)
                            })
                            put(JSONObject().apply {
                                put("inline_data", JSONObject().apply {
                                    put("mime_type", "image/jpeg")
                                    put("data", base64Image)
                                })
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.2)
                })
            }

            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent?key=${apiKey.trim()}")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 15000
                readTimeout = 20000
                doOutput = true
            }

            conn.outputStream.use { it.write(requestJson.toString().toByteArray(Charsets.UTF_8)) }

            val responseCode = conn.responseCode
            val bodyStream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = bodyStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            conn.disconnect()

            if (responseCode !in 200..299) {
                Log.e(TAG, "Gemini API lỗi $responseCode: $responseText")
                showToast("Gemini API lỗi $responseCode")
                return null
            }

            val responseJson = JSONObject(responseText)
            val candidates = responseJson.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null

            val rawText = candidates.getJSONObject(0)
                .optJSONObject("content")
                ?.optJSONArray("parts")
                ?.getJSONObject(0)
                ?.optString("text", "")
                ?.trim() ?: return null

            val cleanJsonStr = extractJsonObject(rawText) ?: return null
            val resultJson = JSONObject(cleanJsonStr)

            val stateDesc = resultJson.optString("state", "")
            val resultList = mutableListOf<GameAction>()

            val actionsArray = resultJson.optJSONArray("actions")
            if (actionsArray != null && actionsArray.length() > 0) {
                for (i in 0 until actionsArray.length()) {
                    val obj = actionsArray.optJSONObject(i) ?: continue
                    val type = obj.optString("type", "TAP").uppercase()
                    val normX = obj.optDouble("x", -1.0).toFloat()
                    val normY = obj.optDouble("y", -1.0).toFloat()
                    val endX = obj.optDouble("endX", normX.toDouble()).toFloat()
                    val endY = obj.optDouble("endY", normY.toDouble()).toFloat()
                    val defaultDur = when (type) {
                        "HOLD" -> 1250L
                        "SWIPE" -> 420L
                        else -> 130L
                    }
                    val duration = obj.optLong("duration", defaultDur).coerceIn(60L, 2500L)
                    val reason = obj.optString("reason", stateDesc)

                    if (normX in 0f..1000f && normY in 0f..1000f) {
                        resultList.add(
                            GameAction(
                                type = type,
                                x = (normX / 1000f).coerceIn(0.01f, 0.99f),
                                y = (normY / 1000f).coerceIn(0.01f, 0.99f),
                                endX = (endX / 1000f).coerceIn(0.01f, 0.99f),
                                endY = (endY / 1000f).coerceIn(0.01f, 0.99f),
                                durationMs = duration,
                                reason = reason
                            )
                        )
                    }
                }
            } else if (resultJson.optBoolean("found", false)) {
                // Backward compatibility single point
                val normX = resultJson.optDouble("x", -1.0).toFloat()
                val normY = resultJson.optDouble("y", -1.0).toFloat()
                if (normX in 0f..1000f && normY in 0f..1000f) {
                    resultList.add(
                        GameAction(
                            type = "TAP",
                            x = (normX / 1000f).coerceIn(0.01f, 0.99f),
                            y = (normY / 1000f).coerceIn(0.01f, 0.99f),
                            reason = "Tương tác"
                        )
                    )
                }
            }

            if (resultList.isNotEmpty()) {
                val first = resultList.first()
                synchronized(stepHistory) {
                    if (stepHistory.size >= 6) stepHistory.removeFirst()
                    stepHistory.addLast("${first.type}@(${(first.x * 1000).toInt()},${(first.y * 1000).toInt()}):${first.reason.take(25)}")
                }
                showToast("🐸 AI: ${first.reason.ifBlank { stateDesc }}")
            }

            return resultList
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi gọi Gemini: ${e.message}")
            return null
        }
    }

    private fun extractJsonObject(raw: String): String? {
        val cleaned = raw
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val firstBrace = cleaned.indexOf('{')
        val lastBrace = cleaned.lastIndexOf('}')
        return if (firstBrace != -1 && lastBrace > firstBrace) {
            cleaned.substring(firstBrace, lastBrace + 1)
        } else null
    }

    private fun executeActionSequence(
        actions: List<GameAction>,
        index: Int,
        screenWidth: Int,
        screenHeight: Int,
        onComplete: (Boolean) -> Unit
    ) {
        if (index >= actions.size) {
            onComplete(true)
            return
        }
        val act = actions[index]
        val startX = (act.x * screenWidth).coerceIn(5f, screenWidth - 5f)
        val startY = (act.y * screenHeight).coerceIn(5f, screenHeight - 5f)
        val targetX = (act.endX * screenWidth).coerceIn(5f, screenWidth - 5f)
        val targetY = (act.endY * screenHeight).coerceIn(5f, screenHeight - 5f)

        val path = Path().apply {
            moveTo(startX, startY)
            if (act.type == "SWIPE") {
                lineTo(targetX, targetY)
            } else {
                lineTo(startX + 1f, startY + 1f)
            }
        }

        val duration = when (act.type) {
            "HOLD" -> act.durationMs.coerceIn(900L, 1800L)
            "SWIPE" -> act.durationMs.coerceIn(250L, 800L)
            else -> act.durationMs.coerceIn(80L, 250L)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0L, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                mainHandler.postDelayed({
                    executeActionSequence(actions, index + 1, screenWidth, screenHeight, onComplete)
                }, 350L)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                onComplete(index > 0)
            }
        }, null)
    }

    private fun showToast(msg: String) {
        mainHandler.post {
            Toast.makeText(service, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
