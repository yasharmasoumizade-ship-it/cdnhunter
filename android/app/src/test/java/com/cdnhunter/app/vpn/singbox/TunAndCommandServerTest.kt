package com.cdnhunter.app.vpn.singbox

import com.cdnhunter.app.core.routing.TunSetupException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files

class TunAndCommandServerTest {
    private val exp = TunExpectation(1500, setOf("172.19.0.1/30"), setOf("fdfe:dcba:9876::1/126"))
    private fun spec(mtu: Int = 1500, v4: List<String> = listOf("172.19.0.1/30"), v6: List<String> = emptyList(),
                     http: Boolean = false, inc: List<String> = emptyList(), exc: List<String> = emptyList()) =
        TunSpec(mtu, v4, v6, http, inc, exc)

    private fun rejects(s: TunSpec) { try { TunValidator.validate(s, exp); fail("should have been refused") } catch (_: TunSetupException) { } }

    @Test fun acceptsExactlyWhatTheServiceEstablished() { TunValidator.validate(spec(), exp); TunValidator.validate(spec(v6 = listOf("fdfe:dcba:9876::1/126")), exp) }
    @Test fun refusesADifferentMtu() = rejects(spec(mtu = 9000))
    @Test fun refusesAnIpv4AddressTheServiceDidNotConfigure() = rejects(spec(v4 = listOf("10.9.9.1/30")))
    @Test fun refusesAnIpv6AddressWhenTheServiceHasNone() {
        try { TunValidator.validate(spec(v6 = listOf("fd00::1/126")), TunExpectation(1500, setOf("172.19.0.1/30"), emptySet())); fail() } catch (_: TunSetupException) { }
    }
    @Test fun refusesASystemHttpProxy() = rejects(spec(http = true))
    @Test fun refusesPerAppListsThatWouldBeSilentlyIgnored() { rejects(spec(inc = listOf("a.b"))); rejects(spec(exc = listOf("a.b"))) }

    // command-server isolation
    private fun tmp(): File = Files.createTempDirectory("cs").toFile()

    @Test fun commandServerNeverListensOnTcp() {
        assertEquals(0, CommandServerSetup.create(tmp()).listenPort)
    }

    @Test fun everyRunGetsAFreshStrongSecret() {
        val root = tmp()
        val a = CommandServerSetup.create(root); val b = CommandServerSetup.create(root)
        assertEquals(64, a.secret.length); assertTrue(a.secret.all { it in "0123456789abcdef" })
        assertNotEquals(a.secret, b.secret)
    }

    @Test fun socketAndWorkingFilesLiveInsideTheAppPrivateRoot() {
        val root = tmp(); val p = CommandServerSetup.create(root)
        for (path in listOf(p.basePath, p.workingPath, p.tempPath)) { assertTrue(path.startsWith(root.absolutePath)); assertTrue(File(path).isDirectory) }
    }

    @Test fun theSecretNeverShowsUpInLogs() {
        val p = CommandServerSetup.create(tmp())
        assertFalse(p.toString().contains(p.secret))
    }

    @Test fun aBasePathTooLongForAUnixSocketIsRefused() {
        var deep = tmp()
        while (deep.absolutePath.length <= CommandServerSetup.MAX_BASE_PATH) deep = File(deep, "d".repeat(20))
        try { CommandServerSetup.create(deep); fail("a too-long unix socket path must be refused") } catch (_: IllegalArgumentException) { }
    }
}
