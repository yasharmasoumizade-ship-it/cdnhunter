package com.cdnhunter.app.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

/** What the OS reports about the live VPN interface. */
data class VpnLinkInfo(
    val interfaceName: String?,
    val addresses: List<String>,
    /** Whether Android's own connectivity check has validated the VPN network (null if not reported). */
    val osValidated: Boolean?,
)

/**
 * Reads facts about the network from the OS instead of assuming them.
 *
 * Deliberately built on [ConnectivityManager] / `LinkProperties` rather than
 * `NetworkInterface.getNetworkInterfaces()`: since Android 11 that call can fail or return
 * a partial list for ordinary apps. Everything here returns null / empty when the OS will
 * not say, and callers treat that as "unknown", never as failure.
 */
object TunnelInspector {

    /** The VPN network's link properties, preferring the one that carries [expectedAddress]. */
    fun vpnLink(ctx: Context, expectedAddress: String): VpnLinkInfo? {
        return try {
            val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return null
            var fallback: VpnLinkInfo? = null
            @Suppress("DEPRECATION")
            for (network in cm.allNetworks) {
                val caps = cm.getNetworkCapabilities(network) ?: continue
                if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
                val lp = cm.getLinkProperties(network) ?: continue
                val addrs = lp.linkAddresses.mapNotNull { it.address?.hostAddress?.substringBefore('%') }
                val info = VpnLinkInfo(
                    interfaceName = lp.interfaceName,
                    addresses = addrs,
                    osValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                )
                if (expectedAddress in addrs) return info
                if (fallback == null) fallback = info
            }
            fallback
        } catch (_: Exception) {
            null
        }
    }

    /** This device's IPv4 address on the physical (non-VPN) network that has internet, if the OS reports one. */
    fun localAddress(ctx: Context): String? {
        return try {
            val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return null
            @Suppress("DEPRECATION")
            for (network in cm.allNetworks) {
                val caps = cm.getNetworkCapabilities(network) ?: continue
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
                val lp = cm.getLinkProperties(network) ?: continue
                lp.linkAddresses
                    .map { it.address }
                    .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && !it.isLinkLocalAddress }
                    ?.hostAddress
                    ?.let { return it }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /** True if any non-VPN network with internet capability exists right now. */
    fun hasUnderlyingNetwork(ctx: Context): Boolean {
        return try {
            val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return true
            @Suppress("DEPRECATION")
            cm.allNetworks.any { n ->
                val c = cm.getNetworkCapabilities(n) ?: return@any false
                !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                    c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        } catch (_: Exception) {
            true // cannot tell: do not report the network as down on a guess
        }
    }

    private val RESOLVER = Executors.newCachedThreadPool(object : ThreadFactory {
        private var n = 0
        @Synchronized override fun newThread(r: Runnable): Thread =
            Thread(r, "tunnel-resolver-${n++}").apply { isDaemon = true }
    })

    /** The server's resolved address, bounded by [timeoutMs]; null if it cannot be resolved in time. */
    fun resolveServerIp(host: String, timeoutMs: Int = 3_000): String? {
        val h = host.trim().removePrefix("[").removeSuffix("]")
        if (h.isEmpty()) return null
        if (h.contains(':') || h.all { it.isDigit() || it == '.' }) return h
        val f = RESOLVER.submit(Callable<String?> { InetAddress.getByName(h).hostAddress })
        return try {
            f.get(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            f.cancel(true)
            null
        }
    }
}
