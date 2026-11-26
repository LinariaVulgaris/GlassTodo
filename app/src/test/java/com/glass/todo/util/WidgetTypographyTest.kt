package com.glass.todo.util

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetTypographyTest {

    @Test
    fun defaultScaleKeepsOriginalDesignSizes() {
        assertEquals(1f, WidgetTypography.scale(100), 0.0001f)
        assertEquals(14f, WidgetTypography.scaledSize(14f, 100), 0.0001f)
    }

    @Test
    fun scaleIsClampedToSupportedRange() {
        assertEquals(0.8f, WidgetTypography.scale(-100), 0.0001f)
        assertEquals(1.4f, WidgetTypography.scale(1000), 0.0001f)
        assertEquals(11.2f, WidgetTypography.scaledSize(14f, 80), 0.0001f)
        assertEquals(19.6f, WidgetTypography.scaledSize(14f, 140), 0.0001f)
    }
}
