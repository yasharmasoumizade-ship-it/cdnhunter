package com.cdnhunter.app.vpn

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Minimal shim of CdnVpnService to satisfy compile-time references from UI code.
 *
 * This file restores the public surface the rest of the app expects (static
 * fields/methods). It is intentionally small and non-functional — it keeps the
 * app compiling while the real service implementation is recovered or restored
 * from history. Replace with the full service later.
 */
object CdnVpnService {
    // cumulative counters (updated by the real service implementation)
    val downloadBytes = AtomicLong(0L)
    val uploadBytes = AtomicLong(0L)

    // runtime flags used by UI
    val isRunning = AtomicBoolean(false)
    val isConnecting = AtomicBoolean(false)

    // simple exit location metadata placeholders
    @Volatile
    var exitCountryCode: String = ""

    @Volatile
    var exitCity: String = ""

    @Volatile
    var exitGeoConfigId: String = ""

    // Start the VPN. Minimal behaviour: flip connecting->running flags.
    fun start(ctx: Context) {
        isConnecting.set(true)
        // In a real implementation this would bind/start the Android Service
        // and initiate mihomo. Here we emulate an immediate start.
        isRunning.set(true)
        isConnecting.set(false)
    }

    // Stop the VPN.
    fun stop(ctx: Context) {
        isRunning.set(false)
        isConnecting.set(false)
    }

    // Protected helper used by Go bridge in the real implementation. No-op here.
    fun protectFd(fd: Int): Boolean = true
}
