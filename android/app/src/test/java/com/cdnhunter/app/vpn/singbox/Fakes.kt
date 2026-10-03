package com.cdnhunter.app.vpn.singbox

import com.cdnhunter.mihomo.libbox.InterfaceUpdateListener
import com.cdnhunter.mihomo.libbox.RoutePrefix
import com.cdnhunter.mihomo.libbox.RoutePrefixIterator
import com.cdnhunter.mihomo.libbox.StringBox
import com.cdnhunter.mihomo.libbox.StringIterator
import com.cdnhunter.mihomo.libbox.TunOptions
import java.util.Collections

class FakeServer(private val b: FakeBackend, val events: ServerEvents) : BackendServer {
    @Volatile var closeServiceCalls = 0
    @Volatile var closeCalls = 0
    override fun start() { b.calls += "start"; b.failAt.takeIf { it == "start" }?.let { throw IllegalStateException("start failed") } }
    override fun startOrReloadService(configJson: String) {
        b.calls += "startOrReload"
        b.onStartOrReload?.invoke()
        if (b.failAt == "startOrReload") throw IllegalStateException("bad config")
    }
    override fun closeService() { closeServiceCalls++; b.calls += "closeService"; b.onCloseService?.invoke() }
    override fun close() { closeCalls++; b.calls += "close" }
}

class FakeBackend : LibboxBackend {
    val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val servers = Collections.synchronizedList(mutableListOf<FakeServer>())
    @Volatile var setupParams: CommandServerParams? = null
    @Volatile var failAt: String? = null
    @Volatile var onStartOrReload: (() -> Unit)? = null
    @Volatile var onCloseService: (() -> Unit)? = null
    override fun setup(params: CommandServerParams) { calls += "setup"; setupParams = params; if (failAt == "setup") throw IllegalStateException("setup failed") }
    override fun newServer(events: ServerEvents): BackendServer { calls += "newServer"; return FakeServer(this, events).also { servers += it } }
}

fun testParams() = CommandServerParams("/tmp/sb", "/tmp/sb/w", "/tmp/sb/t", "s3cr3t".repeat(10))

class FakeLender(var fd: Int = 41, var exp: TunExpectation? = TunExpectation(1500, setOf("172.19.0.1/30"), emptySet())) : TunLender {
    var lendCalls = 0
    var closeCalls = 0 // the glue must never close the service's fd: nothing calls this, a test asserts it stays 0
    override fun expectation() = exp
    override fun lend(): Int { lendCalls++; return fd }
}

private class EmptyStrings : StringIterator {
    override fun hasNext() = false
    override fun len() = 0
    override fun next(): String = throw NoSuchElementException()
}

private class EmptyPrefixes : RoutePrefixIterator {
    override fun hasNext() = false
    override fun next(): RoutePrefix = throw NoSuchElementException()
}

class FakeTunOptions(val mtu: Int = 1500, val httpProxy: Boolean = false) : TunOptions {
    override fun getAutoRoute() = false
    override fun getDNSMode(): StringBox? = null
    override fun getDNSServerAddress(): StringIterator = EmptyStrings()
    override fun getExcludePackage(): StringIterator = EmptyStrings()
    override fun getHTTPProxyBypassDomain(): StringIterator = EmptyStrings()
    override fun getHTTPProxyMatchDomain(): StringIterator = EmptyStrings()
    override fun getHTTPProxyServer() = ""
    override fun getHTTPProxyServerPort() = 0
    override fun getIncludePackage(): StringIterator = EmptyStrings()
    override fun getInet4Address(): RoutePrefixIterator = EmptyPrefixes()
    override fun getInet4RouteAddress(): RoutePrefixIterator = EmptyPrefixes()
    override fun getInet4RouteExcludeAddress(): RoutePrefixIterator = EmptyPrefixes()
    override fun getInet4RouteRange(): RoutePrefixIterator = EmptyPrefixes()
    override fun getInet6Address(): RoutePrefixIterator = EmptyPrefixes()
    override fun getInet6RouteAddress(): RoutePrefixIterator = EmptyPrefixes()
    override fun getInet6RouteExcludeAddress(): RoutePrefixIterator = EmptyPrefixes()
    override fun getInet6RouteRange(): RoutePrefixIterator = EmptyPrefixes()
    override fun getMTU() = mtu
    override fun getStrictRoute() = false
    override fun isHTTPProxyEnabled() = httpProxy
}

class RecordingListener : InterfaceUpdateListener {
    val updates = Collections.synchronizedList(mutableListOf<DefaultInterface>())
    var throwOnUpdate = false
    override fun updateDefaultInterface(name: String, index: Int, expensive: Boolean, constrained: Boolean) {
        if (throwOnUpdate) throw IllegalStateException("go side failed")
        updates += DefaultInterface(name, index, expensive, constrained)
    }
    override fun updateNetworkPath(path: String) {}
}
