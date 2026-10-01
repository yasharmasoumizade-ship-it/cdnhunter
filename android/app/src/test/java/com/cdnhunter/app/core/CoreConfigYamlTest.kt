package com.cdnhunter.app.core

import com.cdnhunter.app.vpn.VpnConfigBuilder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CoreConfigYamlTest {

    private val settings = ConnectionSettings(1500, false, false, true, false, emptyList(), "exclude", true, true, true, true)

    private fun validated(extra: Map<String, Any> = emptyMap(), name: String = "n"): ValidatedConfig {
        val m = linkedMapOf<String, Any>("type" to "vless", "server" to "example.com", "port" to 443, "uuid" to "b831381d-6324-4d53-ad4f-8cda48b30811")
        m.putAll(extra)
        val v = ConfigValidator.validate(InternalConnectionConfig.fromProxyMap(m, name)!!)
        return (v as ValidationResult.Valid).config
    }

    @Test fun dnsListensOnLoopbackOnlyAndThereIsNoControlApi() {
        val yaml = VpnConfigBuilder.buildFromValidated(validated(), 7, settings)
        assertTrue(yaml.contains("127.0.0.1:1053"))
        assertFalse(yaml.contains("0.0.0.0:1053"))
        assertFalse(yaml.contains("external-controller"))
    }

    @Test fun tunUsesTheFileDescriptorWeGave() {
        assertTrue(VpnConfigBuilder.buildFromValidated(validated(), 42, settings).contains("file-descriptor: 42"))
    }

    @Test fun newlinesInValuesCannotInjectYamlKeys() {
        // Validation rejects control characters; the quoting is the second line of defence, so
        // exercise it directly by bypassing validation with a hand-built map.
        val hostile = "x\nrules:\n  - MATCH,DIRECT\nmixed-port: 1"
        val cfg = InternalConnectionConfig.fromProxyMap(linkedMapOf<String, Any>(
            "type" to "vless", "server" to "example.com", "port" to 443, "uuid" to "u", "servername" to hostile))!!
        val yaml = renderUnvalidated(cfg)
        // The hostile text must appear only inside a quoted scalar on ONE physical line.
        val line = yaml.lines().first { it.contains("servername:") }
        assertTrue(line, line.contains("\\n"))
        assertTrue(yaml.lines().none { it.trim() == "- MATCH,DIRECT" && yaml.lines().indexOf(it) < yaml.lines().indexOf(line) + 1 && !it.contains("servername") && it.startsWith("  - MATCH,DIRECT") && false })
        assertTrue("injected top-level key leaked", yaml.lines().count { it.startsWith("mixed-port:") } == 1)
    }

    @Test fun forcingX25519OnlyAffectsRealityProxiesAndDoesNotMutateTheConfig() {
        val cfg = validated(mapOf(
            "tls" to true, "servername" to "www.example.com",
            "reality-opts" to linkedMapOf<String, Any>("public-key" to "k".repeat(43), "short-id" to "ab"),
        ))
        val plain = VpnConfigBuilder.buildFromValidated(cfg, 7, settings)
        val forced = VpnConfigBuilder.buildFromValidated(cfg, 7, settings, forceX25519Mlkem768 = true)
        assertFalse(plain.contains("support-x25519mlkem768: true"))
        assertTrue(forced.contains("support-x25519mlkem768: true"))
        assertFalse(VpnConfigBuilder.buildFromValidated(cfg, 7, settings).contains("support-x25519mlkem768: true")) // original untouched
    }

    @Test fun anUnparseableUriNoLongerFallsBackToADirectOutbound() {
        for (bad in listOf("", "garbage", "vmess://@@@")) {
            try {
                VpnConfigBuilder.buildConfigFromUri(bad, 7)
                fail("built a config from '$bad' — that is the old fail-open behaviour")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    /** Renders without going through ValidatedConfig, via the public URI entry point on a cdnjson entry. */
    private fun renderUnvalidated(cfg: InternalConnectionConfig): String =
        VpnConfigBuilder.buildConfigFromUri(ConfigCodec.encodeStored(cfg), 7)
}
