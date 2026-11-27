package com.glass.todo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetBackgroundConfigTest {

    @Test
    fun defaultBackgroundDoesNotRequireAnImage() {
        val config = WidgetBackgroundConfig()
        assertEquals(WidgetBackgroundType.NONE, config.type)
        assertEquals(0x00000000, config.color)
        assertFalse(config.hasColor)
        assertFalse(config.isImage)
    }

    @Test
    fun opaqueColorIsConsideredSet() {
        val config = WidgetBackgroundConfig(
            type = WidgetBackgroundType.COLOR,
            color = 0xFFFDFCF8.toInt()
        )
        assertTrue(config.hasColor)
    }

    @Test
    fun imageBackgroundRequiresASourceFile() {
        assertFalse(WidgetBackgroundConfig(type = WidgetBackgroundType.IMAGE).isImage)
        assertTrue(
            WidgetBackgroundConfig(
                type = WidgetBackgroundType.IMAGE,
                sourceFileName = "source_12.jpg"
            ).isImage
        )
    }

    @Test
    fun colorBackgroundCanKeepAnUnappliedImageForLaterEditing() {
        val config = WidgetBackgroundConfig(
            type = WidgetBackgroundType.COLOR,
            color = 0xFF123456.toInt(),
            sourceFileName = "source_12.jpg",
            crop = GlassCrop(0f, 0f, 1f, 1f)
        )
        assertEquals(WidgetBackgroundType.COLOR, config.type)
        assertEquals(0xFF123456.toInt(), config.color)
        assertFalse(config.isImage)
    }
}
