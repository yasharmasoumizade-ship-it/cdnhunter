package com.cdnhunter.app.ui

// ── SMART MODE ────────────────────────────────────────────────────────────────
// Two ways to answer "which server does the power button use?"
//
//   MANUAL — the one the user tapped in the browse list. What the app has always
//            done, and still the default for anyone upgrading.
//   SMART  — whichever saved server currently measures best, chosen for the app.
//
// "Best" is deliberately not "lowest last ping". A single TCP-connect sample says
// almost nothing: a server that answers in 40ms once and times out on the next
// three probes is worse than a steady 90ms one, and picking on the last sample
// alone would hop between servers every few seconds. So this keeps a short rolling
// window of the samples the Home ping monitor already takes (one per server every
// 3s, see AppScreen's monitor loop) and scores the window rather than the sample:
// how fast it typically answers, how much that varies, and how often it doesn't
// answer at all.
//
// Nothing here starts work of its own. It is fed from the ping loop that already
// runs, so Smart mode costs no extra probes, no extra sockets and no extra battery.

/** Which server the power button acts on, and where that choice comes from. */
internal enum class ConnectMode(val label: String) {
    /** The app picks the best-measuring server. */
    SMART("Smart"),

    /** The user picks, from the browse list. */
    MANUAL("Manual"),
}

/** Maps [com.cdnhunter.app.vpn.AppSettings.connectMode]'s stored string onto the enum. */
internal fun connectModeOf(setting: String): ConnectMode =
    if (setting == com.cdnhunter.app.vpn.AppSettings.MODE_SMART) ConnectMode.SMART else ConnectMode.MANUAL

/**
 * What one server's recent samples add up to. All three numbers come from the same
 * window, so they can be compared against each other without weighting for age.
 *
 * [medianMs] is the median of the samples that answered — median, not mean, so one
 * outlier probe can't drag a good server down or a bad one up. [jitterMs] is the
 * mean absolute deviation from it: how consistent that latency is. [failures] is
 * how many probes in the window got no answer at all.
 */
internal data class ServerQuality(
    val samples: Int,
    val failures: Int,
    val medianMs: Int,
    val jitterMs: Int,
) {
    val failureRate: Float get() = if (samples == 0) 1f else failures.toFloat() / samples

    /**
     * Whether this server is worth offering at all: it has to have answered at
     * least once, and at least half the time.
     */
    val eligible: Boolean get() = samples - failures > 0 && failureRate <= 0.5f

    /**
     * Lower is better, in milliseconds-equivalent, so latency stays the unit the
     * whole score is read in.
     *
     * Jitter counts 1.5x its own size — a server that swings ±40ms is worse to use
     * than one that sits steadily 40ms further away, because the swing is what
     * shows up as a stalling page. A dropped probe costs [FAILURE_PENALTY_MS] of
     * the rate it happens at, which is enough that one timeout in twelve outweighs
     * a 100ms latency advantage: reliability first, speed second.
     */
    val score: Float get() = medianMs + jitterMs * 1.5f + failureRate * FAILURE_PENALTY_MS

    companion object {
        const val FAILURE_PENALTY_MS = 1_500f
    }
}

/**
 * A rolling window of ping samples per server.
 *
 * Not a Compose state holder on purpose: the ping monitor writes one sample per
 * server every 3s, and with 50 saved servers that would be ~17 recompositions a
 * second for a number the screen doesn't show. Recording is a plain side effect;
 * the pick is recomputed on its own slower cadence (see AppScreen) and only
 * published when the chosen server actually changes.
 */
internal class ServerQualityTracker(private val window: Int = WINDOW) {
    private val samples = HashMap<String, ArrayDeque<Int>>()

    /** One probe result, exactly as [measurePingMs] reports it: negative = no answer. */
    fun record(id: String, pingMs: Int) {
        val q = samples.getOrPut(id) { ArrayDeque(window) }
        q.addLast(pingMs)
        while (q.size > window) q.removeFirst()
    }

    /** Drops windows for servers that no longer exist, so deleting one frees it. */
    fun retain(ids: Set<String>) {
        samples.keys.retainAll(ids)
    }

    fun quality(id: String): ServerQuality? {
        val q = samples[id]?.toList() ?: return null
        if (q.isEmpty()) return null
        val ok = q.filter { it >= 0 }.sorted()
        if (ok.isEmpty()) return ServerQuality(samples = q.size, failures = q.size, medianMs = 0, jitterMs = 0)
        val median = ok[ok.size / 2]
        val jitter = (ok.sumOf { kotlin.math.abs(it - median) }.toFloat() / ok.size).toInt()
        return ServerQuality(
            samples = q.size,
            failures = q.size - ok.size,
            medianMs = median,
            jitterMs = jitter,
        )
    }

    companion object {
        /** 12 samples at one probe per 3s — a 36 second view of each server. */
        const val WINDOW = 12
    }
}

/**
 * How much better a challenger has to measure before Smart mode moves off the
 * server it already chose. Without this the pick flips on noise: two servers a few
 * milliseconds apart would trade places every time the window slid, and the connect
 * bar would flicker between two countries while the user is looking at it.
 */
private const val SWITCH_MARGIN = 0.85f

/**
 * Smart mode's choice: the eligible server with the lowest [ServerQuality.score].
 *
 * [preferId] is the server already chosen (or connected). It keeps its place unless
 * a challenger beats it by more than [SWITCH_MARGIN], so the pick is stable while
 * still moving when something genuinely better shows up.
 *
 * When nothing has a usable window yet — a fresh install, every server still on its
 * first probe — this falls back to the plainest signal available, the last measured
 * ping, and only returns null when there is nothing measured at all.
 */
internal fun pickBestConfig(
    configs: List<SavedConfig>,
    tracker: ServerQualityTracker,
    preferId: String? = null,
): SavedConfig? {
    if (configs.isEmpty()) return null
    val scored = configs.mapNotNull { cfg ->
        val q = tracker.quality(cfg.id) ?: return@mapNotNull null
        if (!q.eligible) null else cfg to q.score
    }
    if (scored.isEmpty()) {
        // No window worth reading yet: fastest last-known ping, else nothing.
        return configs.filter { it.pingMs >= 0 }.minByOrNull { it.pingMs }
    }
    val best = scored.minByOrNull { it.second } ?: return null
    val held = scored.firstOrNull { it.first.id == preferId }
    if (held != null && best.second >= held.second * SWITCH_MARGIN) return held.first
    return best.first
}
