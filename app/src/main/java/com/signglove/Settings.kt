package com.signglove

import android.content.Context

/** 设置持久化 (SharedPreferences)。对应 PC 版 settings.json。 */
class Settings(ctx: Context) {
    private val sp = ctx.getSharedPreferences("signglove", Context.MODE_PRIVATE)

    var serverchan: String
        get() = sp.getString("sos_serverchan", "") ?: ""
        set(v) { sp.edit().putString("sos_serverchan", v).apply() }

    var webhook: String
        get() = sp.getString("sos_webhook", "") ?: ""
        set(v) { sp.edit().putString("sos_webhook", v).apply() }

    var userName: String
        get() = sp.getString("sos_name", "手语手套用户") ?: "手语手套用户"
        set(v) { sp.edit().putString("sos_name", v).apply() }

    var deepseekKey: String
        get() = sp.getString("deepseek_key", "") ?: ""
        set(v) { sp.edit().putString("deepseek_key", v).apply() }

    var deepseekModel: String
        get() = sp.getString("deepseek_model", "deepseek-chat") ?: "deepseek-chat"
        set(v) { sp.edit().putString("deepseek_model", v).apply() }

    var deepseekUrl: String
        get() = sp.getString("deepseek_url", "https://api.deepseek.com/chat/completions")
            ?: "https://api.deepseek.com/chat/completions"
        set(v) { sp.edit().putString("deepseek_url", v).apply() }

    var pauseSec: Float
        get() = sp.getFloat("pause_sec", 2.5f)
        set(v) { sp.edit().putFloat("pause_sec", v).apply() }

    var autoSos: Boolean
        get() = sp.getBoolean("auto_sos", true)
        set(v) { sp.edit().putBoolean("auto_sos", v).apply() }

    var demoText: String
        get() = sp.getString("demo_text", "") ?: ""
        set(v) { sp.edit().putString("demo_text", v).apply() }

    var demoButtonText: String
        get() = sp.getString("demo_button_text", "演示输出脚本") ?: "演示输出脚本"
        set(v) { sp.edit().putString("demo_button_text", v).apply() }

    var demoFirstDelaySec: Float
        get() = sp.getFloat("demo_first_delay_sec", 1.0f)
        set(v) { sp.edit().putFloat("demo_first_delay_sec", v).apply() }

    var demoIntervalSec: Float
        get() = sp.getFloat("demo_interval_sec", 2.0f)
        set(v) { sp.edit().putFloat("demo_interval_sec", v).apply() }

    var demoWaitForGesture: Boolean
        get() = sp.getBoolean("demo_wait_for_gesture", true)
        set(v) { sp.edit().putBoolean("demo_wait_for_gesture", v).apply() }
}
