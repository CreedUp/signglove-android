package com.signglove

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * SOS 协调器: 异常 → 8s 可取消倒计时 → 持续响铃(AlarmService) + GPS定位 + 微信推送。
 * 倒计时/结果通过回调给 UI; 持续响铃由前台服务负责(手动停)。
 */
class Sos(
    private val ctx: Context,
    private val settings: Settings,
    private val onCountdown: (Int, String) -> Unit,   // (剩余秒, 原因); 秒=0 表示结束/隐藏
    private val onPushed: (Boolean, String) -> Unit    // (是否成功, 详情)
) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile var active = false; private set
    private var left = 0
    private var reason = ""
    private var vitals: VitalsData? = null

    private val tick = object : Runnable {
        override fun run() {
            left--
            if (left <= 0) { onCountdown(0, reason); fire() }
            else { onCountdown(left, reason); main.postDelayed(this, 1000) }
        }
    }

    /** 触发 8s 倒计时(防误报)。已在进行中则忽略。 */
    fun trigger(reason: String, vitals: VitalsData?) {
        if (active) return
        active = true
        this.reason = reason
        this.vitals = vitals
        left = 8
        onCountdown(left, reason)
        main.postDelayed(tick, 1000)
    }

    fun cancel() {
        main.removeCallbacks(tick)
        active = false
        onCountdown(0, reason)
    }

    fun fireNow() {
        main.removeCallbacks(tick)
        fire()
    }

    /** 倒计时结束/立即求助: 起持续响铃 + 取定位 + 推送。 */
    private fun fire() {
        active = false
        // 1) 持续响铃(前台服务, 手动停)
        ctx.startForegroundService(Intent(ctx, AlarmService::class.java)
            .putExtra(AlarmService.EXTRA_REASON, reason))
        // 2) 取定位后推送
        LocationHelper.current(ctx) { loc ->
            val mapLine = if (loc != null)
                "位置: ${"%.6f".format(loc.lat)},${"%.6f".format(loc.lon)}\n地图: ${LocationHelper.amapLink(loc)}"
            else "位置: 获取失败(请检查定位权限/GPS)"
            val text = buildText(mapLine)
            thread {
                val (ok, detail) = WeChatPush.send(text, settings.webhook, settings.serverchan)
                main.post { onPushed(ok, detail) }
            }
        }
    }

    private fun buildText(mapLine: String): String {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())
        val sb = StringBuilder()
        sb.append("【紧急求助】${settings.userName} 触发求助\n")
        sb.append("时间: $ts\n")
        sb.append("原因: $reason\n")
        vitals?.let {
            sb.append("心率: ${it.hr} BPM\n血氧: ${it.spo2} %\n体温: ${it.temp} ℃\n")
        }
        sb.append(mapLine)
        return sb.toString()
    }
}
