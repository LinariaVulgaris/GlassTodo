package com.glass.todo.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassCropGeometryTest {

    private val image = GlassCropGeometry.Bounds(0f, 0f, 400f, 800f)

    @Test
    fun thinCropKeepsItsCenterAvailableForMoving() {
        val crop = GlassCropGeometry.Bounds(50f, 100f, 350f, 140f)
        assertEquals(
            GlassCropGeometry.TouchMode.MOVE,
            GlassCropGeometry.touchMode(200f, 120f, crop, 100f)
        )
        assertEquals(
            GlassCropGeometry.TouchMode.TOP_LEFT,
            GlassCropGeometry.touchMode(50f, 100f, crop, 100f)
        )
    }

    @Test
    fun touchOutsideCropDoesNotStartAGesture() {
        val crop = GlassCropGeometry.Bounds(50f, 100f, 350f, 300f)
        assertEquals(
            GlassCropGeometry.TouchMode.NONE,
            GlassCropGeometry.touchMode(20f, 90f, crop, 28f)
        )
    }

    @Test
    fun moveUsesTotalDeltaAndStaysInsideImage() {
        val crop = GlassCropGeometry.Bounds(50f, 100f, 250f, 300f)
        val moved = GlassCropGeometry.move(crop, image, 500f, -200f)
        assertEquals(200f, moved.left, 0.001f)
        assertEquals(0f, moved.top, 0.001f)
        assertEquals(400f, moved.right, 0.001f)
        assertEquals(200f, moved.bottom, 0.001f)
    }

    @Test
    fun freeResizeKeepsOppositeCornerAndMinimumSize() {
        val crop = GlassCropGeometry.Bounds(50f, 100f, 250f, 300f)
        val resized = GlassCropGeometry.resize(
            crop, image, GlassCropGeometry.TouchMode.BOTTOM_RIGHT,
            -500f, -500f, 1f, false, 40f
        )
        assertEquals(50f, resized.left, 0.001f)
        assertEquals(100f, resized.top, 0.001f)
        assertEquals(90f, resized.right, 0.001f)
        assertEquals(140f, resized.bottom, 0.001f)
    }

    @Test
    fun lockedResizePreservesAspectAndAnchor() {
        val crop = GlassCropGeometry.Bounds(50f, 100f, 250f, 300f)
        val resized = GlassCropGeometry.resize(
            crop, image, GlassCropGeometry.TouchMode.TOP_LEFT,
            40f, 40f, 2f, true, 40f
        )
        assertEquals(250f, resized.right, 0.001f)
        assertEquals(300f, resized.bottom, 0.001f)
        assertEquals(2f, resized.width / resized.height, 0.001f)
        assertTrue(resized.left >= image.left && resized.top >= image.top)
    }

    @Test
    fun lockedResizeCannotEscapeImageBounds() {
        val crop = GlassCropGeometry.Bounds(50f, 100f, 250f, 300f)
        val resized = GlassCropGeometry.resize(
            crop, image, GlassCropGeometry.TouchMode.BOTTOM_RIGHT,
            1000f, 1000f, 2f, true, 40f
        )
        assertTrue(resized.left >= image.left && resized.top >= image.top)
        assertTrue(resized.right <= image.right && resized.bottom <= image.bottom)
        assertEquals(2f, resized.width / resized.height, 0.001f)
    }

    @Test
    fun defaultCropUsesRequestedAspectAndFitsImage() {
        val crop = GlassCropGeometry.defaultCrop(image, 0.5f)
        assertEquals(0.5f, crop.width / crop.height, 0.001f)
        assertTrue(crop.left >= image.left && crop.top >= image.top)
        assertTrue(crop.right <= image.right && crop.bottom <= image.bottom)
        assertEquals(200f, crop.left + crop.width / 2f, 0.001f)
        assertEquals(400f, crop.top + crop.height / 2f, 0.001f)
    }

    @Test
    fun adjustingAspectShrinksAroundCenterWithoutLeavingImage() {
        val crop = GlassCropGeometry.Bounds(20f, 20f, 380f, 780f)
        val adjusted = GlassCropGeometry.adjustAspect(crop, image, 2f)
        assertEquals(2f, adjusted.width / adjusted.height, 0.001f)
        assertTrue(adjusted.left >= image.left && adjusted.top >= image.top)
        assertTrue(adjusted.right <= image.right && adjusted.bottom <= image.bottom)
    }

    @Test
    fun lockedResizeCanGrowWhilePreservingAspect() {
        val crop = GlassCropGeometry.Bounds(50f, 100f, 150f, 300f)
        val resized = GlassCropGeometry.resize(
            crop, image, GlassCropGeometry.TouchMode.BOTTOM_RIGHT,
            200f, 400f, 0.5f, true, 40f
        )
        assertTrue(resized.width > crop.width)
        assertTrue(resized.height > crop.height)
        assertEquals(0.5f, resized.width / resized.height, 0.001f)
        assertEquals(50f, resized.left, 0.001f)
        assertEquals(100f, resized.top, 0.001f)
        assertTrue(resized.right <= image.right && resized.bottom <= image.bottom)
    }
}
