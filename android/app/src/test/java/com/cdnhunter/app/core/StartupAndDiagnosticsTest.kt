package com.cdnhunter.app.core

import com.cdnhunter.app.vpn.VpnConfigBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class StartupAndDiagnosticsTest {

    private fun settings(adBlock: Boolean = true, malware: Boolean = true) =
        ConnectionSettings(1500, false, false, true, false, emptyList(), "exclude", adBlock, true, true, malware)

    private fun tmp(): File = Files.createTempDirectory("mihomo-home").toFile().also { it.deleteOnExit() }

    // ── rule-provider cache seeding ──────────────────────────────────────────

    @Test fun everyDeclaredHttpProviderGetsAParseableOldPlaceholder() {
        val home = tmp()
        // The REAL clock: a freshly written file's mtime is "now", so only an explicit back-dating
        // can make it older than the provider interval. (A fixed far-future "now" made this pass vacuously.)
        val now = System.currentTimeMillis()
        VpnConfigBuilder.seedRuleProviderCache(home, settings(), nowMs = now)
        val declared = VpnConfigBuilder.ruleProviders(true, true, true, true).keys
        assertEquals(setOf("ir-domain", "ir-ip", "ad-block", "malware-block"), declared)
        for (f in listOf("ir-domain.txt", "ir-ip.yaml", "ad-block.txt", "malware-block.txt")) {
            val file = File(home, "ruleset/$f")
            assertTrue("$f missing", file.exists() && file.length() > 0)
            // Older than the provider interval (86400 s), so the core refreshes it in the background at once.
            assertTrue("$f not old enough", now - file.lastModified() > 86_400_000L)
        }
        assertTrue(File(home, "ruleset/ir-ip.yaml").readText().startsWith("payload:"))
        assertEquals("example.invalid\n", File(home, "ruleset/ir-domain.txt").readText())
    }

    @Test fun aRealCachedDownloadIsNeverOverwritten() {
        val home = tmp()
        val real = File(home, "ruleset/ir-domain.txt").also { it.parentFile.mkdirs(); it.writeText("a.ir\nb.ir\n"); it.setLastModified(123_000L) }
        VpnConfigBuilder.seedRuleProviderCache(home, settings())
        assertEquals("a.ir\nb.ir\n", real.readText())
        assertEquals(123_000L, real.lastModified())
    }

    @Test fun anEmptyLeftoverFileIsReplacedByAValidPlaceholder() {
        val home = tmp()
        File(home, "ruleset/ad-block.txt").also { it.parentFile.mkdirs(); it.writeText("") }
        VpnConfigBuilder.seedRuleProviderCache(home, settings())
        assertTrue(File(home, "ruleset/ad-block.txt").length() > 0)
    }

    @Test fun onlyProvidersTheConfigActuallyDeclaresAreSeeded() {
        val home = tmp()
        VpnConfigBuilder.seedRuleProviderCache(home, settings(adBlock = false, malware = false))
        assertTrue(File(home, "ruleset/ir-domain.txt").exists() && File(home, "ruleset/ir-ip.yaml").exists())
        assertFalse(File(home, "ruleset/ad-block.txt").exists())
        assertFalse(File(home, "ruleset/malware-block.txt").exists())
    }

    @Test fun seedingNeverFailsTheConnectionEvenIfTheDirectoryIsUnwritable() {
        val notADir = File.createTempFile("not-a-dir", ".tmp").also { it.deleteOnExit() }
        VpnConfigBuilder.seedRuleProviderCache(notADir, settings()) // must simply not throw
    }

    @Test fun theRenderedConfigStillDeclaresExactlyTheSameProviders() {
        val m = linkedMapOf<String, Any>("type" to "vless", "server" to "example.com", "port" to 443, "uuid" to "u")
        val v = ConfigValidator.validate(InternalConnectionConfig.fromProxyMap(m)!!) as ValidationResult.Valid
        val yaml = VpnConfigBuilder.buildFromValidated(v.config, 9, settings())
        for (name in listOf("ir-domain", "ir-ip", "ad-block", "malware-block")) assertTrue("$name missing from YAML", yaml.contains("  $name:"))
        assertTrue(yaml.contains("RULE-SET,ir-domain,DIRECT") && yaml.contains("RULE-SET,ir-ip,DIRECT") && yaml.contains("MATCH,PROXY"))
        assertTrue(yaml.contains("path: ./ruleset/ir-domain.txt"))
    }

    // ── core log filter, on the log the user actually captured ───────────────

    private val field = """
[info] Mixed(http+socks) proxy listening at: 127.0.0.1:10808
[debug] [DNS] hijack udp:8.8.8.8:53 from 10.10.10.10:25898
[debug] [DNS] hijack udp:8.8.8.8:53 from 10.10.10.10:24994
[debug] [DNS] resolve raw.githubusercontent.com error: all DNS requests failed, first error: requesting https://8.8.4.4:443/dns-query: Get "https://8.8.4.4:443/dns-query?dns=AAAB": context deadline exceeded
[debug] [DNS] cache hit xn--1dee-ow4bt2570a.netbazshop.ir --> [157.90.245.77] A, expire at 2026-10-02 21:32:43
[debug] [Rule] use default rules
[debug] re-creating the http client due to requesting https://8.8.4.4:443/dns-query: Get "x": context deadline exceeded
[debug] [https://8.8.8.8:443/dns-query] using HTTP/2 for this upstream: <nil>
[error] initial rule provider ir-domain error: Get "https://raw.githubusercontent.com/Chocolate4U/Iran-clash-rules/release/release/ir.txt": context deadline exceeded
[error] initial rule provider ir-ip error: Get "https://raw.githubusercontent.com/Chocolate4U/Iran-clash-rules/release/ircidr.yaml": context deadline exceeded
[error] initial rule provider ad-block error: Get "https://raw.githubusercontent.com/Loyalsoldier/clash-rules/release/reject.txt": context deadline exceeded
[warn] [TCP] dial PROXY (match Match) 127.0.0.1:43330 --> www.gstatic.com:443 error: dial tcp4 157.90.245.77:33017: i/o timeout
[warn] [TCP] dial PROXY (match Match) 127.0.0.1:43331 --> www.gstatic.com:443 error: dial tcp4 157.90.245.77:33017: i/o timeout
""".trimIndent()

    @Test fun theFilterKeepsTheCauseAndDropsTheNoise() {
        val out = CoreLogFilter.failures(field, max = 10)
        assertTrue(out.any { it.contains("dial PROXY") && it.contains("i/o timeout") })
        assertTrue(out.any { it.contains("initial rule provider ir-domain") })
        assertTrue(out.none { it.contains("hijack") || it.contains("cache hit") || it.contains("[Rule]") || it.contains("HTTP/2") })
        assertTrue(out.none { it.contains("Mixed(http+socks)") })
        // These two CONTAIN failure words ("deadline exceeded") yet are only the core retrying its own
        // DoH client — symptoms, not causes — so they must be dropped by the noise list, not by luck.
        assertTrue(out.none { it.contains("re-creating the http client") })
    }

    @Test fun aNoiseLineIsDroppedEvenIfItMentionsAFailure() {
        val log = """
            [debug] [DNS] hijack udp:8.8.8.8:53 from 10.10.10.10:1 error: timeout
            [debug] [DNS] cache hit a.example.com --> [1.2.3.4] A (reset)
            [warn] [TCP] dial PROXY (match Match) a --> b:443 error: connection refused
        """.trimIndent()
        assertEquals(1, CoreLogFilter.failures(log).size)
        assertTrue(CoreLogFilter.failures(log).single().contains("connection refused"))
    }

    @Test fun repeatsThatDifferOnlyInPortsAreCollapsed() {
        val out = CoreLogFilter.failures(field, max = 10)
        assertEquals(1, out.count { it.contains("dial PROXY") })
    }

    @Test fun theCauseSurvivesEvenWhenBuriedUnderHundredsOfNoiseLines() {
        val noise = (1..600).joinToString("\n") { "[debug] [DNS] hijack udp:8.8.8.8:53 from 10.10.10.10:$it" }
        val log = "[warn] [TCP] dial PROXY (match Match) a --> b:443 error: EOF\n$noise"
        // A plain "last N characters" tail would show only hijack lines. The filter does not.
        assertFalse(log.takeLast(800).contains("dial PROXY"))
        assertEquals(1, CoreLogFilter.failures(log).size)
    }

    @Test fun summaryIsBoundedAndRedactedWhenItEntersAnError() {
        val key = "rK2aQSz2wOAaLoJe61rb_TmbQxnF87VEAxGzd3TVmlY"
        val s = CoreLogFilter.summary("[error] invaild vless encryption value: mlkem768x25519plus.native.0rtt.$key", 3)
        assertTrue(s.contains("invaild vless encryption"))
        assertFalse(ConnectionError.TimeoutError("core: $s").technical.contains(key))
    }

    @Test fun anEmptyLogGivesNothing() { assertEquals(emptyList<String>(), CoreLogFilter.failures("")) }

    // ── probe ordering ───────────────────────────────────────────────────────

    @Test fun theFirstProbeUrlIsAnIpLiteralSoItDoesNotNeedDnsThroughTheProxy() {
        val first = ProxyProbe.DEFAULT_URLS.first()
        assertTrue(first, Regex("""^https://\d{1,3}(\.\d{1,3}){3}/""").containsMatchIn(first))
        assertTrue(ProxyProbe.DEFAULT_URLS.size >= 2)
    }
}
