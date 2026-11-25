package com.glass.todo.util

import com.glass.todo.data.GlassCrop
import com.glass.todo.data.PixelCrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassRenderGeometryTest {

    @Test
    fun largeScreenshotIsCappedBelowRemoteViewsBudget() {
        val size = GlassRenderGeometry.outputSize(PixelCrop(0, 0, 3000, 2000))
        assertTrue(size.pixels <= GlassRenderGeometry.MAX_OUTPUT_PIXELS)
        assertTrue(size.width <= GlassRenderGeometry.MAX_OUTPUT_SIDE)
        assertTrue(size.height <= GlassRenderGeometry.MAX_OUTPUT_SIDE)
        assertTrue(size.width > 0 && size.height > 0)
    }

    @Test
    fun smallCropIsNotUnnecessarilyEnlarged() {
        val size = GlassRenderGeometry.outputSize(PixelCrop(10, 20, 110, 70))
        assertEquals(100, size.width)
        assertEquals(50, size.height)
    }

    @Test
    fun extremeAspectRatioNeverProducesZeroDimensions() {
        val size = GlassRenderGeometry.outputSize(PixelCrop(0, 0, 4000, 10), maxPixels = 25)
        assertTrue(size.width > 0)
        assertTrue(size.height > 0)
        assertTrue(size.pixels <= 25)
    }

    @Test
    fun roundedRectangleDistanceIsNegativeInsideAndPositiveOutside() {
        val center = GlassRenderGeometry.roundedRectDistance(50f, 50f, 100f, 100f, 14f)
        val outside = GlassRenderGeometry.roundedRectDistance(-2f, 50f, 100f, 100f, 14f)
        assertTrue(center < 0f)
        assertTrue(outside > 0f)
    }

    @Test
    fun invalidSavedCropIsClampedToAUsableRectangle() {
        val crop = GlassCrop(-0.5f, 1.5f, 0.01f, 0.01f).normalized()
        assertTrue(crop.isValid)
        assertTrue(crop.left >= 0f && crop.top >= 0f)
        assertFalse(crop.right > 1f || crop.bottom > 1f)
    }
}
