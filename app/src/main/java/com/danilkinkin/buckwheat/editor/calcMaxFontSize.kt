package com.danilkinkin.buckwheat.editor

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imeAnimationSource
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.Paragraph
import androidx.compose.ui.text.ParagraphIntrinsics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.danilkinkin.buckwheat.util.max
import com.danilkinkin.buckwheat.util.min
import java.util.LinkedHashMap
import kotlin.math.ceil

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun isImeAnimationActive(): Boolean {
    val density = LocalDensity.current
    return WindowInsets.imeAnimationSource.getBottom(density) != WindowInsets.imeAnimationTarget.getBottom(density)
}

private data class MaxFontCacheKey(
    val heightBucket: Int,
    val text: String,
    val densityDpi: Float,
)

private val maxFontCache = object : LinkedHashMap<MaxFontCacheKey, TextUnit>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<MaxFontCacheKey, TextUnit>?): Boolean {
        return size > 128
    }
}

private data class AdaptiveFontCacheKey(
    val heightBucket: Int,
    val widthBucket: Int,
    val text: String,
    val minSp: Float,
    val maxSp: Float,
    val densityDpi: Float,
)

private val adaptiveFontCache = object : LinkedHashMap<AdaptiveFontCacheKey, TextUnit>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<AdaptiveFontCacheKey, TextUnit>?): Boolean {
        return size > 128
    }
}

@Composable
fun calcMaxFont(
    height: Float,
    text: String = "SAMPLE 1234567890",
    style: TextStyle = MaterialTheme.typography.displayLarge,
): TextUnit {
    if (height <= 0f) return 14.sp

    val density = LocalDensity.current
    val heightBucket = ((height / 4f).toInt() * 4).coerceAtLeast(0)
    val cacheKey = MaxFontCacheKey(heightBucket, text, density.density)

    synchronized(maxFontCache) {
        maxFontCache[cacheKey]?.let { return it }
    }

    val measureFontSize = 100.sp
    val fontFamilyResolver = LocalFontFamilyResolver.current

    val intrinsics = ParagraphIntrinsics(
        text = text,
        style = style.copy(fontSize = measureFontSize),
        density = density,
        fontFamilyResolver = fontFamilyResolver,
    )

    val paragraph = Paragraph(
        paragraphIntrinsics = intrinsics,
        constraints = Constraints(maxWidth = ceil(1000f).toInt()),
        maxLines = 1,
        overflow = TextOverflow.Clip,
    )

    val baseline = paragraph.firstBaseline.coerceAtLeast(1f)
    val calculated = with(density) {
        ((measureFontSize.toPx() / baseline) * height).toSp()
    }

    synchronized(maxFontCache) {
        maxFontCache[cacheKey] = calculated
    }

    return calculated
}

@Composable
fun calcAdaptiveFont(
    height: Float,
    width: Float,
    minFontSize: TextUnit,
    maxFontSize: TextUnit,
    text: String = "SAMPLE 1234567890",
    style: TextStyle = MaterialTheme.typography.displayLarge,
): TextUnit {
    if (height <= 0f || width <= 0f) {
        return minFontSize
    }

    val density = LocalDensity.current
    val isImeAnimating = isImeAnimationActive()
    var cachedFontSize by remember(text) { mutableStateOf<TextUnit?>(null) }

    // Avoid recalculating text font sizes dynamically during active window inset animations
    // (keyboard opening/closing). Freeze and return the previous stable size.
    if (isImeAnimating && cachedFontSize != null) {
        return cachedFontSize!!
    }

    // Quantize dimensions to 4px buckets to prevent subpixel jitter from busting the cache
    val heightBucket = ((height / 4f).toInt() * 4).coerceAtLeast(0)
    val widthBucket = ((width / 4f).toInt() * 4).coerceAtLeast(0)

    val cacheKey = AdaptiveFontCacheKey(
        heightBucket = heightBucket,
        widthBucket = widthBucket,
        text = text,
        minSp = minFontSize.value,
        maxSp = maxFontSize.value,
        densityDpi = density.density,
    )

    synchronized(adaptiveFontCache) {
        adaptiveFontCache[cacheKey]?.let {
            cachedFontSize = it
            return it
        }
    }

    val initialFontSize = calcMaxFont(height = height, text = text, style = style)
    val fontFamilyResolver = LocalFontFamilyResolver.current

    val intrinsics = ParagraphIntrinsics(
        text = text,
        style = style.copy(fontSize = initialFontSize),
        density = density,
        fontFamilyResolver = fontFamilyResolver,
    )

    val resultSize: TextUnit = if (intrinsics.maxIntrinsicWidth <= width) {
        min(max(minFontSize, initialFontSize), maxFontSize)
    } else {
        // Fast linear scale estimation: single-line text width is roughly linear with font size
        val scaleRatio = (width / intrinsics.maxIntrinsicWidth).coerceIn(0.1f, 1f)
        val estimatedSp = min(max(minFontSize, (initialFontSize.value * scaleRatio).sp), maxFontSize)

        // Single verification pass to account for non-linear kerning/spacing
        val testIntrinsics = ParagraphIntrinsics(
            text = text,
            style = style.copy(fontSize = estimatedSp),
            density = density,
            fontFamilyResolver = fontFamilyResolver,
        )

        if (testIntrinsics.maxIntrinsicWidth <= width) {
            estimatedSp
        } else {
            // Minor refinement step down
            min(max(minFontSize, (estimatedSp.value * 0.95f).sp), maxFontSize)
        }
    }

    synchronized(adaptiveFontCache) {
        adaptiveFontCache[cacheKey] = resultSize
    }
    cachedFontSize = resultSize

    return resultSize
}
