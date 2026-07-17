package com.signglove

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** 调 DeepSeek 把手势词序列组合成通顺中文句子 (同 PC server.py:deepseek_combine)。 */
object DeepSeek {
    private val client = OkHttpClient.Builder()
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    /** 同步调用 (放后台线程)。失败返回 null。 */
    fun combine(words: List<String>, key: String, model: String, url: String, prompt: String): String? {
        if (key.isBlank()) return null
        return try {
            val messages = JSONArray()
                .put(JSONObject().put("role", "system").put("content", prompt))
                .put(JSONObject().put("role", "user").put("content", "手势词序列: " + words.joinToString(" ")))
            val payload = JSONObject()
                .put("model", model)
                .put("messages", messages)
                .put("temperature", 0.3)
                .put("stream", false)
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $key")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                JSONObject(body).getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content").trim()
            }
        } catch (e: Exception) {
            null
        }
    }
}
