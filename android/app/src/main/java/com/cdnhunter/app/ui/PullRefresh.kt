package com.cdnhunter.app.ui

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.ln

/** Pulling past this much (px of content offset) and letting go starts a refresh. */
internal val PullThreshold = 60.dp

/** Where the content rests, and the arrow turns, while a refresh runs: the gap above the first row. */
internal val PullHold = 48.dp

/** The most the content can ever be dragged down. The arrow lives in this gap and never leaves it. */
internal val PullMax = 96.dp

/**
 * The drag's resistance: how far the content has moved for a finger that has moved [raw]. It starts
 * close to the finger (slope [SLOPE]) and flattens towards [max] -- a deterministic exponential, no
 * spring. [inverse] turns an offset back into finger distance so a drag can resume from wherever an
 * animation left the content.
 */
internal object PullMath {
    const val SLOPE = 0.7f

    fun offset(raw: Float, max: Float): Float = max * (1f - exp(-SLOPE * raw.coerceAtLeast(0f) / max))

    fun inverse(offset: Float, max: Float): Float {
        val p = offset.coerceIn(0f, max * 0.999f)
        return -max / SLOPE * ln(1f - p / max)
    }
}

/**
 * Pull-to-refresh as one piece of state: the distance the content has been pulled down ([pull], px)
 * is the only thing the gesture owns, and everything on screen is derived from it.
 *
 *   pull distance -> content translation + arrow position -> threshold -> release
 *                 -> refresh (content settles at [holdPx]) or cancel (content returns to 0)
 *
 * The REAL work stays outside: [onRefresh] starts it and [refreshing] says when it is running (the
 * app's `refreshingPings`). While it runs, further pulls are ignored, so there is exactly one
 * refresh at a time; when it ends the content eases back to rest. A gesture only ever starts at the
 * top of the list, because it consumes only the scroll the list could not.
 *
 * Every move on release is a fixed-duration cubic-bezier tween -- no springs, no overshoot.
 */
@Stable
internal class PullRefreshState(
    private val scope: CoroutineScope,
    private val thresholdPx: Float,
    private val holdPx: Float,
    private val maxPx: Float,
    private val reduceMotion: () -> Boolean,
    private val isRefreshing: () -> Boolean,
    private val onRefresh: () -> Unit,
    private val onThresholdReached: () -> Unit,
) {
    /** How far the content is currently pulled down, px. 0 at rest. */
    var pull by mutableFloatStateOf(0f)
        private set

    /** 0..1+ share of the way to the threshold. */
    val progress: Float get() = pull / thresholdPx

    private var job: Job? = null
    private var armed = false

    private fun setPull(value: Float) {
        pull = value
        if (!armed && value >= thresholdPx) {
            armed = true
            onThresholdReached()
        } else if (armed && value < thresholdPx * 0.9f) {
            armed = false
        }
    }

    private suspend fun settle(target: Float, ms: Int, easing: Easing) {
        val from = pull
        if (from == target) return
        if (reduceMotion()) {
            pull = target
            return
        }
        animate(from, target, animationSpec = tween(ms, easing = easing)) { v, _ -> pull = v }
    }

    private fun release() {
        armed = false
        job?.cancel()
        val start = pull >= thresholdPx && !isRefreshing()
        job = scope.launch {
            if (start) {
                onRefresh()
                // Up to the refresh position at the top. By the time this lands the work's own flag
                // is visible, so the check below only fires if the work ended (or never began).
                settle(holdPx, Motion.Standard, Motion.EaseOut)
                if (!isRefreshing()) settle(0f, Motion.Emphasis, Motion.EaseInOut)
            } else {
                settle(0f, Motion.Emphasis, Motion.EaseInOut)
            }
        }
    }

    /** The work started or finished: hold the content open while it runs, let it rest when it is done. */
    fun onRefreshingChanged(refreshing: Boolean) {
        if (refreshing) {
            if (pull != holdPx) {
                job?.cancel()
                job = scope.launch { settle(holdPx, Motion.Standard, Motion.EaseOut) }
            }
        } else if (pull != 0f) {
            job?.cancel()
            job = scope.launch { settle(0f, Motion.Emphasis, Motion.EaseInOut) }
        }
    }

    val connection = object : NestedScrollConnection {
        // Finger moving back up while the content is pulled down: take it back before the list scrolls.
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.Drag || isRefreshing() || pull <= 0f || available.y >= 0f) return Offset.Zero
            job?.cancel()
            val raw = PullMath.inverse(pull, maxPx)
            val used = available.y.coerceAtLeast(-raw)
            setPull(PullMath.offset(raw + used, maxPx))
            return Offset(0f, used)
        }

        // Finger moving down with the list already at its top: the scroll the list could not use.
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.Drag || isRefreshing() || available.y <= 0f) return Offset.Zero
            job?.cancel()
            val raw = PullMath.inverse(pull, maxPx) + available.y
            setPull(PullMath.offset(raw, maxPx))
            return Offset(0f, available.y)
        }

        // The finger lifts: refresh or cancel, and keep the list from flinging.
        override suspend fun onPreFling(available: Velocity): Velocity {
            if (isRefreshing() || pull <= 0f) return Velocity.Zero
            release()
            return Velocity(0f, available.y)
        }
    }
}

@Composable
internal fun rememberPullRefreshState(refreshing: Boolean, onRefresh: () -> Unit): PullRefreshState {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val reduce by rememberUpdatedState(appReduceMotion())
    val refreshingNow by rememberUpdatedState(refreshing)
    val onRefreshNow by rememberUpdatedState(onRefresh)
    val state = remember(density, scope) {
        PullRefreshState(
            scope = scope,
            thresholdPx = with(density) { PullThreshold.toPx() },
            holdPx = with(density) { PullHold.toPx() },
            maxPx = with(density) { PullMax.toPx() },
            reduceMotion = { reduce },
            isRefreshing = { refreshingNow },
            onRefresh = { onRefreshNow() },
            onThresholdReached = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
        )
    }
    LaunchedEffect(refreshing) { state.onRefreshingChanged(refreshing) }
    return state
}

/**
 * The arrow, in the gap the pull opens above the first row. Its centre is always half the pull
 * distance, so it can never be lower than the gap it sits in: it comes in from the top edge as the
 * content moves down, stays centred in the refresh gap while the work runs, and leaves upward as the
 * content returns. It is moved in the graphics layer, so a drag never re-lays anything out.
 */
@Composable
internal fun PullRefreshIndicator(state: PullRefreshState, refreshing: Boolean, modifier: Modifier = Modifier) {
    val holdPx = with(LocalDensity.current) { PullHold.toPx() }
    RefreshArrow(
        progress = state.progress,
        refreshing = refreshing,
        modifier = modifier.graphicsLayer {
            translationY = (state.pull - size.height) / 2f
            alpha = (state.pull / (holdPx * 0.7f)).coerceIn(0f, 1f)
        },
    )
}
