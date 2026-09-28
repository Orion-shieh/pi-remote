package com.piremote.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piremote.app.data.SessionRepository
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Top-level navigation tabs.
 * 0: 电脑终端 (PC Remote Sessions)
 * 1: 手机终端 (Dedicated Local Phone Terminal)
 * 2: 电脑路径 (Remote Path Explorer)
 * 3: 设置 (Settings)
 */
enum class MainTab {
    HOME,
    PHONE_TERMINAL,
    REMOTE_PATH,
    SETTINGS,
}

@Composable
fun App(repository: SessionRepository) {
    val connection by repository.connection.collectAsState()
    val activeSessionId by repository.activeSessionId.collectAsState()
    val title by repository.title.collectAsState()

    val context = LocalContext.current
    val phoneSessionManager = remember { PhoneSessionManager.getInstance(context) }
    val activePhoneSessionId by phoneSessionManager.activeSessionId.collectAsState()

    val scope = rememberCoroutineScope()
    var screenWidthPx by remember { mutableFloatStateOf(0f) }

    val initialTab = if (!repository.settingsStore.isConfigured) 3 else 0
    val animatedProgress = remember { Animatable(initialTab.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(initialTab.toFloat()) }

    // Instantaneous, unified continuous progress lambda (prevents App recomposition during animation!)
    val currentFractionProvider: () -> Float = {
        if (isDragging) dragProgress else animatedProgress.value
    }

    // Stable navigation callbacks (never recreated on fraction changes)
    val onOpenSettings: () -> Unit = remember {
        {
            scope.launch {
                isDragging = false
                animatedProgress.animateTo(
                    3f,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 340f),
                )
            }
        }
    }

    val onOpenRemotePath: () -> Unit = remember {
        {
            scope.launch {
                isDragging = false
                animatedProgress.animateTo(
                    2f,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 340f),
                )
            }
        }
    }

    val onOpenPhoneTerminal: () -> Unit = remember {
        {
            scope.launch {
                isDragging = false
                animatedProgress.animateTo(
                    1f,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 340f),
                )
            }
        }
    }

    val onBackToHome: () -> Unit = remember {
        {
            scope.launch {
                isDragging = false
                animatedProgress.animateTo(
                    0f,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 340f),
                )
            }
        }
    }

    // Handle back button presses (derivedStateOf only triggers when crossing 0.5f threshold):
    val backEnabled by remember {
        derivedStateOf { activeSessionId == null && activePhoneSessionId == null && animatedProgress.value > 0.5f }
    }
    val isRemotePathActive by remember {
        derivedStateOf { abs(animatedProgress.value - 2f) < 0.5f }
    }
    BackHandler(enabled = backEnabled) {
        if (animatedProgress.value > 0.5f) {
            onBackToHome()
        }
    }

    LaunchedEffect(Unit) { repository.connect() }

    // Returning to the list (or first launch) refreshes it
    LaunchedEffect(activeSessionId) {
        if (activeSessionId == null) repository.refresh()
    }

    HierarchicalPushPopContainer(
        targetSessionId = activeSessionId,
        onDetach = { repository.detachActive() },
        underlyingContent = {
            // Screen draggable state so the user can also swipe horizontally on the screens
            val screenDraggableState = rememberDraggableState { deltaPx ->
                if (screenWidthPx > 0f) {
                    val deltaFraction = -deltaPx / screenWidthPx
                    dragProgress = (dragProgress + deltaFraction).coerceIn(0f, 3f)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(GeekColors.DeepCanvas),
            ) {
                // Screen Viewport Container (Handles screen swiping without capturing bottom bar gestures)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .onSizeChanged { screenWidthPx = it.width.toFloat() }
                        .draggable(
                            state = screenDraggableState,
                            orientation = Orientation.Horizontal,
                            enabled = activePhoneSessionId == null && activeSessionId == null,
                            onDragStarted = {
                                dragProgress = currentFractionProvider()
                                isDragging = true
                            },
                            onDragStopped = { velocity ->
                                val currentVal = dragProgress
                                val targetIndex = when {
                                    velocity < -500f -> min(3, currentVal.toInt() + 1)
                                    velocity > 500f -> max(0, ceil(currentVal).toInt() - 1)
                                    else -> currentVal.roundToInt().coerceIn(0, 3)
                                }
                                scope.launch {
                                    animatedProgress.snapTo(dragProgress)
                                    isDragging = false
                                    animatedProgress.animateTo(
                                        targetIndex.toFloat(),
                                        animationSpec = spring(dampingRatio = 0.72f, stiffness = 340f),
                                    )
                                }
                            },
                        ),
                ) {
                    // Keep all 4 screens composed and ready in memory.
                    // Transitions run 100% in the Draw phase via GPU translationX with ZERO recompositions!
                    for (pageIndex in 0..3) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val current = currentFractionProvider()
                                    val offset = pageIndex - current
                                    translationX = offset * size.width
                                    alpha = if (abs(offset) >= 1.02f) 0f else (1f - (abs(offset) * 0.15f)).coerceIn(0f, 1f)
                                },
                        ) {
                            when (pageIndex) {
                                0 -> SessionListScreen(
                                    repository = repository,
                                    connection = connection,
                                    onOpenSettings = onOpenSettings,
                                    onOpenRemotePath = onOpenRemotePath,
                                )
                                1 -> PhoneTerminalScreen(
                                    manager = phoneSessionManager,
                                    onSwitchToPc = {
                                        scope.launch {
                                            animatedProgress.animateTo(
                                                0f,
                                                animationSpec = spring(dampingRatio = 0.72f, stiffness = 340f),
                                            )
                                        }
                                    },
                                )
                                2 -> RemotePathScreen(
                                    repository = repository,
                                    connection = connection,
                                    isActive = isRemotePathActive,
                                    onBackToHome = onBackToHome,
                                )
                                3 -> SettingsScreen(
                                    repository = repository,
                                    connection = connection,
                                    onDone = onBackToHome,
                                )
                            }
                        }
                    }
                }

                // Check keyboard status and phone terminal fullscreen status to smoothly hide navigation dock
                val density = LocalDensity.current
                val isKeyboardOpen = WindowInsets.ime.getBottom(density) > 0
                val shouldShowBottomBar = !isKeyboardOpen && activePhoneSessionId == null
                val bottomBarAlpha by animateFloatAsState(
                    targetValue = if (shouldShowBottomBar) 1f else 0f,
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
                    label = "bottomBarAlpha",
                )

                // Floating Apple Liquid Glass Navigation Bar with real-time draggable slider
                // Placed as a sibling in root Box so bottom bar gestures are 100% dedicated & isolated
                if (bottomBarAlpha > 0.01f) {
                    LiquidGlassBottomBar(
                        currentFraction = currentFractionProvider,
                        onTabSelected = { selectedTab ->
                            val targetPage = when (selectedTab) {
                                MainTab.HOME -> 0
                                MainTab.PHONE_TERMINAL -> 1
                                MainTab.REMOTE_PATH -> 2
                                MainTab.SETTINGS -> 3
                            }
                            scope.launch {
                                isDragging = false
                                animatedProgress.animateTo(
                                    targetPage.toFloat(),
                                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 340f),
                                )
                            }
                        },
                        onSliderDragStarted = {
                            dragProgress = currentFractionProvider()
                            isDragging = true
                        },
                        onSliderDrag = { deltaFraction ->
                            dragProgress = (dragProgress + deltaFraction).coerceIn(0f, 3f)
                        },
                        onSliderDragStopped = { velocityFraction ->
                            val currentVal = dragProgress
                            val targetIndex = when {
                                velocityFraction > 1.2f -> min(3, (currentVal + 0.35f).toInt() + 1)
                                velocityFraction < -1.2f -> max(0, (currentVal + 0.65f).toInt() - 1)
                                else -> currentVal.roundToInt().coerceIn(0, 3)
                            }
                            scope.launch {
                                animatedProgress.snapTo(dragProgress)
                                isDragging = false
                                animatedProgress.animateTo(
                                    targetIndex.toFloat(),
                                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 340f),
                                )
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .graphicsLayer { alpha = bottomBarAlpha },
                    )
                }
            }
        },
        activeContent = { sid, requestExit ->
            TerminalScreen(
                repository = repository,
                sessionId = sid,
                title = title,
                onBack = requestExit,
            )
        },
    )
}

/**
 * Apple-style Liquid Glass (Frosted Glassmorphism) Floating Navigation Dock.
 * Features:
 * - Floating Island Capsule geometry (visionOS / iOS style)
 * - Multi-layer Frosted Translucent Glass with Specular Light Rim
 * - Diagonal Glass Reflection Sheen
 * - Fluid Liquid Droplet Active Indicator with Spring Physics & Real-time Gesture Dragging
 * - Specular Bevel Highlights & Dynamic Color Transitions
 */
@Composable
private fun LiquidGlassBottomBar(
    currentFraction: () -> Float,
    onTabSelected: (MainTab) -> Unit,
    onSliderDragStarted: () -> Unit,
    onSliderDrag: (deltaFraction: Float) -> Unit,
    onSliderDragStopped: (velocityFraction: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = GeekColors.isDark
    val dockShape = RoundedCornerShape(34.dp)
    var isBarDragging by remember { mutableStateOf(false) }

    // Optical glass border: specular highlight on top bevel, soft ambient on sides/bottom
    val glassBorderBrush = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (isDark) 0.38f else 0.85f),
            Color.White.copy(alpha = if (isDark) 0.12f else 0.32f),
            Color.White.copy(alpha = if (isDark) 0.03f else 0.10f),
        )
    )

    // Frosted liquid glass backdrop brush with high translucency
    val glassBackgroundBrush = if (isDark) {
        Brush.verticalGradient(
            listOf(
                Color(0xD9181D2E), // 85% opacity dark sapphire glass
                Color(0xBF0E121E), // 75% opacity deep cosmic obsidian
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color(0xFFFFFFFF),
                Color(0xFFFFFFFF),
            )
        )
    }

    // Floating island capsule container
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, bottom = 12.dp)
            .shadow(
                elevation = if (isDark) 22.dp else 12.dp,
                shape = dockShape,
                spotColor = if (isDark) Color(0x99000000) else Color(0x18000000),
                ambientColor = if (isDark) Color(0x4D6366F1) else Color(0x0A000000),
            )
            .clip(dockShape)
            .background(glassBackgroundBrush)
            .border(
                if (isDark) BorderStroke(1.2.dp, glassBorderBrush) else BorderStroke(0.dp, Color.Transparent),
                dockShape,
            )
            .height(64.dp),
    ) {
        // Specular gloss reflection layer (subtle diagonal sheen across the glass)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = if (isDark) 0.12f else 0.22f),
                            Color.Transparent,
                            Color.White.copy(alpha = if (isDark) 0.03f else 0.06f),
                        )
                    )
                )
        )

        var barWidthPx by remember { mutableFloatStateOf(0f) }
        val tabWidthPx = if (barWidthPx > 0f) barWidthPx / 4f else 1f

        // Draggable gesture state for real-time swiping/dragging
        val draggableState = rememberDraggableState { deltaPx ->
            if (barWidthPx > 0f) {
                val deltaFraction = deltaPx / tabWidthPx
                onSliderDrag(deltaFraction)
            }
        }

        // Fluid liquid indicator & tab items
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(5.dp)
                .onSizeChanged { barWidthPx = it.width.toFloat() }
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                    startDragImmediately = isBarDragging,
                    onDragStarted = {
                        isBarDragging = true
                        onSliderDragStarted()
                    },
                    onDragStopped = { velocity ->
                        isBarDragging = false
                        val velocityFraction = if (tabWidthPx > 0f) velocity / tabWidthPx else 0f
                        onSliderDragStopped(velocityFraction)
                    },
                ),
        ) {
            // Liquid Droplet Active Pill (Apple-style fluid floating bubble)
            val pillShape = RoundedCornerShape(26.dp)
            val bubbleScaleX by animateFloatAsState(
                targetValue = if (isBarDragging) 1.05f else 1.0f,
                animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
                label = "bubbleStretch",
            )

            Box(
                modifier = Modifier
                    .offset {
                        val fraction = currentFraction().coerceIn(0f, 3f)
                        IntOffset(
                            x = (fraction * (barWidthPx / 4f)).roundToInt(),
                            y = 0,
                        )
                    }
                    .fillMaxWidth(1f / 4f)
                    .fillMaxHeight()
                    .padding(horizontal = 2.dp)
                    .graphicsLayer {
                        scaleX = bubbleScaleX
                    }
                    .shadow(
                        elevation = if (isDark) 10.dp else 6.dp,
                        shape = pillShape,
                        spotColor = if (isDark) Color(0x666366F1) else Color(0x28000000),
                        ambientColor = if (isDark) Color(0x338B5CF6) else Color(0x14000000),
                    )
                    .clip(pillShape)
                    .background(
                        if (isDark) {
                            Brush.linearGradient(
                                listOf(
                                    Color(0x4D6366F1), // Electric indigo gloss
                                    Color(0x388B5CF6), // Purple glow
                                )
                            )
                        } else {
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFFEFF2F8),
                                    Color(0xFFE5EAF4),
                                )
                            )
                        }
                    )
                    .border(
                        if (isDark) {
                            BorderStroke(
                                1.dp,
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.45f),
                                        Color(0x306366F1),
                                        Color.Transparent,
                                    )
                                )
                            )
                        } else {
                            BorderStroke(0.dp, Color.Transparent)
                        },
                        pillShape,
                    )
            ) {
                // Top specular highlight inside the liquid bubble
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = if (isDark) 0.25f else 0.45f),
                                    Color.Transparent,
                                )
                            )
                        )
                )
            }

            // Tab items row (positioned directly above the liquid bubble)
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiquidTabItem(
                    label = "电脑终端",
                    icon = Icons.Default.Dns,
                    targetIndex = 0,
                    currentFraction = currentFraction,
                    onClick = { onTabSelected(MainTab.HOME) },
                    modifier = Modifier.weight(1f),
                    isDark = isDark,
                )

                LiquidTabItem(
                    label = "手机终端",
                    icon = Icons.Default.PhoneAndroid,
                    targetIndex = 1,
                    currentFraction = currentFraction,
                    onClick = { onTabSelected(MainTab.PHONE_TERMINAL) },
                    modifier = Modifier.weight(1f),
                    isDark = isDark,
                )

                LiquidTabItem(
                    label = "电脑路径",
                    icon = Icons.Default.FolderOpen,
                    targetIndex = 2,
                    currentFraction = currentFraction,
                    onClick = { onTabSelected(MainTab.REMOTE_PATH) },
                    modifier = Modifier.weight(1f),
                    isDark = isDark,
                )

                LiquidTabItem(
                    label = "设置",
                    icon = Icons.Default.Settings,
                    targetIndex = 3,
                    currentFraction = currentFraction,
                    onClick = { onTabSelected(MainTab.SETTINGS) },
                    modifier = Modifier.weight(1f),
                    isDark = isDark,
                )
            }
        }
    }
}

/**
 * Individual Liquid Glass Tab Item.
 * Seamlessly responds to the liquid bubble's continuous drag progress.
 */
@Composable
private fun LiquidTabItem(
    label: String,
    icon: ImageVector,
    targetIndex: Int,
    currentFraction: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean,
) {
    val progress = (1f - abs(currentFraction().coerceIn(0f, 3f) - targetIndex)).coerceIn(0f, 1f)

    // Continuous color interpolation based on bubble proximity
    val activeColor = if (isDark) Color.White else GeekColors.BrandAccent
    val inactiveColor = if (isDark) Color.White.copy(alpha = 0.50f) else GeekColors.TextMuted
    val contentColor = lerp(inactiveColor, activeColor, progress)

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(26.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier
                .size(21.dp)
                .graphicsLayer {
                    val scale = 1.0f + 0.12f * progress
                    scaleX = scale
                    scaleY = scale
                },
        )

        Spacer(Modifier.height(3.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                letterSpacing = (-0.2).sp,
            ),
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
