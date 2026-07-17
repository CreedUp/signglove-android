package com.signglove

import java.util.Locale

/** 设备手势标签 → 中文手势词。null = 忽略（如 idle/静止）。 */
object GestureMap {
    private val map: Map<String, String?> = mapOf(
        "idle" to null,
        "静止" to null,
        "fist" to "我",
        "握拳" to "我",
        "拳头" to "我",
        "我" to "我",
        "open" to "你好",
        "张开" to "你好",
        "张手" to "你好",
        "你好" to "你好",
        "point" to "这个",
        "指向" to "这个",
        "这个" to "这个",
        "victory" to "需要",
        "胜利" to "需要",
        "剪刀手" to "需要",
        "需要" to "需要",
        "ok" to "好的",
        "确认" to "好的",
        "好的" to "好的",
        "help" to "帮助",
        "帮助" to "帮助",
        "sos" to "求救",
        "rescue" to "求救",
        "emergency" to "求救",
        "求救" to "求救",
    )

    /** 解析一行: "GESTURE:fist" → "fist"; 容错纯文本也当手势名。 */
    fun parseGesture(line: String): String? {
        val s = line.trim()
        if (s.isEmpty()) return null
        return if (s.startsWith("GESTURE:", ignoreCase = true)) s.substringAfter(":").trim() else s
    }

    /** SOS 标签大小写不敏感，中文“求救”也能直接识别。 */
    fun isSos(name: String): Boolean =
        normalize(name) in setOf("sos", "rescue", "emergency", "求救")

    /** 手势名 → 中文词。未知英文标签不再原样显示，中文词可直接透传。 */
    fun word(name: String): String? {
        val key = normalize(name)
        if (map.containsKey(key)) return map[key]
        return name.trim().takeIf { value -> value.any { it.code in 0x3400..0x9FFF } }
    }

    private fun normalize(name: String): String = name.trim().lowercase(Locale.ROOT)
}
