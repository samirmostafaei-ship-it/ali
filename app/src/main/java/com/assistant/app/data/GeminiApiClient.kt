package com.assistant.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

enum class ContentType(val titleFa: String, val promptInstruction: String) {
    ARTICLE(
        "مقاله علمی و تخصصی",
        "شما یک استاد و پژوهشگر ارشد هستید. یک مقاله تخصصی، علمی و ساختاریافته کامل در موضوع درخواستی به زبان فارسی بنویسید شامل: عنوان، چکیده، مقدمه، مبانی نظری و فنی، استانداردهای مرتبط (مانند استانداردهای IEC یا مقررات ملی در صورت ارتباط)، تحلیل و نتیجه‌گیری."
    ),
    LECTURE_NOTES(
        "جزوه درسی و آموزشی",
        "شما یک مدرس برجسته دانشگاهی و دوره‌های نظام مهندسی هستید. یک جزوه آموزشی گام‌به‌گام و بسیار دقیق و کاربردی بنویسید شامل: سرفصل‌ها، مفاهیم کلیدی، روابط و فرمول‌ها یا دیاگرام‌های منطقی، نکات اجرایی و نظارتی، و چند مثال کاربردی یا پرسش و پاسخ چهارگزینه‌ای."
    ),
    BOOKLET(
        "کتابچه تخصصی و هندبوک",
        "شما مؤلف کتاب‌های مرجع مهندسی هستید. یک کتابچه و راهنمای جامع (Handbook) چندبخشی تدوین کنید شامل: فهرست مطالب، مقدمه، فصول مستقل با جزئیات کامل، چک‌لیست‌های تحویل و نظارت کارگاهی، راهنمای عیب‌یابی و مراجع فنی."
    )
}

class GeminiApiClient(
    private val apiKey: String,
    private val customBaseUrl: String? = null,
    private val proxyHost: String? = null,
    private val proxyPort: Int? = null
) {

    private val client: OkHttpClient = run {
        val builder = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)

        if (!proxyHost.isNullOrBlank() && proxyPort != null && proxyPort > 0) {
            try {
                val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyHost.trim(), proxyPort))
                builder.proxy(proxy)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        builder.build()
    }

    suspend fun generateContent(topic: String, type: ContentType): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank()) {
                return@withContext Result.failure(Exception("لطفاً ابتدا کلید API جمینای خود را در تنظیمات وارد کنید."))
            }

            // تعیین آدرس سرور (دسترسی مستقیم یا از طریق سرور واسط / Reverse Proxy)
            val baseHost = if (!customBaseUrl.isNullOrBlank()) {
                customBaseUrl.trim().removeSuffix("/")
            } else {
                "https://generativelanguage.googleapis.com"
            }

            val url = "$baseHost/v1beta/models/gemini-3.6-flash:generateContent?key=$apiKey"

            val systemInstruction = type.promptInstruction
            val userPrompt = "موضوع درخواستی: $topic\n\nلطفاً طبق دستورالعمل، خروجی کامل و تخصصی با قالب‌بندی مرتب Markdown ارائه دهید."

            val jsonBody = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", "$systemInstruction\n\n$userPrompt"))
                        })
                    })
                }
                put("contents", contentsArray)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.4)
                    put("maxOutputTokens", 8192)
                })
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("خطا در ارتباط با سرور (کد ${response.code}): $responseBody"))
            }

            val jsonResp = JSONObject(responseBody)
            val candidates = jsonResp.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return@withContext Result.failure(Exception("پاسخی از مدل دریافت نشد."))
            }

            val content = candidates.getJSONObject(0).getJSONObject("content")
            val parts = content.getJSONArray("parts")
            val resultText = StringBuilder()
            for (i in 0 until parts.length()) {
                resultText.append(parts.getJSONObject(i).optString("text", ""))
            }

            Result.success(resultText.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
