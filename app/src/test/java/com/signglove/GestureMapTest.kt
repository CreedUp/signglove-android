package com.signglove

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureMapTest {
    @Test
    fun `english gesture labels are converted to chinese`() {
        assertEquals("我", GestureMap.word("FIST"))
        assertEquals("你好", GestureMap.word("open"))
        assertEquals("帮助", GestureMap.word("HELP"))
    }

    @Test
    fun `uppercase SOS triggers emergency recognition`() {
        val gesture = GestureMap.parseGesture("GESTURE:SOS")

        assertEquals("SOS", gesture)
        assertTrue(GestureMap.isSos(gesture!!))
        assertEquals("求救", GestureMap.word(gesture))
    }

    @Test
    fun `chinese labels pass through while unknown english is ignored`() {
        assertEquals("求救", GestureMap.word("求救"))
        assertEquals("吃饭", GestureMap.word("吃饭"))
        assertNull(GestureMap.word("unknown_label"))
    }
}
