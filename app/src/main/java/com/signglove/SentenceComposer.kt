package com.signglove

import android.os.Handler
import android.os.Looper
import kotlin.concurrent.thread

/**
 * 停顿断句 + 组句。收到手势词进缓冲, 停顿超过 pauseSec 触发组句:
 * 有 DeepSeek key → 调云端; 否则直拼。结果回主线程。
 */
class SentenceComposer(
    private val settings: Settings,
    private val onWord: (String) -> Unit,          // 每个手势词(累积小字)
    private val onSentence: (String, String) -> Unit  // (句子, 来源: deepseek/local)
) {
    private val main = Handler(Looper.getMainLooper())
    private val buffer = mutableListOf<String>()
    private var pending: Runnable? = null

    /** 收到一个手势词。 */
    fun feed(word: String) {
        buffer.add(word)
        main.post { onWord(word) }
        pending?.let { main.removeCallbacks(it) }
        val r = Runnable { fire() }
        pending = r
        main.postDelayed(r, (settings.pauseSec * 1000).toLong())
    }

    private fun fire() {
        pending = null
        if (buffer.isEmpty()) return
        val words = buffer.toList()
        buffer.clear()
        val key = settings.deepseekKey
        if (key.isBlank()) {
            main.post { onSentence(words.joinToString(" "), "local") }
            return
        }
        thread {
            val s = DeepSeek.combine(words, key, settings.deepseekModel, settings.deepseekUrl)
            val text = s ?: words.joinToString(" ")
            val src = if (s != null) "deepseek" else "fallback"
            main.post { onSentence(text, src) }
        }
    }
}
