package com.signglove

/** 决定手势词是否需要交给云端连词成句。 */
object CompositionPolicy {
    /** 单个词必须原样显示；只有两个及以上词才需要云端组句。 */
    fun shouldUseDeepSeek(words: List<String>): Boolean = words.size >= 2

    /** 拒绝云端凭空生成输入里不存在的数字，防止“下午好”再次被改写为“9”。 */
    fun acceptsDeepSeekResult(words: List<String>, result: String): Boolean {
        if (result.isBlank()) return false
        val inputDigits = words.joinToString("").filter(Char::isDigit).toSet()
        val outputDigits = result.filter(Char::isDigit).toSet()
        return outputDigits.all { it in inputDigits }
    }
}
