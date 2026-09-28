package com.piremote.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Modern hierarchical push/pop navigation container with Apple & Material 3 physics.
 *
 * Features:
 * - Forward Push Transition (左滑推进): Slide in from right (100% -> 0%) with subtle depth shadow and background parallax (-20%).
 * - Backward Pop Transition (右滑退场): Slide out to right (0% -> 100%) when clicking Back, pressing System Back, or swiping.
 * - Interactive Edge-Swipe Gesture (边缘跟手滑动返回): Real-time finger tracking on left edge (0~32dp) with spring threshold rebound.
 */
@Composable
fun HierarchicalPushPopContainer(
    targetSessionId: String?,
    onDetach: () -> Unit,
    underlyingContent: @Composable () -> Unit,
    activeContent: @Composable (sessionId: String, requestExit: () -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()

    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    var displayedSessionId by remember { mutableStateOf<String?>(targetSessionId) }
    var isExiting by remember { mutableStateOf(false) }
    var isEdgeDragging by remember { mutableStateOf(false) }

    // progress: 0f = fully visible (onscreen), 1f = fully dismissed (offscreen right)
    val progress = remember { Animatable(if (targetSessionId != null) 0f else 1f) }

    val exitSpring = remember { spring<Float>(dampingRatio = 0.86f, stiffness = 420f) }
    val enterSpring = remember { spring<Float>(dampingRatio = 0.86f, stiffness = 420f) }
    val reboundSpring = remember { spring<Float>(dampingRatio = 0.78f, stiffness = 400f) }

    fun requestExit() {
        if (isExiting || displayedSessionId == null) return
        isExiting = true
        scope.launch {
            progress.animateTo(1f, animationSpec = exitSpring)
            displayedSessionId = null
            isExiting = false
            onDetach()
        }
    }

    // Intercept system hardware/gesture back when a session is being displayed
    BackHandler(enabled = displayedSessionId != null && !isExiting) {
        requestExit()
    }

    // Drive entrance / external exit
    LaunchedEffect(targetSessionId) {
        if (targetSessionId != null) {
            if (displayedSessionId == null) {
                displayedSessionId = targetSessionId
                isExiting = false
                progress.snapTo(1f)
                progress.animateTo(0f, animationSpec = enterSpring)
            } else if (displayedSessionId != targetSessionId) {
                // Switched session internally
                displayedSessionId = targetSessionId
                if (progress.value > 0f) {
                    progress.animateTo(0f, animationSpec = enterSpring)
                }
            }
        } else {
            // targetSessionId became null externally
            if (displayedSessionId != null && !isExiting && !isEdgeDragging) {
                isExiting = true
                progress.animateTo(1f, animationSpec = exitSpring)
                displayedSessionId = null
                isExiting = false
                onDetach()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { containerWidthPx = it.width.toFloat() },
    ) {
        // Base Layer: Underlying session list / main tabs
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = progress.value
                    val width = if (size.width > 0f) size.width else containerWidthPx
                    translationX = -0.15f * (1f - p) * width
                },
        ) {
            underlyingContent()

            // Subtle parallax darkening scrim: evaluated strictly in Draw phase via graphicsLayer (ZERO recomposition!)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = progress.value
                        alpha = (0.24f * (1f - p)).coerceIn(0f, 0.24f)
                    }
                    .background(Color.Black),
            )
        }

        // Active Layer: Terminal / Agent Screen
        val currentSessionId = displayedSessionId
        if (currentSessionId != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Evaluated strictly in Draw phase: ZERO recompositions of the active screen!
                        val p = progress.value
                        val width = if (size.width > 0f) size.width else (if (containerWidthPx > 0f) containerWidthPx else 2000f)
                        translationX = p * width
                    }
                    .background(GeekColors.DeepCanvas),
            ) {
                // Left shadow strip: gives the active screen physical depth over the list
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(18.dp)
                        .offset(x = (-18).dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.32f),
                                ),
                            ),
                        ),
                )

                // Active screen content
                activeContent(currentSessionId, ::requestExit)

                // Edge-Swipe Gesture Strip on the left margin (0~32dp)
                // Positioned below the top bar (top = 56dp) so it never interferes with the back button pill
                var dragDistX by remember { mutableFloatStateOf(0f) }
                val velocityTracker = remember { VelocityTracker() }

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(32.dp)
                        .padding(top = 56.dp)
                        .pointerInput(currentSessionId) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    isEdgeDragging = true
                                    dragDistX = 0f
                                    velocityTracker.resetTracking()
                                },
                                onDragEnd = {
                                    isEdgeDragging = false
                                    val velocityX = velocityTracker.calculateVelocity().x
                                    val width = if (containerWidthPx > 0f) containerWidthPx else size.width.toFloat()
                                    val shouldDismiss = dragDistX > width * 0.24f || velocityX > 800f

                                    if (shouldDismiss) {
                                        requestExit()
                                    } else {
                                        scope.launch {
                                            progress.animateTo(0f, animationSpec = reboundSpring)
                                        }
                                    }
                                },
                                onDragCancel = {
                                    isEdgeDragging = false
                                    scope.launch {
                                        progress.animateTo(0f, animationSpec = reboundSpring)
                                    }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    if (dragAmount > 0f || dragDistX > 0f) {
                                        dragDistX = (dragDistX + dragAmount).coerceAtLeast(0f)
                                        val width = if (containerWidthPx > 0f) containerWidthPx else size.width.toFloat()
                                        val fraction = if (width > 0f) (dragDistX / width).coerceIn(0f, 1f) else 0f
                                        scope.launch {
                                            progress.snapTo(fraction)
                                        }
                                    }
                                    change.consume()
                                },
                            )
                        },
                )
            }
        }
    }
}
