package com.danilkinkin.buckwheat.base

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.ceil

class WavyShape(
    val period: Dp,
    val amplitude: Dp,
    val shift: Float,
) : Shape {

    companion object {
        private val localScratchPaths = ThreadLocal.withInitial {
            Pair(Path(), Path())
        }
    }

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val halfPeriod = with(density) { period.toPx() } / 2
        val amp = with(density) { amplitude.toPx() }

        val (scratchWavy, scratchBounds) = localScratchPaths.get()!!
        scratchWavy.reset()
        scratchBounds.reset()

        scratchWavy.moveTo(0f, 0f)
        scratchWavy.lineTo(size.width - amp, -halfPeriod * 2.5f + halfPeriod * 2 * shift)
        repeat(ceil(size.height / halfPeriod + 3).toInt()) { i ->
            scratchWavy.relativeQuadraticTo(
                dx1 = 2 * amp * (if (i % 2 == 0) 1 else -1),
                dy1 = halfPeriod / 2,
                dx2 = 0f,
                dy2 = halfPeriod,
            )
        }
        scratchWavy.lineTo(0f, size.height)
        scratchWavy.close()

        scratchBounds.addRect(Rect(offset = Offset.Zero, size = size))

        val resultPath = Path()
        resultPath.op(scratchWavy, scratchBounds, PathOperation.Intersect)
        return Outline.Generic(resultPath)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WavyShape) return false
        return period == other.period && amplitude == other.amplitude && shift == other.shift
    }

    override fun hashCode(): Int {
        var result = period.hashCode()
        result = 31 * result + amplitude.hashCode()
        result = 31 * result + shift.hashCode()
        return result
    }
}