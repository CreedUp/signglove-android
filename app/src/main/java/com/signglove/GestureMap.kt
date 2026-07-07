package com.signglove

/** 手势名 → 显示词 映射 (同 PC 版 gesture_map.py)。null = 忽略(如 idle 静止)。 */
object GestureMap {
    val map: Map<String, String?> = mapOf(
        "idle" to null,
        "fist" to "我",
        "open" to "你好",
        "point" to "这个",
        "victory" to "需要",
        "ok" to "好的",
        "help" to "帮助",
        "sos" to "求救",
        "rescue" to "求救",
        "emergency" to "求救",
    )

    /** 解析一行: "GESTURE:fist" → "fist"; 容错纯文本也当手势名。 */
    fun parseGesture(line: String): String? {
        val s = line.trim()
        if (s.isEmpty()) return null
        return if (s.uppercase().startsWith("GESTURE:")) s.substringAfter(":").trim() else s
    }

    /** 手势名 → 词。不在表里则原样返回(容错); 表里为 null 则返回 null(忽略)。 */
    fun word(name: String): String? = if (map.containsKey(name)) map[name] else name
}
