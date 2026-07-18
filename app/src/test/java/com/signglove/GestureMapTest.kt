package com.signglove

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureMapTest {
    private val firmwareVocabulary: Map<String, String> = linkedMapOf(
        "hello" to "你好",
        "thanks" to "谢谢",
        "bye" to "再见",
        "me" to "我",
        "you" to "你",
        "want" to "要",
        "water" to "喝水",
        "eat" to "吃饭",
        "toilet" to "厕所",
        "help" to "帮助",
        "pain" to "疼痛",
        "home" to "家",
        "sos" to "求救",
        "go" to "走",
        "come" to "过来",
        "stop" to "停",
        "sorry" to "对不起",
        "please" to "请",
        "you_all" to "你们",
        "why" to "为什么",
        "where" to "哪里",
        "how" to "怎么样",
        "teacher" to "老师",
        "go_to" to "去",
        "look" to "看",
        "judge" to "评委",
        "num_0" to "0",
        "num_1" to "1",
        "num_4" to "4",
        "num_7" to "7",
        "num_8" to "8",
        "num_9" to "9",
        "welcome" to "欢迎",
        "good_afternoon" to "下午好",
        "very" to "很",
        "think" to "想"
    )

    @Test
    fun `all 37-class firmware words map to chinese`() {
        assertTrue(GestureMap.isIdle("idle"))
        assertNull(GestureMap.word("idle"))
        firmwareVocabulary.forEach { (english, chinese) ->
            assertEquals("mapping for $english", chinese, GestureMap.word(english))
        }
    }

    @Test
    fun `labels are case insensitive and old firmware remains compatible`() {
        assertEquals("你好", GestureMap.word("HELLO"))
        assertEquals("我", GestureMap.word("FIST"))
        assertEquals("你好", GestureMap.word("open"))
        assertEquals("好的", GestureMap.word("OK"))
    }

    @Test
    fun `uppercase SOS triggers emergency recognition`() {
        val gesture = GestureMap.parseGesture("GESTURE:SOS")

        assertEquals("SOS", gesture)
        assertTrue(GestureMap.isSos(gesture!!))
        assertEquals("求救", GestureMap.word(gesture))
    }

    @Test
    fun `chinese labels pass through while unknown english is reported as unmapped`() {
        assertEquals("求救", GestureMap.word("求救"))
        assertEquals("吃饭", GestureMap.word("吃饭"))
        assertFalse(GestureMap.isIdle("unknown_label"))
        assertNull(GestureMap.word("unknown_label"))
    }
}
