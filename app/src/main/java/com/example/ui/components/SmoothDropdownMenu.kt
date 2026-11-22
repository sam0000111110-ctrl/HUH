package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.example.ui.theme.HuhTheme

/**
 * Position provider for smooth dropdown popups, respecting window bounds and RTL/LTR layout.
 */
class SmoothDropdownMenuPositionProvider(
    private val contentOffset: DpOffset,
    private val density: Density
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val xOffsetPx = with(density) { contentOffset.x.roundToPx() }
        val yOffsetPx = with(density) { contentOffset.y.roundToPx() }

        // Compute horizontal position: align with anchor start or clamp to window
        val x = if (layoutDirection == LayoutDirection.Ltr) {
            anchorBounds.left + xOffsetPx
        } else {
            anchorBounds.right - popupContentSize.width - xOffsetPx
        }.coerceIn(8, (windowSize.width - popupContentSize.width - 8).coerceAtLeast(8))

        // Compute vertical position: prioritize below anchor; flip above if space is tight
        val yBelow = anchorBounds.bottom + yOffsetPx
        val yAbove = anchorBounds.top - popupContentSize.height - yOffsetPx

        val y = if (yBelow + popupContentSize.height <= windowSize.height - 16) {
            yBelow
        } else if (yAbove >= 16) {
            yAbove
        } else {
            yBelow.coerceAtMost((windowSize.height - popupContentSize.height - 16).coerceAtLeast(16))
        }

        return IntOffset(x, y)
    }
}

/**
 * Dropdown Menu with high-fidelity Enter and Exit animation transitions (Scale, Fade, Expand/Shrink).
 * Uses [updateTransition] to keep the Popup mounted throughout the exit animation sequence.
 */
@Composable
fun SmoothDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 6.dp),
    shape: Shape = RoundedCornerShape(16.dp),
    containerColor: Color = HuhTheme.colors.surfaceElevated,
    borderColor: Color = HuhTheme.colors.outline.copy(alpha = 0.5f),
    shadowElevation: Dp = 14.dp,
    minWidth: Dp = 140.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val density = LocalDensity.current
    val transition = updateTransition(targetState = expanded, label = "SmoothDropdownTransition")

    // Keep popup in composition during both enter and exit animation phases
    if (transition.currentState || transition.targetState) {
        val positionProvider = remember(offset, density) {
            SmoothDropdownMenuPositionProvider(offset, density)
        }

        Popup(
            popupPositionProvider = positionProvider,
            onDismissRequest = onDismissRequest,
            properties = PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            transition.AnimatedVisibility(
                visible = { state -> state },
                enter = fadeIn(
                    animationSpec = tween(durationMillis = 220, easing = LinearOutSlowInEasing)
                ) + scaleIn(
                    initialScale = 0.86f,
                    transformOrigin = TransformOrigin(0.5f, 0f),
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + expandVertically(
                    expandFrom = Alignment.Top,
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
                ),
                exit = fadeOut(
                    animationSpec = tween(durationMillis = 180, easing = FastOutLinearInEasing)
                ) + scaleOut(
                    targetScale = 0.88f,
                    transformOrigin = TransformOrigin(0.5f, 0f),
                    animationSpec = tween(durationMillis = 180)
                ) + shrinkVertically(
                    shrinkTowards = Alignment.Top,
                    animationSpec = tween(durationMillis = 180, easing = FastOutLinearInEasing)
                )
            ) {
                Surface(
                    modifier = modifier
                        .widthIn(min = minWidth)
                        .shadow(shadowElevation, shape)
                        .border(
                            BorderStroke(
                                1.dp,
                                Brush.verticalGradient(
                                    listOf(
                                        borderColor.copy(alpha = 0.7f),
                                        borderColor.copy(alpha = 0.3f)
                                    )
                                )
                            ),
                            shape
                        ),
                    shape = shape,
                    color = containerColor,
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier
                            .width(IntrinsicSize.Max)
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        content = content
                    )
                }
            }
        }
    }
}

/**
 * Dropdown item within [SmoothDropdownMenu] with ripple, hover feedback, and selection styling.
 */
@Composable
fun SmoothDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    isSelected: Boolean = false,
    enabled: Boolean = true
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick),
        color = if (isSelected) HuhTheme.colors.primary.copy(alpha = 0.16f) else Color.Transparent,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 9.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(10.dp))
            }
            Box(modifier = Modifier.weight(1f)) {
                text()
            }
            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(10.dp))
                trailingContent()
            }
        }
    }
}
