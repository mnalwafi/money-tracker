package com.danilkinkin.buckwheat.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * Buckwheat Centralized Design System Tokens
 *
 * Provides a unified source of truth for:
 * 1. Spacing and layout gaps
 * 2. Component corner shapes and radii
 * 3. Dynamic surface containers and borders
 * 4. Control dimensions and gesture physics constants
 * 5. Typography tokens (drawer headers, card titles)
 */
object BuckwheatDesignSystem {

    object Spacing {
        val xxs: Dp = 2.dp
        val xs: Dp = 4.dp
        val s: Dp = 8.dp
        val m: Dp = 12.dp
        val l: Dp = 16.dp
        val xl: Dp = 20.dp
        val xxl: Dp = 24.dp
        val xxxl: Dp = 32.dp

        // Layout rules
        val screenPadding: Dp = 16.dp
        val cardPadding: Dp = 16.dp
        val heroPadding: Dp = 20.dp
        val cardGap: Dp = 12.dp
        val sectionGap: Dp = 16.dp
        val labelToInputGap: Dp = 8.dp
        val formFieldGap: Dp = 16.dp
        val headerToBodyGap: Dp = 4.dp
    }

    object Shapes {
        val small: Shape = RoundedCornerShape(8.dp)
        val medium: Shape = RoundedCornerShape(12.dp)
        val input: Shape = RoundedCornerShape(14.dp)
        val button: Shape = RoundedCornerShape(16.dp)
        val card: Shape = RoundedCornerShape(20.dp)
        val cardHero: Shape = RoundedCornerShape(28.dp)
        val cardItem: Shape = RoundedCornerShape(22.dp)
        val sheet: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        val pill: Shape = CircleShape
    }

    object Controls {
        val inputHeight: Dp = 56.dp
        val buttonHeight: Dp = 52.dp
        val smallButtonHeight: Dp = 40.dp
        val dragHandleWidth: Dp = 36.dp
        val dragHandleHeight: Dp = 4.dp
    }

    object Typography {
        /**
         * Universal Drawer Title Style: applied to all sheets (Dashboard, Recurring, History, Settings, Wallet).
         * Standardized to MaterialTheme.typography.titleLarge + Bold, centered in header.
         */
        val drawerTitle: TextStyle
            @Composable
            @ReadOnlyComposable
            get() = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
            )
    }

    object Drawers {
        const val xl: Float = 0.92f        // 92% screen height (Settings, Dashboard, Budget)
        const val lg: Float = 0.88f        // 88% screen height (Recurring & Subscriptions, Viewer History, Analytics)
        const val default: Float = 0.75f   // 75% standard drawer
        const val sm: Float = 0.60f        // 60% small drawer
        const val xs: Float = 0.45f        // 45% compact drawer
    }

    object Physics {
        const val springDampingRatio: Float = 0.85f
        const val springStiffness: Float = 380f
        val velocityThreshold: Dp = 125.dp
        val dismissThreshold: Dp = 40.dp
        const val dismissVelocityThreshold: Float = 150f
        const val dragSlopMultiplier: Float = 1.75f
    }

    object Colors {
        /**
         * Unified bottom sheet container background across all drawers (Dashboard, Recurring, Budget, Settings).
         */
        val sheetContainer: Color
            @Composable
            @ReadOnlyComposable
            get() = MaterialTheme.colorScheme.surface

        /**
         * Standard card container color aligned with Material 3 dynamic color tokens.
         */
        val cardContainer: Color
            @Composable
            @ReadOnlyComposable
            get() = MaterialTheme.colorScheme.surfaceContainer

        /**
         * Slightly elevated container for nested or secondary cards.
         */
        val cardContainerHigh: Color
            @Composable
            @ReadOnlyComposable
            get() = MaterialTheme.colorScheme.surfaceContainerHigh

        /**
         * Subtle card border stroke harmonized with outlineVariant.
         */
        val cardBorder: BorderStroke
            @Composable
            @ReadOnlyComposable
            get() = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

        /**
         * Drag handle pill color.
         */
        val dragHandle: Color
            @Composable
            @ReadOnlyComposable
            get() = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)

        /**
         * Primary high-contrast content text color.
         */
        val textPrimary: Color
            @Composable
            @ReadOnlyComposable
            get() = MaterialTheme.colorScheme.onSurface

        /**
         * Secondary metadata and hint text color.
         */
        val textSecondary: Color
            @Composable
            @ReadOnlyComposable
            get() = MaterialTheme.colorScheme.onSurfaceVariant
    }
}
