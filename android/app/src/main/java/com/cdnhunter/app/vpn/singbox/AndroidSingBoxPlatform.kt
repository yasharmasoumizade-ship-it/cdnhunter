package com.cdnhunter.app.vpn.singbox

import com.cdnhunter.app.core.Redactor
import com.cdnhunter.app.core.routing.TunSetupException
import com.cdnhunter.mihomo.libbox.BridgeOptions
import com.cdnhunter.mihomo.libbox.BridgeSession
import com.cdnhunter.mihomo.libbox.ConnectionOwner
import com.cdnhunter.mihomo.libbox.InterfaceUpdateListener
import com.cdnhunter.mihomo.libbox.LocalDNSTransport
import com.cdnhunter.mihomo.libbox.NeighborUpdateListener
import com.cdnhunter.mihomo.libbox.NetworkInterfaceIterator
import com.cdnhunter.mihomo.libbox.PlatformInterface
import com.cdnhunter.mihomo.libbox.PlatformUser
import com.cdnhunter.mihomo.libbox.RoutePrefixIterator
import com.cdnhunter.mihomo.libbox.ShellSession
import com.cdnhunter.mihomo.libbox.StringIterator
import com.cdnhunter.mihomo.libbox.TunOptions
import com.cdnhunter.mihomo.libbox.WIFIState
import com.cdnhunter.mihomo.libbox.NetworkInterface as LibboxNetworkInterface
import com.cdnhunter.mihomo.libbox.Notification as LibboxNotification

/**
 * sing-box 1.14.2 libbox PlatformInterface, all methods, for the APP-OWNED TUN architecture.
 *
 * Used (and why):
 *  - openTun: validates libbox's TunOptions against the TUN the service already established and LENDS that fd
 *    (libbox dup()s it itself; see [TunLender]). It builds no VpnService.Builder and changes no route/kill-switch.
 *  - autoDetectInterfaceControl / usePlatformAutoDetectInterfaceControl: every engine socket goes through protect().
 *  - start/closeDefaultInterfaceMonitor, getInterfaces, registerMyInterface: underlying-network awareness.
 *  - localDNSTransport = null: allowed (libbox registers its platform transport only when non-null). The sing-box
 *    config must therefore NOT use the "local" DNS server type; it would not go through Android's resolver.
 *
 * Refused loudly (UnsupportedOperationException — the app's config never enables them, so a call means a config
 * error): shell, SFTP/SSH, user lookup, bridge, neighbor monitor, connection-owner lookup, Tailscale, WIFI state.
 * No Android classes are referenced here, so the class loads in JVM unit tests.
 */
class AndroidSingBoxPlatform(
    private val tun: TunLender,
    private val protector: SocketProtector,
    private val defaultInterface: DefaultInterfaceSource,
    private val interfaces: InterfaceLister,
    private val log: (String) -> Unit = {},
) : PlatformInterface {

    @Volatile var myTunName: String? = null
        private set

    private fun d(msg: String) = log(Redactor.redact(msg))

    // ---- TUN -----------------------------------------------------------------------------------

    override fun openTun(options: TunOptions): Int {
        val expectation = tun.expectation() ?: throw TunSetupException("the service has no TUN established")
        TunValidator.validate(project(options), expectation)
        val fd = tun.lend()
        if (fd < 0) throw TunSetupException("the service's TUN is closed")
        d("openTun: lent the service TUN to sing-box")
        return fd
    }

    override fun registerMyInterface(name: String?) {
        myTunName = name
        d("registerMyInterface")
    }

    private fun project(o: TunOptions) = TunSpec(
        mtu = o.getMTU(),
        inet4 = prefixes(o.getInet4Address()),
        inet6 = prefixes(o.getInet6Address()),
        httpProxyEnabled = o.isHTTPProxyEnabled(),
        includePackage = strings(o.getIncludePackage()),
        excludePackage = strings(o.getExcludePackage()),
    )

    private fun prefixes(it: RoutePrefixIterator?): List<String> {
        val out = ArrayList<String>()
        if (it != null) while (it.hasNext()) out += it.next().string()
        return out
    }

    private fun strings(it: StringIterator?): List<String> {
        val out = ArrayList<String>()
        if (it != null) while (it.hasNext()) out += it.next()
        return out
    }

    // ---- sockets / interfaces ------------------------------------------------------------------

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

    override fun autoDetectInterfaceControl(fd: Int) {
        if (!protector.protect(fd)) throw IllegalStateException("VpnService.protect failed for an engine socket")
    }

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        defaultInterface.start { di ->
            try {
                listener.updateDefaultInterface(di.name, di.index, di.expensive, di.constrained)
            } catch (e: Exception) {
                d("updateDefaultInterface failed: ${e.javaClass.simpleName}")
            }
        }
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        defaultInterface.stop()
    }

    override fun getInterfaces(): NetworkInterfaceIterator {
        val items = interfaces.list()
        return object : NetworkInterfaceIterator {
            private var i = 0
            override fun hasNext(): Boolean = i < items.size
            override fun next(): LibboxNetworkInterface {
                val n = items[i++]
                val out = LibboxNetworkInterface()
                out.setIndex(n.index)
                out.setMTU(n.mtu)
                out.setName(n.name)
                out.setAddresses(ListStringIterator(n.addresses))
                out.setFlags(n.flags)
                out.setType(n.type)
                out.setDNSServer(ListStringIterator(n.dns))
                out.setGateway(ListStringIterator(n.gateway))
                out.setMetered(n.metered)
                return out
            }
        }
    }

    override fun clearDNSCache() { d("clearDNSCache: nothing to clear (the app does not cache outside the engine)") }

    override fun includeAllNetworks(): Boolean = false
    override fun underNetworkExtension(): Boolean = false
    override fun useProcFS(): Boolean = false
    override fun usePlatformBridge(): Boolean = false
    override fun usePlatformShell(): Boolean = false

    override fun localDNSTransport(): LocalDNSTransport? = null

    override fun readWIFIState(): WIFIState? = null

    override fun sendNotification(notification: LibboxNotification?) { /* no platform notifications */ }
    override fun cancelNotification(identifier: String?, typeId: Int) { /* no platform notifications */ }

    // ---- not used by this app's configs: refuse loudly ----------------------------------------

    private fun unsupported(what: String): Nothing = throw UnsupportedOperationException("$what is not supported by this app")

    override fun findConnectionOwner(ipProtocol: Int, sourceAddress: String?, sourcePort: Int, destinationAddress: String?, destinationPort: Int): ConnectionOwner =
        unsupported("connection-owner lookup")

    override fun checkPlatformShell() { unsupported("platform shell") }
    override fun openShellSession(user: PlatformUser?, command: String?, environ: StringIterator?, term: String?, rows: Int, cols: Int): ShellSession = unsupported("shell session")
    override fun lookupUser(username: String?): PlatformUser = unsupported("user lookup")
    override fun lookupSFTPServer(): String = unsupported("SFTP")
    override fun readSystemSSHHostKey(): String = unsupported("SSH host key")
    override fun createBridge(options: BridgeOptions?): BridgeSession = unsupported("platform bridge")
    override fun startNeighborMonitor(listener: NeighborUpdateListener?) { unsupported("neighbor monitor") }
    override fun closeNeighborMonitor(listener: NeighborUpdateListener?) { unsupported("neighbor monitor") }
    override fun tailscaleHostname(): String = ""
}

internal class ListStringIterator(private val items: List<String>) : StringIterator {
    private var i = 0
    override fun hasNext(): Boolean = i < items.size
    override fun len(): Int = items.size - i
    override fun next(): String = items[i++]
}
