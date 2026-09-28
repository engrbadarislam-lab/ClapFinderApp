package com.clapfinder.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.clapfinder.app.R
import com.clapfinder.app.ui.theme.AccelerateDecelerate
import com.clapfinder.app.ui.theme.CF
import com.clapfinder.app.ui.theme.CardTitle
import com.clapfinder.app.util.Prefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
//  Small helpers
// ─────────────────────────────────────────────────────────────────────────────

fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Status / navigation bar icon colour for one screen — replaces the old per-activity
 * `windowLightStatusBar` theme flags (dark = dark icons, like Theme.ClapFinder).
 */
@Composable
fun SystemBarIcons(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(dark) {
        view.context.findActivity()?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = dark
                isAppearanceLightNavigationBars = dark
            }
        }
        onDispose { }
    }
}

/** Click with no ripple — like the old Views that only had a click listener. */
fun Modifier.plainClickable(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
}

/** ?attr/selectableItemBackgroundBorderless */
fun Modifier.borderlessClickable(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = ripple(bounded = false),
        onClick = onClick
    )
}

/** android:layout_marginTop="-Xdp" — pulls this view up over the previous one, keeping its bottom edge. */
fun Modifier.negativeTopMargin(margin: Dp): Modifier = layout { measurable, constraints ->
    val m = margin.roundToPx()
    val extended = if (constraints.hasBoundedHeight) {
        constraints.copy(minHeight = constraints.minHeight + m, maxHeight = constraints.maxHeight + m)
    } else constraints
    val placeable = measurable.measure(extended)
    layout(placeable.width, (placeable.height - m).coerceAtLeast(0)) {
        placeable.place(0, -m)
    }
}

/**
 * Bumps every time a preference changes or the screen resumes, so a screen can
 * re-read [Prefs] exactly like the old Activities did in onResume().
 */
@Composable
fun rememberPrefsVersion(prefs: Prefs): Int {
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> version++ }
        prefs.registerListener(listener)
        onDispose { prefs.unregisterListener(listener) }
    }
    LifecycleResumeEffect(prefs) {
        version++
        onPauseOrDispose { }
    }
    return version
}

// ─────────────────────────────────────────────────────────────────────────────
//  Building blocks shared by the screens
// ─────────────────────────────────────────────────────────────────────────────

/** ImageView: no tint → the vector's own android:tint, like before; [tint] = android:tint (SRC_IN). */
@Composable
fun CfIcon(@DrawableRes id: Int, size: Dp, modifier: Modifier = Modifier, tint: Color? = null) {
    Image(
        painter = painterResource(id),
        contentDescription = null,
        modifier = modifier.size(size),
        colorFilter = tint?.let { ColorFilter.tint(it) }
    )
}

/** Back arrow + white title bar used by Sound / Setting / How to use. */
@Composable
fun BackTopBar(
    title: String,
    backSize: Dp,
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CfIcon(
            id = R.drawable.ic_back,
            size = backSize,
            tint = CF.White,
            modifier = Modifier.borderlessClickable(onBack)
        )
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
            color = CF.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        trailing?.invoke()
    }
}

/** Card row with icon + title + switch (Flash Mode / Vibration Mode). */
@Composable
fun SwitchCardRow(
    @DrawableRes icon: Int,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(CF.Card, CF.CardShape)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CfIcon(icon, 24.dp)
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
            style = CardTitle
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Clickable card row with icon + title + chevron (Whistle Finder / More Setting). */
@Composable
fun ChevronCardRow(
    @DrawableRes icon: Int,
    iconTint: Color?,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CF.CardShape)
            .background(CF.Card)
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CfIcon(icon, 24.dp, tint = iconTint)
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
            style = CardTitle
        )
        CfIcon(R.drawable.ic_chevron, 22.dp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Activate ring (Home + Whistle)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The 196dp activate button: bg_activate_ring / bg_activate_ring_active and,
 * while listening on Home, the two radiating "sonar" rings behind it.
 */
@Composable
fun ActivateRing(
    running: Boolean,
    pulsing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .size(196.dp)
            .plainClickable(onClick),
        contentAlignment = Alignment.Center
    ) {
        if (pulsing) PulseRings()
        Box(
            Modifier
                .fillMaxSize()
                .background(if (running) CF.GreenSoft else CF.White, CircleShape)
                .border(10.dp, if (running) CF.GreenPrimaryDark else CF.TextSecondary, CircleShape)
        )
        icon()
    }
}

/** Two staggered rings: scale 1→1.6, alpha 0.45→0, 1.8s, second one starts 0.9s later. */
@Composable
private fun PulseRings() {
    val ring1 = remember { Animatable(0f) }
    val ring2 = remember { Animatable(0f) }
    var ring2Started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        launch {
            ring1.animateTo(
                1f,
                infiniteRepeatable(tween(1800, easing = AccelerateDecelerate), RepeatMode.Restart)
            )
        }
        launch {
            delay(900)
            ring2Started = true
            ring2.animateTo(
                1f,
                infiniteRepeatable(tween(1800, easing = AccelerateDecelerate), RepeatMode.Restart)
            )
        }
    }
    PulseCircle { ring1.value }
    if (ring2Started) PulseCircle { ring2.value }
}

@Composable
private fun PulseCircle(progress: () -> Float) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = progress()
                val s = 1f + 0.6f * p
                scaleX = s
                scaleY = s
                alpha = 0.45f * (1f - p)
            }
            .background(CF.Black, CircleShape)
    )
}

/** Power icon "breathing": 1→1.08, 1s, reverse, only while [enabled]. */
@Composable
fun rememberBreathingScale(enabled: Boolean): State<Float> {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(enabled) {
        if (enabled) {
            scale.animateTo(
                1.08f,
                infiniteRepeatable(tween(1000, easing = AccelerateDecelerate), RepeatMode.Reverse)
            )
        } else {
            scale.snapTo(1f)
        }
    }
    return scale.asState()
}

// ─────────────────────────────────────────────────────────────────────────────
//  Slider that looks and behaves like the old SeekBar / Material Slider
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Hand-drawn slider (20dp round thumb, flat rounded track) — the same look as the
 * old seek_progress / seek_thumb SeekBar and the Material Slider, and independent
 * of Material3's Slider API, which changes shape between versions.
 * Like SeekBar inside a ScrollView: a horizontal drag moves it, a tap jumps to
 * the tapped spot, a vertical swipe still scrolls the page.
 */
@Composable
fun CfSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    trackHeight: Dp,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    thumbColor: Color = activeColor,
    tickColor: Color? = null,
    showValueLabel: Boolean = false,
) {
    val onChange by rememberUpdatedState(onValueChange)
    val thumbRadiusPx = with(LocalDensity.current) { 10.dp.toPx() }
    var widthPx by remember { mutableIntStateOf(0) }
    var touching by remember { mutableStateOf(false) }
    val span = valueRange.endInclusive - valueRange.start
    val fraction = if (span > 0f) ((value - valueRange.start) / span).coerceIn(0f, 1f) else 0f

    /** x (px inside the slider) → value, snapped to [steps] like the old step sizes. */
    fun valueAt(x: Float): Float {
        val usable = (widthPx - 2f * thumbRadiusPx).coerceAtLeast(1f)
        var f = ((x - thumbRadiusPx) / usable).coerceIn(0f, 1f)
        if (steps > 0) {
            val n = steps + 1
            f = (f * n).roundToInt() / n.toFloat()
        }
        return valueRange.start + f * span
    }

    Box(
        modifier = modifier
            .height(48.dp)
            .onSizeChanged { widthPx = it.width }
            // TalkBack can read and change it, like the old SeekBar / Slider
            .semantics(mergeDescendants = true) {
                progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange, steps)
                setProgress { target ->
                    onChange(target.coerceIn(valueRange.start, valueRange.endInclusive))
                    true
                }
            }
            .pointerInput(valueRange, steps) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    touching = true
                    val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                        change.consume()
                    }
                    if (drag != null) {
                        onChange(valueAt(drag.position.x))
                        horizontalDrag(drag.id) { change ->
                            change.consume()
                            onChange(valueAt(change.position.x))
                        }
                    } else {
                        // Tap: released without dragging and not taken by the scroll view
                        val up = currentEvent.changes.firstOrNull { it.id == down.id }
                        if (up != null && !up.pressed && !up.isConsumed) {
                            onChange(valueAt(up.position.x))
                        }
                    }
                    touching = false
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cy = size.height / 2f
            val h = trackHeight.toPx()
            val r = h / 2f
            val left = thumbRadiusPx
            val w = (size.width - 2f * thumbRadiusPx).coerceAtLeast(0f)
            drawRoundRect(
                color = inactiveColor,
                topLeft = Offset(left, cy - r),
                size = Size(w, h),
                cornerRadius = CornerRadius(r, r)
            )
            if (fraction > 0f) {
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(left, cy - r),
                    size = Size(w * fraction, h),
                    cornerRadius = CornerRadius(r, r)
                )
            }
            if (tickColor != null && steps > 0) {
                val n = steps + 1
                for (i in 0..n) {
                    val x = left + w * i / n
                    if (x <= left + w * fraction) {
                        drawCircle(color = tickColor, radius = 1.5.dp.toPx(), center = Offset(x, cy))
                    }
                }
            }
            drawCircle(color = thumbColor, radius = thumbRadiusPx, center = Offset(left + w * fraction, cy))
        }

        if (showValueLabel && touching) {
            // Value bubble above the thumb while touching, like the Material Slider label
            Text(
                text = value.roundToInt().toString(),
                modifier = Modifier
                    .layout { measurable, _ ->
                        val p = measurable.measure(Constraints())
                        val thumbX = thumbRadiusPx +
                            (widthPx - 2f * thumbRadiusPx).coerceAtLeast(0f) * fraction
                        layout(0, 0) {
                            p.place((thumbX - p.width / 2f).roundToInt(), -p.height + 4.dp.roundToPx())
                        }
                    }
                    .background(Color(0xFF313033), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                color = CF.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
