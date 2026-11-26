package com.glass.todo.util

import com.glass.todo.data.WidgetLayoutMode
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetLayoutGeometryTest {

    @Test
    fun automaticModeUsesWideRatioAtAndAboveThreshold() {
        assertEquals(WidgetLayoutMode.WIDE, WidgetLayoutGeometry.resolve(135, 100, WidgetLayoutMode.AUTO))
        assertEquals(WidgetLayoutMode.WIDE, WidgetLayoutGeometry.resolve(600, 200, WidgetLayoutMode.AUTO))
    }

    @Test
    fun automaticModeUsesTallRatioAtAndBelowThreshold() {
        assertEquals(WidgetLayoutMode.TALL, WidgetLayoutGeometry.resolve(75, 100, WidgetLayoutMode.AUTO))
        assertEquals(WidgetLayoutMode.TALL, WidgetLayoutGeometry.resolve(200, 600, WidgetLayoutMode.AUTO))
    }

    @Test
    fun automaticModeUsesSquareForIntermediateRatios() {
        assertEquals(WidgetLayoutMode.SQUARE, WidgetLayoutGeometry.resolve(100, 100, WidgetLayoutMode.AUTO))
        assertEquals(WidgetLayoutMode.SQUARE, WidgetLayoutGeometry.resolve(240, 180, WidgetLayoutMode.AUTO))
    }

    @Test
    fun explicitModeAlwaysOverridesMeasuredAspectRatio() {
        WidgetLayoutMode.entries.filter { it != WidgetLayoutMode.AUTO }.forEach { mode ->
            assertEquals(mode, WidgetLayoutGeometry.resolve(600, 100, mode))
            assertEquals(mode, WidgetLayoutGeometry.resolve(100, 600, mode))
        }
    }

    @Test
    fun invalidReportedDimensionsNeverCrashOrProduceZeroRatio() {
        assertEquals(WidgetLayoutMode.SQUARE, WidgetLayoutGeometry.resolve(-100, 0, WidgetLayoutMode.AUTO))
        assertEquals(WidgetLayoutMode.SQUARE, WidgetLayoutGeometry.resolve(0, -100, WidgetLayoutMode.AUTO))
        assertEquals(1f, WidgetLayoutGeometry.aspectRatio(0, 0), 0.0001f)
        assertEquals(2f, WidgetLayoutGeometry.aspectRatio(200, 100), 0.0001f)
    }

    @Test
    fun todoUsesCompactRowsOnlyWhenWidthIsLimited() {
        WidgetLayoutMode.entries.forEach { mode ->
            assertEquals(
                WidgetLayoutGeometry.TodoLayout.COMPACT_LIST,
                WidgetLayoutGeometry.todoLayout(mode, 259)
            )
            assertEquals(
                WidgetLayoutGeometry.TodoLayout.COMFORT_LIST,
                WidgetLayoutGeometry.todoLayout(mode, 280)
            )
        }
    }

    @Test
    fun todoStaysSingleColumnEvenOnVeryWideWidgets() {
        assertEquals(1, WidgetLayoutGeometry.todoColumns(WidgetLayoutMode.WIDE, 240))
        assertEquals(1, WidgetLayoutGeometry.todoColumns(WidgetLayoutMode.WIDE, 600))
        assertEquals(1, WidgetLayoutGeometry.todoColumns(WidgetLayoutMode.SQUARE, 420))
        assertEquals(1, WidgetLayoutGeometry.todoColumns(WidgetLayoutMode.AUTO, 0))
    }

    @Test
    fun displayPixelBudgetUsesDpAndScreenDensity() {
        assertEquals(135_000, WidgetLayoutGeometry.displayPixels(250, 180, 3f))
        assertEquals(2, WidgetLayoutGeometry.displayPixels(0, 0, 2f))
        assertEquals(Int.MAX_VALUE, WidgetLayoutGeometry.displayPixels(100_000, 100_000, 4f))
    }
}
