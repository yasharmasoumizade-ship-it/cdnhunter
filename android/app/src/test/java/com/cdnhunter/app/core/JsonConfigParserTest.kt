package com.cdnhunter.app.core

import com.cdnhunter.app.core.json.JsonConfigParser
import com.cdnhunter.app.core.json.JsonParseResult
import com.cdnhunter.app.core.json.JsonSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonConfigParserTest {

    private val uuid = "b831381d-6324-4d53-ad4f-8cda48b30811"
    private val pbk = "k".repeat(43)

    private fun ok(json: String) = JsonConfigParser.parse(json) as JsonParseResult.Success
    private fun failCode(json: String) = (JsonConfigParser.parse(json) as JsonParseResult.Failure).error.code

    private val xrayReality = """
    { "log": {"loglevel":"warning"}, "inbounds": [ {"port":10808,"protocol":"socks"} ],
      "outbounds": [
        { "tag":"proxy","protocol":"vless",
          "settings": { "vnext": [ { "address":"203.0.113.10","port":443,
             "users":[ {"id":"$uuid","flow":"xtls-rprx-vision","encryption":"none"} ] } ] },
          "streamSettings": { "network":"tcp","security":"reality",
             "realitySettings": {"serverName":"www.example.com","publicKey":"$pbk","shortId":"6ba85179e30d4fc2","fingerprint":"chrome"} } },
        { "tag":"direct","protocol":"freedom" },
        { "tag":"block","protocol":"blackhole" } ] }
    """

    @Test fun xrayVlessRealityIsParsedAndPlumbingOutboundsAreSkipped() {
        val r = ok(xrayReality)
        assertEquals(JsonSchema.XRAY, r.schema)
        assertEquals(1, r.proxies.size)
        assertEquals(2, r.skipped.size)
        val p = r.proxies[0].config.proxy
        assertEquals("vless", p["type"]); assertEquals("203.0.113.10", p["server"]); assertEquals(443, p["port"])
        assertEquals(uuid, p["uuid"]); assertEquals("xtls-rprx-vision", p["flow"])
        assertEquals("www.example.com", p["servername"]); assertEquals("chrome", p["client-fingerprint"])
        val reality = p["reality-opts"] as Map<*, *>
        assertEquals(pbk, reality["public-key"]); assertEquals("6ba85179e30d4fc2", reality["short-id"])
    }

    @Test fun xrayVmessWebSocketTls() {
        val r = ok("""{"outbounds":[{"tag":"a","protocol":"vmess","settings":{"vnext":[{"address":"cdn.example.com","port":"443",
          "users":[{"id":"$uuid","alterId":0,"security":"auto"}]}]},
          "streamSettings":{"network":"ws","security":"tls","tlsSettings":{"serverName":"cdn.example.com","alpn":["h2","http/1.1"]},
          "wsSettings":{"path":"/ws","headers":{"Host":"cdn.example.com"}}}}]}""")
        val p = r.proxies.single().config.proxy
        assertEquals("vmess", p["type"]); assertEquals(443, p["port"]); assertEquals("ws", p["network"]); assertEquals(true, p["tls"])
        val ws = p["ws-opts"] as Map<*, *>
        assertEquals("/ws", ws["path"]); assertEquals("cdn.example.com", (ws["headers"] as Map<*, *>)["Host"])
    }

    @Test fun xrayTrojanAndShadowsocksAndFlatSettings() {
        val r = ok("""[
          {"tag":"t","protocol":"trojan","settings":{"servers":[{"address":"t.example.com","port":443,"password":"pw"}]},
           "streamSettings":{"network":"tcp","security":"tls","tlsSettings":{"serverName":"t.example.com"}}},
          {"tag":"s","protocol":"shadowsocks","settings":{"servers":[{"address":"s.example.com","port":8388,"method":"aes-256-gcm","password":"pw"}]}},
          {"tag":"f","protocol":"vless","settings":{"address":"f.example.com","port":443,"id":"$uuid"}} ]""")
        assertEquals(3, r.proxies.size)
        assertEquals("t.example.com", r.proxies[0].config.proxy["sni"])
        assertEquals("aes-256-gcm", r.proxies[1].config.proxy["cipher"])
        assertEquals("f.example.com", r.proxies[2].config.proxy["server"])
    }

    @Test fun singBoxVlessReality() {
        val r = ok("""{"outbounds":[
          {"type":"vless","tag":"p","server":"203.0.113.10","server_port":443,"uuid":"$uuid","flow":"xtls-rprx-vision",
           "tls":{"enabled":true,"server_name":"www.example.com","utls":{"enabled":true,"fingerprint":"chrome"},
                  "reality":{"enabled":true,"public_key":"$pbk","short_id":"6ba85179e30d4fc2"}}},
          {"type":"selector","tag":"sel","outbounds":["p"]}, {"type":"direct","tag":"direct"} ]}""")
        assertEquals(JsonSchema.SING_BOX, r.schema)
        val p = r.proxies.single().config.proxy
        assertEquals("203.0.113.10", p["server"]); assertEquals("chrome", p["client-fingerprint"])
        assertEquals(pbk, (p["reality-opts"] as Map<*, *>)["public-key"])
        assertEquals(2, r.skipped.size)
    }

    @Test fun singBoxGrpcAndWsTransports() {
        val r = ok("""{"outbounds":[
          {"type":"trojan","server":"a.example.com","server_port":443,"password":"pw","tls":{"enabled":true,"server_name":"a.example.com"},
           "transport":{"type":"grpc","service_name":"svc"}},
          {"type":"vmess","server":"b.example.com","server_port":80,"uuid":"$uuid","transport":{"type":"ws","path":"/p","headers":{"Host":"h.example.com"}}} ]}""")
        assertEquals("grpc", r.proxies[0].config.proxy["network"])
        assertEquals("svc", (r.proxies[0].config.proxy["grpc-opts"] as Map<*, *>)["grpc-service-name"])
        assertEquals("/p", (r.proxies[1].config.proxy["ws-opts"] as Map<*, *>)["path"])
    }

    @Test fun clashJsonIsWhitelistedNotPassedThrough() {
        val r = ok("""{"proxies":[{"name":"n","type":"vless","server":"a.example.com","port":443,"uuid":"$uuid","tls":true,
          "network":"ws","ws-opts":{"path":"/x","headers":{"Host":"h.example.com"}},
          "evil-key":"rules:\n - MATCH,DIRECT","dialer-proxy":"x","interface-name":"eth0"}]}""")
        assertEquals(JsonSchema.CLASH, r.schema)
        val p = r.proxies.single().config.proxy
        assertFalse(p.containsKey("evil-key")); assertFalse(p.containsKey("dialer-proxy")); assertFalse(p.containsKey("interface-name"))
        assertEquals("/x", (p["ws-opts"] as Map<*, *>)["path"])
    }

    @Test fun singleOutboundAtTheRootIsAccepted() {
        assertEquals(1, ok("""{"protocol":"trojan","settings":{"servers":[{"address":"a.example.com","port":443,"password":"pw"}]}}""").proxies.size)
        assertEquals(1, ok("""{"type":"trojan","server":"a.example.com","server_port":443,"password":"pw"}""").proxies.size)
    }

    // ── bad input ────────────────────────────────────────────────────────────

    @Test fun invalidJsonIsReportedNotThrown() {
        for (bad in listOf("", "   ", "not json", "{", "{\"a\":", "[1,2", "{\"a\":1} trailing", "{'single':'quotes'}x"))
            assertEquals("input: $bad", ErrorCode.INVALID_JSON, failCode(bad))
    }

    @Test fun deeplyNestedDocumentIsRefusedBeforeItCanOverflowTheStack() {
        val bomb = "[".repeat(100_000) + "]".repeat(100_000)
        assertEquals(ErrorCode.INVALID_JSON, failCode(bomb))
    }

    @Test fun oversizedDocumentIsRefused() {
        val big = """{"outbounds":[{"protocol":"freedom","tag":"${"x".repeat(JsonConfigParser.MAX_BYTES)}"}]}"""
        assertEquals(ErrorCode.INVALID_JSON, failCode(big))
    }

    @Test fun unknownSchemaIsRefusedNotGuessed() {
        assertEquals(ErrorCode.INVALID_CONFIG, failCode("""{"hello":"world"}"""))
        assertEquals(ErrorCode.INVALID_CONFIG, failCode("[]"))
        assertEquals(ErrorCode.INVALID_CONFIG, failCode("""{"outbounds":[]}"""))
    }

    @Test fun configWithOnlyPlumbingHasNoProxyToConnectTo() {
        assertEquals(ErrorCode.INVALID_CONFIG, failCode("""{"outbounds":[{"protocol":"freedom","tag":"d"}]}"""))
    }

    @Test fun missingFieldsGiveSpecificCodes() {
        fun vless(user: String, addr: String = "a.example.com", port: String = "443") =
            """{"outbounds":[{"protocol":"vless","settings":{"vnext":[{"address":"$addr","port":$port,"users":[$user]}]}}]}"""
        assertEquals(ErrorCode.INVALID_CREDENTIALS, failCode(vless("""{"encryption":"none"}""")))
        assertEquals(ErrorCode.INVALID_SERVER, failCode(vless("""{"id":"$uuid"}""", addr = "bad host!")))
        assertEquals(ErrorCode.INVALID_PORT, failCode(vless("""{"id":"$uuid"}""", port = "70000")))
        assertEquals(ErrorCode.INVALID_PORT, failCode("""{"outbounds":[{"protocol":"vless","settings":{"vnext":[{"address":"a.example.com","users":[{"id":"$uuid"}]}]}}]}"""))
    }

    @Test fun unsupportedProtocolsAndTransportsAreNamed() {
        assertEquals(ErrorCode.UNSUPPORTED_PROTOCOL, failCode("""{"outbounds":[{"protocol":"wireguard","settings":{}}]}"""))
        assertEquals(ErrorCode.UNSUPPORTED_PROTOCOL, failCode("""{"outbounds":[{"type":"hysteria2","server":"a.example.com","server_port":443}]}"""))
        assertEquals(ErrorCode.UNSUPPORTED_PROTOCOL, failCode(
            """{"outbounds":[{"protocol":"vless","settings":{"vnext":[{"address":"a.example.com","port":443,"users":[{"id":"$uuid"}]}]},"streamSettings":{"network":"kcp"}}]}"""))
        assertEquals(ErrorCode.UNSUPPORTED_PROTOCOL, failCode(
            """{"outbounds":[{"protocol":"vless","settings":{"vnext":[{"address":"a.example.com","port":443,"users":[{"id":"$uuid","encryption":"some-future-scheme.native.0rtt.xxx"}]}]}}]}"""))
    }

    @Test fun oneBadServerDoesNotSinkTheOthers() {
        val r = ok("""{"proxies":[
          {"name":"good","type":"trojan","server":"a.example.com","port":443,"password":"pw"},
          {"name":"badport","type":"trojan","server":"b.example.com","port":0,"password":"pw"},
          {"name":"hy","type":"hysteria2","server":"c.example.com","port":443} ]}""")
        assertEquals(listOf("good"), r.proxies.map { it.name })
        assertEquals(2, r.skipped.size)
    }

    @Test fun errorTextNeverContainsTheCredentialsThatWereInTheInput() {
        val err = (JsonConfigParser.parse("""{"outbounds":[{"protocol":"vless","settings":{"vnext":[{"address":"bad host","port":443,"users":[{"id":"$uuid"}]}]}}]}""")
            as JsonParseResult.Failure).error
        assertFalse(err.technical.contains(uuid))
        val syntax = (JsonConfigParser.parse("""{"id":"$uuid" "x"}""") as JsonParseResult.Failure).error
        assertFalse(syntax.technical.contains(uuid))
    }

    @Test fun controlCharactersInJsonValuesAreRejectedByValidation() {
        assertEquals(ErrorCode.INVALID_CONFIG, failCode(
            """{"protocol":"vless","settings":{"address":"a.example.com","port":443,"id":"$uuid"},
               "streamSettings":{"network":"ws","wsSettings":{"path":"/a\nrules:\n  - MATCH,DIRECT"}}}"""))
    }

    @Test fun looksLikeJsonDetection() {
        assertTrue(JsonConfigParser.looksLikeJson("  {\"a\":1}")); assertTrue(JsonConfigParser.looksLikeJson("\uFEFF[1]"))
        assertFalse(JsonConfigParser.looksLikeJson("vless://x")); assertFalse(JsonConfigParser.looksLikeJson("https://sub.example.com/x"))
        assertNull(null)
    }
}
