package com.signglove

import android.content.Context

/** 设置持久化 (SharedPreferences)。对应 PC 版 settings.json。 */
class Settings(ctx: Context) {
    companion object {
        const val DEFAULT_DEEPSEEK_PROMPT =
            "你是专业的手语翻译助手。用户会按识别顺序输入一串以空格分隔的中文手势词。" +
            "请结合手语语序，将这些词连成一句自然、通顺、口语化的中文句子；可以调整语序、补充必要虚词并添加恰当标点，" +
            "但不得改变原意、遗漏关键信息或凭空添加事实。若包含“求救”或“SOS”等紧急信息，必须保留其紧急含义。" +
            "只输出最终中文句子，不要解释、引号、前缀或其他内容。"
    }

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

    var deepseekEnabled: Boolean
        get() = sp.getBoolean("deepseek_enabled", true)
        set(v) { sp.edit().putBoolean("deepseek_enabled", v).apply() }

    var deepseekPrompt: String
        get() = sp.getString("deepseek_prompt", DEFAULT_DEEPSEEK_PROMPT)
            ?.ifBlank { DEFAULT_DEEPSEEK_PROMPT } ?: DEFAULT_DEEPSEEK_PROMPT
        set(v) { sp.edit().putString("deepseek_prompt", v.ifBlank { DEFAULT_DEEPSEEK_PROMPT }).apply() }

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
}
