package com.cdnhunter.app.core

import com.cdnhunter.app.core.json.JsonConfigParser
import com.cdnhunter.app.core.json.JsonParseResult
import com.cdnhunter.app.vpn.ConfigUriParser
import com.cdnhunter.app.vpn.VpnConfigBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real configs from a subscription that "would not connect". Every server in it uses VLESS
 * Encryption (mlkem768x25519plus); ours dropped that field, so the server rejected every handshake.
 */
class SubscriptionSamplesTest {

    private val settings = ConnectionSettings(1500, false, false, true, false, emptyList(), "exclude", true, true, true, true)
    private val key = "rK2aQSz2wOAaLoJe61rb_TmbQxnF87VEAxGzd3TVmlY"
    private val enc = "mlkem768x25519plus.native.0rtt.$key"

    /** The user's sample, verbatim. */
    private val userJson = """
    { "log": {"access":"","error":"","loglevel":"warning"},
      "policy": {"system":{"statsOutboundDownlink":true,"statsOutboundUplink":true},"levels":{"8":{"connIdle":300,"downlinkOnly":1,"handshake":4,"uplinkOnly":1}}},
      "inbounds": [
        {"tag":"socks","port":10808,"listen":"0.0.0.0","protocol":"socks","sniffing":{"enabled":true,"destOverride":["http","tls"],"routeOnly":false},"settings":{"auth":"noauth","udp":true,"allowTransparent":false}},
        {"tag":"http","port":10809,"listen":"0.0.0.0","protocol":"http","sniffing":{"enabled":true,"destOverride":["http","tls"],"routeOnly":false},"settings":{"auth":"noauth","udp":true,"allowTransparent":false}} ],
      "outbounds": [
        {"protocol":"vless","tag":"proxy","settings":{"vnext":[{"address":"varzesh3.com","port":80,
           "users":[{"id":"d9618dc1-c78c-4a41-a8d2-4f07e7fd14bf","encryption":"$enc"}]}]},
         "streamSettings":{"network":"ws","wsSettings":{"path":"/pgcfws-r4","host":"jase.yldl.ir"}}},
        {"protocol":"freedom","tag":"DIRECT"}, {"protocol":"blackhole","tag":"BLOCK"} ],
      "dns": {"servers":["1.1.1.1","8.8.8.8"]},
      "routing": {"domainStrategy":"AsIs","rules":[]},
      "remarks": "🇩🇪𝔾𝕖𝕣𝕞𝕒𝕟𝕪4🇮🇷" }
    """

    private fun yamlFor(p: LinkedHashMap<String, Any>): String {
        val cfg = InternalConnectionConfig.fromProxyMap(p)!!
        val v = ConfigValidator.validate(cfg)
        assertTrue("validation failed: $v", v is ValidationResult.Valid)
        return VpnConfigBuilder.buildFromValidated((v as ValidationResult.Valid).config, 9, settings)
    }

    @Test fun theUsersJsonNowParsesAndKeepsItsEncryption() {
        val r = JsonConfigParser.parse(userJson) as JsonParseResult.Success
        assertEquals(1, r.proxies.size)
        assertEquals("🇩🇪𝔾𝕖𝕣𝕞𝕒𝕟𝕪4🇮🇷", r.proxies[0].name)          // the remark, not the tag "proxy"
        val p = r.proxies[0].config.proxy
        assertEquals("varzesh3.com", p["server"]); assertEquals(80, p["port"]); assertEquals("ws", p["network"])
        assertEquals(enc, p["encryption"])
        assertEquals("jase.yldl.ir", ((p["ws-opts"] as Map<*, *>)["headers"] as Map<*, *>)["Host"])
        assertEquals("/pgcfws-r4", (p["ws-opts"] as Map<*, *>)["path"])
        assertNull(p["tls"])                                           // security is "none" for this server
    }

    @Test fun theEncryptionStringReachesTheCoreConfigUnchanged() {
        val p = (JsonConfigParser.parse(userJson) as JsonParseResult.Success).proxies[0].config.proxy
        val yaml = yamlFor(p)
        assertTrue(yaml, yaml.contains("encryption: $enc"))
        assertTrue(yaml.contains("network: ws"))
    }

    @Test fun theSameServerAsAShareLinkGivesTheSameConfig() {
        val link = "vless://d9618dc1-c78c-4a41-a8d2-4f07e7fd14bf@varzesh3.com:80?encryption=$enc&security=none&type=ws&headerType=none&path=%2Fpgcfws-r4&host=cloud.safari-df.ir#x"
        val p = ConfigUriParser.parseToProxy(link)!!
        assertEquals(enc, p["encryption"])
        assertEquals("ws", p["network"])
        assertTrue(yamlFor(p).contains("encryption: $enc"))
    }

    @Test fun encryptionNoneIsNotEmittedAsAFieldAtAll() {
        val p = ConfigUriParser.parseToProxy("vless://d9618dc1-c78c-4a41-a8d2-4f07e7fd14bf@a.example.com:443?encryption=none&security=tls&type=ws&host=h.example.com&path=%2F")!!
        assertFalse(p.containsKey("encryption"))
    }

    @Test fun tcpWithAnHttpHeaderIsHttpObfuscationNotPlainTcp() {
        val link = "vless://d9618dc1-c78c-4a41-a8d2-4f07e7fd14bf@xn--1dee-ow4bt2570a.netbazshop.ir:33017?encryption=mlkem768x25519plus.native.0rtt.YoqVHL7fCzI4qbWSy3TN6inQG32vJD01ZGgUKvLoQFY&security=none&type=tcp&headerType=http&path=%2F&host=testspeed-foreign.tci.ir#"
        val p = ConfigUriParser.parseToProxy(link)!!
        assertEquals("http", p["network"])
        val opts = p["http-opts"] as Map<*, *>
        assertEquals("GET", opts["method"]); assertEquals(listOf("/"), opts["path"])
        assertEquals(listOf("testspeed-foreign.tci.ir"), (opts["headers"] as Map<*, *>)["Host"])
        val yaml = yamlFor(p)
        assertTrue(yaml, yaml.contains("network: http") && yaml.contains("http-opts:"))
    }

    @Test fun plainTcpWithoutAHeaderIsStillPlainTcp() {
        val p = ConfigUriParser.parseToProxy("vless://d9618dc1-c78c-4a41-a8d2-4f07e7fd14bf@a.example.com:443?type=tcp&security=none&headerType=none")!!
        assertNull(p["network"]); assertNull(p["http-opts"])
    }

    @Test fun xhttpWithTlsAndNoEncryptionStillValidates() {
        val link = "vless://d9618dc1-c78c-4a41-a8d2-4f07e7fd14bf@cloud2.netbazshop.ir:1001?encryption=none&security=tls&type=xhttp&headerType=none&path=%2F&host=cafeariel.ir&mode=stream-up&extra=%7B%22xPaddingBytes%22%3A%22100-1000%22%7D&sni=sslcert.okamii1.ir#"
        val p = ConfigUriParser.parseToProxy(link)!!
        assertEquals("xhttp", p["network"]); assertEquals(true, p["tls"]); assertEquals("sslcert.okamii1.ir", p["servername"])
        yamlFor(p)
    }

    // ── JSON shapes subscriptions actually use ───────────────────────────────

    @Test fun anArrayOfCompleteConfigsIsUnpackedOneServerEach() {
        fun cfg(remark: String, host: String) = """{"inbounds":[{"protocol":"socks","port":10808}],"outbounds":[
            {"protocol":"vless","tag":"proxy","settings":{"vnext":[{"address":"$host","port":80,"users":[{"id":"d9618dc1-c78c-4a41-a8d2-4f07e7fd14bf","encryption":"$enc"}]}]},
             "streamSettings":{"network":"ws","wsSettings":{"path":"/p","host":"h.example.com"}}},
            {"protocol":"freedom","tag":"DIRECT"}],"remarks":"$remark"}"""
        val r = JsonConfigParser.parse("[${cfg("Germany 1", "a.example.com")},${cfg("Germany 2", "b.example.com")}]") as JsonParseResult.Success
        assertEquals(listOf("Germany 1", "Germany 2"), r.proxies.map { it.name })
        assertEquals(listOf("a.example.com", "b.example.com"), r.proxies.map { it.config.server })
    }

    @Test fun anArrayMixingUsableAndUnusableConfigsKeepsTheUsableOnes() {
        val good = """{"outbounds":[{"protocol":"trojan","settings":{"servers":[{"address":"a.example.com","port":443,"password":"pw"}]}}],"remarks":"ok"}"""
        val bad = """{"outbounds":[{"protocol":"wireguard","settings":{}}],"remarks":"nope"}"""
        val r = JsonConfigParser.parse("[$bad,$good]") as JsonParseResult.Success
        assertEquals(listOf("ok"), r.proxies.map { it.name })
        assertEquals(1, r.skipped.count { it.tag == "wireguard" })
    }

    // ── encryption validation ────────────────────────────────────────────────

    @Test fun encryptionEnvelopeIsValidatedButKeyMaterialIsLeftToTheCore() {
        assertNull(ConfigValidator.validateVlessEncryption(enc))
        assertNull(ConfigValidator.validateVlessEncryption("mlkem768x25519plus.xorpub.1rtt.100-111-1111.75-0-111.$key.${key}"))
        for (bad in listOf("", "none", "aes-128-gcm", "mlkem768x25519plus.native.0rtt", "mlkem768x25519plus.weird.0rtt.$key",
            "mlkem768x25519plus.native.2rtt.$key", "mlkem768x25519plus.native.0rtt..$key", "mlkem768x25519plus.native.0rtt.a b", "x".repeat(5000)))
            assertNotNull("should reject '$bad'", ConfigValidator.validateVlessEncryption(bad))
    }

    @Test fun anUnsupportedEncryptionIsNamedNotSilentlyDropped() {
        val cfg = InternalConnectionConfig.fromProxyMap(linkedMapOf("type" to "vless", "server" to "a.example.com", "port" to 443,
            "uuid" to "u", "encryption" to "some-future-scheme.x.y.z"))!!
        assertEquals(ErrorCode.UNSUPPORTED_PROTOCOL, (ConfigValidator.validate(cfg) as ValidationResult.Invalid).error.code)
        val trojan = InternalConnectionConfig.fromProxyMap(linkedMapOf("type" to "trojan", "server" to "a.example.com", "port" to 443,
            "password" to "p", "encryption" to enc))!!
        assertEquals(ErrorCode.INVALID_CONFIG, (ConfigValidator.validate(trojan) as ValidationResult.Invalid).error.code)
    }

    @Test fun visionFlowIsAllowedOverVlessEncryptionWithoutTls() {
        val cfg = InternalConnectionConfig.fromProxyMap(linkedMapOf("type" to "vless", "server" to "a.example.com", "port" to 443,
            "uuid" to "u", "flow" to "xtls-rprx-vision", "encryption" to enc))!!
        assertTrue(ConfigValidator.validate(cfg) is ValidationResult.Valid)
    }

    @Test fun theEncryptionKeyNeverAppearsInLogsOrErrors() {
        ConnLog.clear()
        ConnLog.i("c1", Stage.CONFIG_LOADED, "proxy encryption: $enc")
        assertFalse(ConnLog.dump().contains(key))
        assertFalse(ConnectionError.CoreError(ErrorCode.CORE_START_FAILED, "invalid encryption $enc").technical.contains(key))
    }
}
