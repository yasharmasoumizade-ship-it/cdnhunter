package com.cdnhunter.app.vpn.singbox

import com.cdnhunter.app.core.routing.TunSetupException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AndroidSingBoxPlatformTest {
    private class FakeSource : DefaultInterfaceSource {
        var onChange: ((DefaultInterface) -> Unit)? = null
        var stops = 0
        override fun start(onChange: (DefaultInterface) -> Unit) { this.onChange = onChange }
        override fun stop() { stops++ }
    }
    private val noIfs = InterfaceLister { emptyList() }

    private fun platform(lender: FakeLender = FakeLender(), protect: Boolean = true, src: FakeSource = FakeSource()) =
        AndroidSingBoxPlatform(lender, SocketProtector { protect }, src, noIfs)

    // ---- openTun / TUN ownership ---------------------------------------------------------------

    @Test fun openTunLendsTheServicesOwnFdAndNothingElse() {
        val l = FakeLender(fd = 57)
        val fd = platform(l).openTun(FakeTunOptions())
        assertEquals("the very same fd number: no dup, no new fd", 57, fd)
        assertEquals(1, l.lendCalls)
        assertEquals("the glue must never close the service's fd", 0, l.closeCalls)
    }

    @Test fun openTunValidatesBeforeLendingAnything() {
        val l = FakeLender()
        try { platform(l).openTun(FakeTunOptions(mtu = 9000)); fail() } catch (_: TunSetupException) { }
        assertEquals(0, l.lendCalls)
    }

    @Test fun openTunRefusesASystemHttpProxy() {
        try { platform().openTun(FakeTunOptions(httpProxy = true)); fail() } catch (_: TunSetupException) { }
    }

    @Test fun openTunFailsClearlyWhenTheServiceHasNoTun() {
        try { platform(FakeLender(exp = null)).openTun(FakeTunOptions()); fail() } catch (_: TunSetupException) { }
        try { platform(FakeLender(fd = -1)).openTun(FakeTunOptions()); fail() } catch (_: TunSetupException) { }
    }

    @Test fun closingTheEngineNeverClosesTheLentFd() {
        val l = FakeLender(); val p = platform(l)
        p.openTun(FakeTunOptions())
        p.registerMyInterface("tun0")
        assertEquals("tun0", p.myTunName)
        assertEquals(0, l.closeCalls)
    }

    // ---- protector ----------------------------------------------------------------------------

    @Test fun everyEngineSocketGoesThroughProtect() {
        val seen = mutableListOf<Int>()
        val p = AndroidSingBoxPlatform(FakeLender(), SocketProtector { seen += it; true }, FakeSource(), noIfs)
        assertTrue(p.usePlatformAutoDetectInterfaceControl())
        p.autoDetectInterfaceControl(33)
        assertEquals(listOf(33), seen)
    }

    @Test fun aFailedProtectIsAnErrorNotASilentLeak() {
        try { platform(protect = false).autoDetectInterfaceControl(33); fail() } catch (_: IllegalStateException) { }
    }

    // ---- interface monitor --------------------------------------------------------------------

    @Test fun defaultInterfaceChangesReachLibbox() {
        val src = FakeSource(); val l = RecordingListener(); val p = platform(src = src)
        p.startDefaultInterfaceMonitor(l)
        src.onChange!!.invoke(DefaultInterface("wlan0", 12, false, false))
        src.onChange!!.invoke(DefaultInterface.NONE)
        assertEquals(listOf(DefaultInterface("wlan0", 12, false, false), DefaultInterface.NONE), l.updates)
        p.closeDefaultInterfaceMonitor(l)
        assertEquals(1, src.stops)
    }

    @Test fun anExceptionOnTheGoSideDoesNotEscapeIntoTheNetworkCallback() {
        val src = FakeSource(); val l = RecordingListener().apply { throwOnUpdate = true }
        platform(src = src).startDefaultInterfaceMonitor(l)
        src.onChange!!.invoke(DefaultInterface("wlan0", 12, false, false)) // must not throw
    }

    @Test fun getInterfacesWithNoInterfacesIsEmpty() {
        assertFalse(platform().getInterfaces().hasNext())
    }

    // ---- DNS and the things this app does not use ---------------------------------------------

    @Test fun noPlatformLocalDnsTransportIsOffered() { assertNull(platform().localDNSTransport()) }

    @Test fun platformFeaturesTheAppDoesNotUseAreOff() {
        val p = platform()
        assertFalse(p.usePlatformBridge()); assertFalse(p.usePlatformShell()); assertFalse(p.useProcFS())
        assertFalse(p.includeAllNetworks()); assertFalse(p.underNetworkExtension()); assertNull(p.readWIFIState())
    }

    @Test fun unusedFeaturesFailLoudlyInsteadOfDoingNothing() {
        val p = platform()
        val calls = listOf<() -> Unit>(
            { p.checkPlatformShell() }, { p.lookupUser("u") }, { p.lookupSFTPServer() }, { p.readSystemSSHHostKey() },
            { p.createBridge(null) }, { p.startNeighborMonitor(null) }, { p.closeNeighborMonitor(null) },
            { p.findConnectionOwner(6, "1.1.1.1", 1, "2.2.2.2", 2) },
        )
        for (c in calls) { try { c(); fail("must be refused") } catch (_: UnsupportedOperationException) { } }
    }
}
