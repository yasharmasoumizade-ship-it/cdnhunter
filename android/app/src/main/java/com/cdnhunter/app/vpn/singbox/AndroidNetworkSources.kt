package com.cdnhunter.app.vpn.singbox

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import java.net.NetworkInterface

/**
 * The underlying (non-VPN) network, as libbox's default-interface monitor wants it. DEVICE-ONLY code: it needs a real
 * ConnectivityManager, so it is not covered by unit tests — the logic that consumes it is (AndroidSingBoxPlatform).
 */
class AndroidDefaultInterfaceSource(private val cm: ConnectivityManager) : DefaultInterfaceSource {
    private var callback: ConnectivityManager.NetworkCallback? = null

    @Synchronized
    override fun start(onChange: (DefaultInterface) -> Unit) {
        stopLocked()
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) // never our own tunnel as the "default"
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { onChange(describe(network)) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { onChange(describe(network)) }
            override fun onLost(network: Network) { onChange(DefaultInterface.NONE) }
        }
        callback = cb
        if (Build.VERSION.SDK_INT >= 31) cm.registerBestMatchingNetworkCallback(request, cb, android.os.Handler(android.os.Looper.getMainLooper()))
        else cm.registerNetworkCallback(request, cb) // API < 31: reports every matching network; verify default-selection on a device
    }

    @Synchronized
    override fun stop() = stopLocked()

    private fun stopLocked() {
        callback?.let { try { cm.unregisterNetworkCallback(it) } catch (_: Exception) { } }
        callback = null
    }

    private fun describe(network: Network): DefaultInterface {
        val name = cm.getLinkProperties(network)?.interfaceName ?: return DefaultInterface.NONE
        val index = try { NetworkInterface.getByName(name)?.index ?: -1 } catch (_: Exception) { -1 }
        if (index < 0) return DefaultInterface.NONE
        val metered = cm.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false
        return DefaultInterface(name, index, metered, false)
    }
}

class AndroidInterfaceLister(private val cm: ConnectivityManager) : InterfaceLister {
    @Suppress("DEPRECATION")
    override fun list(): List<NetIf> {
        val extra = HashMap<String, Triple<Int, List<String>, Pair<List<String>, Boolean>>>()
        for (n in cm.allNetworks) {
            val lp = cm.getLinkProperties(n) ?: continue
            val name = lp.interfaceName ?: continue
            val caps = cm.getNetworkCapabilities(n)
            val type = when {
                caps == null -> IfType.OTHER
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> IfType.WIFI
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> IfType.CELLULAR
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> IfType.ETHERNET
                else -> IfType.OTHER
            }
            val gateways = lp.routes.mapNotNull { it.gateway?.hostAddress }.distinct()
            val metered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false
            extra[name] = Triple(type, lp.dnsServers.mapNotNull { it.hostAddress }, Pair(gateways, metered))
        }
        val out = ArrayList<NetIf>()
        val all = (try { NetworkInterface.getNetworkInterfaces() } catch (_: Exception) { null }) ?: return out
        for (ni in all) {
            val e = extra[ni.name]
            out += NetIf(
                index = ni.index,
                mtu = try { ni.mtu } catch (_: Exception) { 0 },
                name = ni.name,
                addresses = ni.interfaceAddresses.mapNotNull { a -> a.address?.hostAddress?.substringBefore('%')?.let { "$it/${a.networkPrefixLength}" } },
                flags = LinuxIfFlags.of(
                    up = try { ni.isUp } catch (_: Exception) { false },
                    loopback = try { ni.isLoopback } catch (_: Exception) { false },
                    pointToPoint = try { ni.isPointToPoint } catch (_: Exception) { false },
                    multicast = try { ni.supportsMulticast() } catch (_: Exception) { false },
                ),
                type = e?.first ?: IfType.OTHER,
                dns = e?.second ?: emptyList(),
                gateway = e?.third?.first ?: emptyList(),
                metered = e?.third?.second ?: false,
            )
        }
        return out
    }
}
