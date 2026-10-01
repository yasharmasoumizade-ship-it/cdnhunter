package com.cdnhunter.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigValidatorTest {

    private fun vless(
        server: String = "example.com", port: Int = 443, uuid: String? = "b831381d-6324-4d53-ad4f-8cda48b30811",
        extra: Map<String, Any> = emptyMap(),
    ): InternalConnectionConfig {
        val m = linkedMapOf<String, Any>("type" to "vless", "server" to server, "port" to port)
        if (uuid != null) m["uuid"] = uuid
        m.putAll(extra)
        return InternalConnectionConfig.fromProxyMap(m)!!
    }

    private fun codeOf(c: InternalConnectionConfig): ErrorCode? =
        (ConfigValidator.validate(c) as? ValidationResult.Invalid)?.error?.code

    @Test fun aNormalConfigPasses() = assertNull(codeOf(vless()))

    @Test fun serverAddressRules() {
        for (ok in listOf("example.com", "sub.example.co.uk", "1.2.3.4", "[2001:db8::1]", "xn--mnchen-3ya.de", "a_b.example.com"))
            assertNull("should accept $ok", codeOf(vless(server = ok)))
        for (bad in listOf("", " ", "999.1.1.1", "1.2.3", "0.0.0.0", "exa mple.com", "example.com/path", "user@example.com",
            "-bad.example.com", "2001:db8::1", "[2001:db8::1", "a..b.com", "host#frag"))
            assertEquals("should reject '$bad'", ErrorCode.INVALID_SERVER, codeOf(vless(server = bad)))
    }

    @Test fun portRules() {
        assertNull(codeOf(vless(port = 1))); assertNull(codeOf(vless(port = 65535)))
        assertEquals(ErrorCode.INVALID_PORT, codeOf(vless(port = 0)))
        assertEquals(ErrorCode.INVALID_PORT, codeOf(vless(port = 65536)))
        assertEquals(ErrorCode.INVALID_PORT, codeOf(vless(port = -5)))
    }

    @Test fun credentialsAreRequired() {
        assertEquals(ErrorCode.INVALID_CREDENTIALS, codeOf(vless(uuid = null)))
        assertEquals(ErrorCode.INVALID_CREDENTIALS, codeOf(vless(uuid = "  ")))
        assertEquals(ErrorCode.INVALID_CREDENTIALS, codeOf(vless(uuid = "x".repeat(65))))
        val trojan = InternalConnectionConfig.fromProxyMap(linkedMapOf("type" to "trojan", "server" to "a.com", "port" to 443))!!
        assertEquals(ErrorCode.INVALID_CREDENTIALS, codeOf(trojan))
        val ss = InternalConnectionConfig.fromProxyMap(linkedMapOf("type" to "ss", "server" to "a.com", "port" to 443, "password" to "p"))!!
        assertEquals(ErrorCode.INVALID_CREDENTIALS, codeOf(ss)) // no cipher
    }

    @Test fun unsupportedTransportIsNamedNotSilentlyTreatedAsTcp() {
        assertEquals(ErrorCode.UNSUPPORTED_PROTOCOL, codeOf(vless(extra = mapOf("network" to "kcp"))))
        for (ok in listOf("tcp", "ws", "grpc", "h2", "xhttp")) assertNull(codeOf(vless(extra = mapOf("network" to ok))))
    }

    @Test fun realityNeedsKeyAndSniAndAValidShortId() {
        fun reality(pbk: String?, sid: String, sni: String?) = vless(extra = buildMap {
            put("tls", true)
            put("reality-opts", buildMap<String, Any> { if (pbk != null) put("public-key", pbk); put("short-id", sid) })
            if (sni != null) put("servername", sni)
        })
        assertNull(codeOf(reality("k".repeat(43), "6ba85179e30d4fc2", "www.example.com")))
        assertNull(codeOf(reality("k".repeat(43), "", "www.example.com")))
        assertEquals(ErrorCode.INVALID_CONFIG, codeOf(reality(null, "ab", "www.example.com")))
        assertEquals(ErrorCode.INVALID_CONFIG, codeOf(reality("k".repeat(43), "zz", "www.example.com")))
        assertEquals(ErrorCode.INVALID_CONFIG, codeOf(reality("k".repeat(43), "a".repeat(18), "www.example.com")))
        assertEquals(ErrorCode.INVALID_CONFIG, codeOf(reality("k".repeat(43), "ab", null)))
    }

    @Test fun flowRules() {
        assertNull(codeOf(vless(extra = mapOf("flow" to "xtls-rprx-vision", "tls" to true))))
        assertEquals(ErrorCode.INVALID_CONFIG, codeOf(vless(extra = mapOf("flow" to "xtls-rprx-vision"))))        // no TLS
        assertEquals(ErrorCode.UNSUPPORTED_PROTOCOL, codeOf(vless(extra = mapOf("flow" to "xtls-rprx-direct", "tls" to true))))
    }

    @Test fun controlCharactersAnywhereAreRejected() {
        assertEquals(ErrorCode.INVALID_CONFIG, codeOf(vless(extra = mapOf("ws-opts" to mapOf("path" to "/a\nrules:\n  - MATCH,DIRECT")))))
        assertEquals(ErrorCode.INVALID_CONFIG, codeOf(vless(extra = mapOf("servername" to "a\u0000b"))))
        assertEquals(ErrorCode.INVALID_CONFIG, codeOf(vless(extra = mapOf("alpn" to listOf("h2", "x\ty")))))
    }

    @Test fun validatedConfigNeverPrintsItsCredentials() {
        val v = ConfigValidator.validate(vless()) as ValidationResult.Valid
        assertTrue(!v.toString().contains("b831381d"))
        assertNotNull(v.config.config.credential)
    }

    @Test fun settingsValidation() {
        fun s(mtu: Int = 1500, mode: String = "exclude", dns: List<String> = emptyList(), dnsOn: Boolean = true) =
            ConnectionSettings(mtu, false, false, true, dnsOn, dns, mode, true, true, true, true)
        assertNull(SettingsValidator.validate(s()))
        assertEquals("mtu", SettingsValidator.validate(s(mtu = 100))?.field)
        assertEquals("mtu", SettingsValidator.validate(s(mtu = 20000))?.field)
        assertEquals("split_tunnel_mode", SettingsValidator.validate(s(mode = "weird"))?.field)
        assertNull(SettingsValidator.validate(s(dns = listOf("1.1.1.1", "8.8.8.8:53", "https://dns.google/dns-query", "tls://one.one.one.one", "[2606:4700:4700::1111]:53"))))
        assertEquals("custom_dns", SettingsValidator.validate(s(dns = listOf("1.1.1.1", "not a dns")))?.field)
        assertEquals("custom_dns", SettingsValidator.validate(s(dns = listOf("300.1.1.1")))?.field)
        assertNull(SettingsValidator.validate(s(dns = listOf("garbage"), dnsOn = false))) // unused list is not validated
    }
}
