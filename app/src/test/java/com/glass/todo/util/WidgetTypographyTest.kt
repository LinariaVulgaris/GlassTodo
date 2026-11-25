package com.glass.todo.util

import com.glass.todo.data.WidgetLayoutMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun spaciousCountdownNumberScalesWithUserPreference() {
        val normal = WidgetTypography.countdownNumberSize("128", WidgetLayoutMode.WIDE, 400, 300, 100)
        val larger = WidgetTypography.countdownNumberSize("128", WidgetLayoutMode.WIDE, 400, 300, 140)
        assertTrue(larger > normal)
    }

    @Test
    fun longNumberInNarrowWidgetLeavesRoomForSideUnits() {
        val number = "12345"
        val size = WidgetTypography.countdownNumberSize(number, WidgetLayoutMode.TALL, 110, 300, 100)
        val estimatedWidth = size * number.length * 0.62f
        assertTrue(size > 0f)
        assertTrue(estimatedWidth <= 41.01f)
    }

    @Test
    fun maximumScaleNeverExceedsCountdownHardFitLimitWithSideUnits() {
        val size = WidgetTypography.countdownNumberSize("12345", WidgetLayoutMode.TALL, 110, 300, 140)
        val estimatedWidth = size * 5 * 0.62f
        assertTrue(estimatedWidth <= 25.41f)
        assertTrue(size <= 62f * 1.4f)
    }

    @Test
    fun invalidDimensionsStillProduceUsableTextSize() {
        val size = WidgetTypography.countdownNumberSize("128", WidgetLayoutMode.AUTO, 0, 0, 100)
        assertTrue(size >= 8f)
        assertTrue(size <= 62f * 1.4f)
    }
}
