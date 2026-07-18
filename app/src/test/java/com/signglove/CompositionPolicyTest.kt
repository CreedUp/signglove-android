package com.signglove

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositionPolicyTest {
    @Test
    fun `single mapped gesture bypasses DeepSeek`() {
        assertFalse(CompositionPolicy.shouldUseDeepSeek(listOf("下午好")))
        assertFalse(CompositionPolicy.shouldUseDeepSeek(listOf("9")))
    }

    @Test
    fun `multiple gestures can use DeepSeek for sentence composition`() {
        assertTrue(CompositionPolicy.shouldUseDeepSeek(listOf("我", "喝水")))
    }

    @Test
    fun `DeepSeek cannot invent a digit that was not recognized`() {
        assertFalse(CompositionPolicy.acceptsDeepSeekResult(listOf("下午好"), "9"))
        assertFalse(CompositionPolicy.acceptsDeepSeekResult(listOf("我", "下午好"), "我9"))
        assertTrue(CompositionPolicy.acceptsDeepSeekResult(listOf("数字", "9"), "数字9"))
        assertTrue(CompositionPolicy.acceptsDeepSeekResult(listOf("我", "喝水"), "我想喝水。"))
    }
}
