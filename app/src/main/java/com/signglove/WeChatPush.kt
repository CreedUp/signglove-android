package com.signglove

import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** 微信推送: 企业微信群机器人 / Server酱 (同 PC server.py:sos_push)。 */
object WeChatPush {
    private val client = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    /** 同步发送(放后台线程)。返回 (是否至少一个成功, 详情)。 */
    fun send(text: String, webhook: String, serverchan: String): Pair<Boolean, String> {
        if (webhook.isBlank() && serverchan.isBlank())
            return false to "未配置微信推送(设置页填 Server酱 SendKey 或 企业微信 Webhook)"
        var ok = false
        val detail = StringBuilder()
        if (webhook.isNotBlank()) {
            try {
                val body = JSONObject()
                    .put("msgtype", "text")
                    .put("text", JSONObject().put("content", text))
                val req = Request.Builder().url(webhook)
                    .post(body.toString().toRequestBody("application/json".toMediaType())).build()
                client.newCall(req).execute().use { r ->
                    val j = JSONObject(r.body?.string() ?: "{}")
                    if (r.isSuccessful && j.optInt("errcode", 0) == 0) { ok = true; detail.append("企业微信 已发送; ") }
                    else detail.append("企业微信失败:$j; ")
                }
            } catch (e: Exception) { detail.append("企业微信异常:${e.message}; ") }
        }
        if (serverchan.isNotBlank()) {
            try {
                val form = FormBody.Builder().add("title", "【紧急求助】").add("desp", text).build()
                val req = Request.Builder().url("https://sctapi.ftqq.com/$serverchan.send").post(form).build()
                client.newCall(req).execute().use { r ->
                    val j = JSONObject(r.body?.string() ?: "{}")
                    if (r.isSuccessful && j.optInt("code", 0) == 0) { ok = true; detail.append("Server酱 已发送; ") }
                    else detail.append("Server酱失败:$j; ")
                }
            } catch (e: Exception) { detail.append("Server酱异常:${e.message}; ") }
        }
        return ok to detail.toString()
    }
}
