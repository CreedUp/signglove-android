package com.signglove

import android.os.Handler
import android.os.Looper
import kotlin.random.Random

/** 一组生命体征读数。 */
data class VitalsData(val hr: Int, val spo2: Int, val temp: Double)

/**
 * 生命体征: 现为模拟数据(同 PC), 真实 MAX30102 接入后替换 feed()。
 * 内置异常检测: 危险阈值连续 dangerStreakNeed 拍命中 → onDanger(原因)。
 */
class Vitals(
    private val onUpdate: (VitalsData) -> Unit,
    private val onDanger: (String, VitalsData) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private var simRunning = false
    private var streak = 0
    @Volatile private var last: VitalsData? = null
    private val dangerStreakNeed = 3

    private val simTick = object : Runnable {
        override fun run() {
            val v = VitalsData(
                hr = 66 + Random.nextInt(14),
                spo2 = 96 + Random.nextInt(4),
                temp = (363 + Random.nextInt(8)) / 10.0
            )
            feed(v)
            main.postDelayed(this, 2200)
        }
    }

    fun startSim() {
        if (simRunning) return
        simRunning = true
        main.post(simTick)
    }

    fun stopSim() {
        simRunning = false
        main.removeCallbacks(simTick)
    }

    /** 最近一次有效读数，供手势 SOS 在立即告警时一并发送。 */
    fun latest(): VitalsData? = last

    /** 注入一组读数(模拟或真实), 更新 UI 并做异常检测。 */
    fun feed(v: VitalsData) {
        last = v
        main.post { onUpdate(v) }
        val bad = danger(v)
        if (bad != null) {
            streak++
            if (streak >= dangerStreakNeed) {
                streak = 0
                main.post { onDanger(bad, v) }
            }
        } else {
            streak = 0
        }
    }

    /** 一次性注入危险读数 3 拍(测试报警链路用)。 */
    fun injectDanger() {
        val d = VitalsData(hr = 138, spo2 = 86, temp = 39.1)
        repeat(dangerStreakNeed) { feed(d) }
    }

    /** 危险阈值(同 PC): 命中返回原因, 否则 null。 */
    private fun danger(v: VitalsData): String? {
        val bad = mutableListOf<String>()
        if (v.hr < 45 || v.hr > 130) bad.add("心率${v.hr}BPM")
        if (v.spo2 < 90) bad.add("血氧${v.spo2}%")
        if (v.temp >= 38.5 || v.temp < 35) bad.add("体温${v.temp}℃")
        return if (bad.isEmpty()) null else bad.joinToString("、")
    }
}
