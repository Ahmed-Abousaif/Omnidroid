package com.omnidroid.app.mobile.shared.compose.dialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.ui.LibraryNeonGreen
import com.omnidroid.app.mobile.shared.compose.ui.chromeColors

/** The single place to tune dialog geometry. Colors follow the side-menu palette. */
object OmnidroidDialogStyle {
    val cornerRadius = 8.dp
    val buttonRadius = 6.dp
    val padding = 16.dp
    val gap = 8.dp
    val screenMargin = 12.dp
    val minWidth = 280.dp
    val width = 440.dp
    val inputWidth = 312.dp
    val expandedWidth = 640.dp
    val touchTarget = 48.dp
    val buttonHeight = 36.dp
    val scrollbarWidth = 3.dp
    val scrollbarOffset = 8.dp
    val scrollbarMinThumb = 24.dp
    val scrollCueHeight = 24.dp
    val destructiveContainer = Color(0xFFD32F2F)
    val onDestructiveContainer = Color.White
}

enum class DialogActionRole { Confirm, Cancel, Destructive, Alternative }
enum class DialogWidth { Content, Standard, Input, Expanded }

/** Scroll metrics supplied by a bounded list/grid that manages its own scrolling. */
data class DialogScrollInfo(val progress: Float, val visibleFraction: Float, val canScrollForward: Boolean)

private val LocalDialogActionRole = staticCompositionLocalOf { DialogActionRole.Confirm }
private val LocalDialogCancelFocus = staticCompositionLocalOf<FocusRequester?> { null }

/**
 * All app-owned modal UI uses this shell, including the legacy Activity/View bridge.
 * The body scrolls within finite bounds; lazy content can opt into managing its own scroll.
 * Callers own feature state and effects, never window geometry or button styling.
 */
@Composable
fun OmnidroidDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    confirmButton: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null,
    destructive: Boolean = false,
    width: DialogWidth = DialogWidth.Content,
    scrollBody: Boolean = true,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    handleControllerBack: Boolean = true,
    bodyScrollInfo: DialogScrollInfo? = null,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = dismissOnClickOutside,
        ),
    ) {
        OmnidroidDialogLayout(
            onDismissRequest, modifier, title, icon, text, confirmButton, dismissButton,
            destructive, width, scrollBody, dismissOnClickOutside, dismissOnBackPress, handleControllerBack,
            bodyScrollInfo,
        )
    }
}

@Composable
internal fun OmnidroidDialogLayout(
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    title: (@Composable () -> Unit)?,
    icon: (@Composable () -> Unit)?,
    text: (@Composable () -> Unit)?,
    confirmButton: (@Composable () -> Unit)?,
    dismissButton: (@Composable () -> Unit)?,
    destructive: Boolean,
    width: DialogWidth,
    scrollBody: Boolean,
    dismissOnClickOutside: Boolean,
    dismissOnBackPress: Boolean,
    handleControllerBack: Boolean,
    bodyScrollInfo: DialogScrollInfo? = null,
) {
    val chrome = chromeColors
    val cancelFocus = remember { FocusRequester() }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val inputMode = LocalInputModeManager.current.inputMode
    LaunchedEffect(destructive, windowFocused, inputMode) {
        if (destructive && windowFocused) {
            withFrameNanos { }
            runCatching { cancelFocus.requestFocus() }
        }
    }
    val scheme = MaterialTheme.colorScheme.copy(
        primary = LibraryNeonGreen,
        onPrimary = androidx.compose.ui.graphics.Color.Black,
        surface = chrome.panel,
        onSurface = chrome.content,
        onSurfaceVariant = chrome.muted,
        surfaceContainerHighest = chrome.panelSelected,
        outline = chrome.panelBorder,
    )
    MaterialTheme(
        colorScheme = scheme,
        shapes = MaterialTheme.shapes.copy(
            extraSmall = RoundedCornerShape(OmnidroidDialogStyle.buttonRadius),
            small = RoundedCornerShape(OmnidroidDialogStyle.cornerRadius),
        ),
    ) {
        val view = LocalView.current
        var origin by remember { mutableStateOf(IntOffset.Zero) }
        BoxWithConstraints(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            val back = event.key == Key.Escape || (handleControllerBack && event.key == Key.ButtonB)
            if (back && dismissOnBackPress) {
                if (event.type == KeyEventType.KeyUp) onDismissRequest()
                true
            } else false
        }.onGloballyPositioned {
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            origin = IntOffset(location[0], location[1])
        }) {
            val pane = rememberDialogPaneInsets(maxWidth, maxHeight, origin)
            val density = LocalDensity.current
            // A separate sibling catches outside taps without stealing taps from body controls.
            Box(Modifier.fillMaxSize().pointerInput(onDismissRequest, dismissOnClickOutside) {
                detectTapGestures { if (dismissOnClickOutside) onDismissRequest() }
            })
            BoxWithConstraints(
                Modifier.fillMaxSize()
                    .absolutePadding(
                        left = with(density) { pane.left.toDp() }, top = with(density) { pane.top.toDp() },
                        right = with(density) { pane.right.toDp() }, bottom = with(density) { pane.bottom.toDp() },
                    )
                    .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
                    .padding(OmnidroidDialogStyle.screenMargin),
                contentAlignment = Alignment.Center,
            ) {
                val availableHeight = maxHeight
                var headerHeight by remember { mutableIntStateOf(0) }
                var actionsHeight by remember { mutableIntStateOf(0) }
                val fixedHeight = with(density) { headerHeight.toDp() + actionsHeight.toDp() } +
                    OmnidroidDialogStyle.padding * 2 + OmnidroidDialogStyle.gap * 2
                val shortWindow = availableHeight < 280.dp * LocalDensity.current.fontScale ||
                    availableHeight < fixedHeight + OmnidroidDialogStyle.touchTarget
                val panelScroll = rememberScrollState()
                var panelViewport by remember { mutableIntStateOf(0) }
                val panelScrollInfo = dialogScrollInfo(panelScroll, panelViewport)
                Surface(
                    modifier = modifier
                        .widthIn(min = OmnidroidDialogStyle.minWidth.coerceAtMost(maxWidth), max = when (width) {
                            DialogWidth.Content -> OmnidroidDialogStyle.width
                            DialogWidth.Standard -> OmnidroidDialogStyle.width
                            DialogWidth.Input -> OmnidroidDialogStyle.inputWidth
                            DialogWidth.Expanded -> OmnidroidDialogStyle.expandedWidth
                        })
                        .then(if (width == DialogWidth.Content) Modifier.width(IntrinsicSize.Max) else Modifier.fillMaxWidth())
                        .heightIn(max = maxHeight)
                        .testTag("omnidroid-dialog")
                        .pointerInput(Unit) { detectTapGestures(onTap = {}) },
                    shape = RoundedCornerShape(OmnidroidDialogStyle.cornerRadius),
                    color = chrome.panel,
                    contentColor = chrome.content,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    Column {
                        Box(Modifier.weight(1f, fill = false).onSizeChanged { panelViewport = it.height }) {
                            Column(
                                Modifier.then(if (shortWindow) Modifier.verticalScroll(panelScroll) else Modifier)
                                    .padding(OmnidroidDialogStyle.padding),
                                verticalArrangement = Arrangement.spacedBy(OmnidroidDialogStyle.gap),
                            ) {
                                if (icon != null || title != null) {
                                    Column(
                                        Modifier.onSizeChanged { headerHeight = it.height },
                                        verticalArrangement = Arrangement.spacedBy(OmnidroidDialogStyle.gap),
                                    ) {
                                        icon?.invoke()
                                        if (title != null) {
                                            Box(Modifier.semantics { heading() }) {
                                                ProvideTextStyle(MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) {
                                                    title()
                                                }
                                            }
                                        }
                                    }
                                }
                                if (text != null) {
                                    val bodyScrollState = rememberScrollState()
                                    var bodyViewport by remember { mutableIntStateOf(0) }
                                    val ownScrollInfo = if (scrollBody && !shortWindow) dialogScrollInfo(bodyScrollState, bodyViewport) else null
                                    val bodyModifier = if (shortWindow) {
                                        // One scroll owner on short windows: the complete panel.
                                        Modifier
                                    } else {
                                        Modifier.weight(1f, fill = false)
                                    }
                                    Column(bodyModifier.fillMaxWidth()) {
                                        val viewportModifier = if (shortWindow) Modifier else Modifier.weight(1f, fill = false)
                                        Box(
                                            viewportModifier.fillMaxWidth()
                                                .onSizeChanged { bodyViewport = it.height }
                                                .testTag("omnidroid-dialog-body"),
                                        ) {
                                            CompositionLocalProvider(LocalContentColor provides chrome.muted) {
                                                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                                                    Column(
                                                        Modifier.fillMaxWidth().then(
                                                            if (scrollBody && !shortWindow) Modifier.verticalScroll(bodyScrollState) else Modifier,
                                                        ),
                                                    ) { text() }
                                                }
                                            }
                                            DialogScrollbar(
                                                ownScrollInfo ?: bodyScrollInfo,
                                                Modifier.align(Alignment.CenterEnd)
                                                    .offset(x = OmnidroidDialogStyle.scrollbarOffset)
                                                    .testTag("omnidroid-dialog-body-scrollbar"),
                                            )
                                        }
                                        DialogMoreBelow(
                                            ownScrollInfo?.canScrollForward == true || bodyScrollInfo?.canScrollForward == true,
                                            Modifier.testTag("omnidroid-dialog-body-more-below"),
                                            reserveSpace = ownScrollInfo != null || bodyScrollInfo != null,
                                        )
                                    }
                                }
                                FlowRow(
                                    Modifier.fillMaxWidth().testTag("omnidroid-dialog-actions")
                                        .onSizeChanged { actionsHeight = it.height },
                                    horizontalArrangement = Arrangement.spacedBy(OmnidroidDialogStyle.gap, Alignment.End),
                                    verticalArrangement = Arrangement.spacedBy(OmnidroidDialogStyle.gap),
                                ) {
                                    CompositionLocalProvider(
                                        LocalDialogActionRole provides DialogActionRole.Cancel,
                                        LocalDialogCancelFocus provides cancelFocus,
                                    ) {
                                        dismissButton?.invoke()
                                    }
                                    CompositionLocalProvider(LocalDialogActionRole provides
                                        if (destructive) DialogActionRole.Destructive else DialogActionRole.Confirm,
                                        LocalDialogCancelFocus provides cancelFocus,
                                    ) { confirmButton?.invoke() }
                                }
                            }
                            if (shortWindow) {
                                DialogScrollbar(
                                    panelScrollInfo,
                                    Modifier.align(Alignment.CenterEnd).padding(end = 6.dp)
                                        .testTag("omnidroid-dialog-panel-scrollbar"),
                                )
                            }
                        }
                        if (shortWindow && availableHeight >= OmnidroidDialogStyle.scrollCueHeight + OmnidroidDialogStyle.touchTarget) {
                            DialogMoreBelow(
                                panelScrollInfo?.canScrollForward == true,
                                Modifier.testTag("omnidroid-dialog-panel-more-below"),
                                reserveSpace = panelScrollInfo != null,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun dialogScrollInfo(state: ScrollState, viewport: Int): DialogScrollInfo? {
    if (viewport <= 0 || state.maxValue <= 0 || state.maxValue == Int.MAX_VALUE) return null
    return DialogScrollInfo(
        progress = state.value.toFloat() / state.maxValue,
        visibleFraction = viewport.toFloat() / (viewport.toFloat() + state.maxValue),
        canScrollForward = state.canScrollForward,
    )
}

/** A persistent track and thumb make scrolling discoverable even while idle. */
@Composable
private fun DialogScrollbar(info: DialogScrollInfo?, modifier: Modifier) {
    if (info == null || info.visibleFraction >= 1f) return
    val track = chromeColors.content.copy(alpha = 0.16f)
    val thumb = chromeColors.content.copy(alpha = 0.85f)
    Canvas(modifier.width(OmnidroidDialogStyle.scrollbarWidth).fillMaxHeight().padding(vertical = 4.dp)) {
        val radius = CornerRadius(size.width / 2f)
        drawRoundRect(track, cornerRadius = radius)
        val thumbHeight = (size.height * info.visibleFraction.coerceIn(0f, 1f))
            .coerceIn(minOf(OmnidroidDialogStyle.scrollbarMinThumb.toPx(), size.height), size.height)
        val top = (size.height - thumbHeight) * info.progress.coerceIn(0f, 1f)
        drawRoundRect(thumb, topLeft = Offset(0f, top), size = Size(size.width, thumbHeight), cornerRadius = radius)
    }
}

/** Reserved space below the viewport keeps the cue from covering text or actions. */
@Composable
private fun DialogMoreBelow(visible: Boolean, modifier: Modifier, reserveSpace: Boolean) {
    if (!visible && !reserveSpace) return
    // Keep the viewport stable at the end of scrolling. Removing this row would
    // resize it and repeatedly retrigger focused-control bring-into-view on small windows.
    val rowModifier = if (visible) modifier else Modifier.alpha(0f).clearAndSetSemantics { }
    Row(
        rowModifier.fillMaxWidth().heightIn(min = OmnidroidDialogStyle.scrollCueHeight),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.dialog_more_below),
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.labelMedium,
            color = chromeColors.content,
        )
        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp), tint = LibraryNeonGreen)
    }
}

/** Shared action renderer; its default role comes from the confirm/cancel slot. */
@Composable
fun OmnidroidDialogButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: DialogActionRole = LocalDialogActionRole.current,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(OmnidroidDialogStyle.buttonRadius)
    val cancelFocus = LocalDialogCancelFocus.current
    var focused by remember { mutableStateOf(false) }
    val buttonModifier = modifier
        .then(if (role == DialogActionRole.Cancel && cancelFocus != null) Modifier.focusRequester(cancelFocus) else Modifier)
        .heightIn(min = OmnidroidDialogStyle.buttonHeight)
        .onFocusChanged { focused = it.isFocused }
    val padding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
    if (role == DialogActionRole.Cancel || role == DialogActionRole.Alternative) {
        OutlinedButton(
            onClick = onClick, modifier = buttonModifier, enabled = enabled, shape = shape,
            border = BorderStroke(
                if (focused) 2.dp else 1.dp,
                if (focused) MaterialTheme.colorScheme.primary else chromeColors.panelBorder,
            ),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = chromeColors.content),
            contentPadding = padding,
        ) { content() }
    } else {
        val destructive = role == DialogActionRole.Destructive
        Button(
            onClick = onClick, modifier = buttonModifier, enabled = enabled, shape = shape,
            border = if (focused) BorderStroke(2.dp, Color.White) else null,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (destructive) OmnidroidDialogStyle.destructiveContainer else MaterialTheme.colorScheme.primary,
                contentColor = if (destructive) OmnidroidDialogStyle.onDestructiveContainer else MaterialTheme.colorScheme.onPrimary,
            ),
            contentPadding = padding,
        ) { content() }
    }
}
