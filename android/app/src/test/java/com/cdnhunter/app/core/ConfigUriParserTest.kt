package com.cdnhunter.app.core

import com.cdnhunter.app.vpn.ConfigUriParser
import com.cdnhunter.app.vpn.ConfigUriParser.UriParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigUriParserTest {
    private val uuid = "b831381d-6324-4d53-ad4f-8cda48b30811"

    @Test fun malformedLinksNeverThrow() {
        val junk = listOf(
            "", "   ", "vmess://", "vmess://!!!", "vmess://bm90IGpzb24=", "vmess://e30=",
            "vless://", "vless://@", "vless://uuid@", "vless://uuid@host:notaport", "vless://uuid@host:99999",
            "trojan://", "trojan://@:", "ss://", "ss://@", "ss://!!!@", "http://example.com", "random text", "vless:/x",
        )
        for (j in junk) {
            val r = try { ConfigUriParser.parse(j) } catch (e: Throwable) { throw AssertionError("threw on '$j'", e) }
            assertNotNull(r)
            // parseToProxy keeps its old contract: null for anything unusable.
            try { ConfigUriParser.parseToProxy(j) } catch (e: Throwable) { throw AssertionError("parseToProxy threw on '$j'", e) }
        }
    }

    @Test fun badVmessPayloadIsAFailureWithACode() {
        val r = ConfigUriParser.parse("vmess://@@@@") as UriParseResult.Failure
        assertEquals(ErrorCode.INVALID_CONFIG, r.error.code)
    }

    @Test fun unknownSchemeIsUnsupportedNotInvalid() {
        val r = ConfigUriParser.parse("hysteria2://x@host:443") as UriParseResult.Failure
        assertEquals(ErrorCode.UNSUPPORTED_PROTOCOL, r.error.code)
    }

    @Test fun vmessLinkParses() {
        val json = """{"v":"2","ps":"n","add":"a.example.com","port":"443","id":"$uuid","aid":"0","net":"ws","type":"none","host":"h.example.com","path":"/w","tls":"tls"}"""
        val link = "vmess://" + ConfigCodec.base64UrlEncode(json.toByteArray())
        val p = ConfigUriParser.parseToProxy(link)!!
        assertEquals("vmess", p["type"]); assertEquals("a.example.com", p["server"]); assertEquals(443, p["port"]); assertEquals("ws", p["network"])
    }

    @Test fun vlessRealityLinkParsesAndValidates() {
        val link = "vless://$uuid@203.0.113.10:443?type=tcp&security=reality&sni=www.example.com&pbk=${"k".repeat(43)}&sid=6ba85179e30d4fc2&fp=chrome&flow=xtls-rprx-vision#Germany%2001"
        val p = ConfigUriParser.parseToProxy(link)!!
        val cfg = InternalConnectionConfig.fromProxyMap(p)!!
        assertTrue(ConfigValidator.validate(cfg) is ValidationResult.Valid)
    }

    @Test fun unsupportedTransportInALinkIsFlaggedByValidationInsteadOfBecomingTcp() {
        val p = ConfigUriParser.parseToProxy("vless://$uuid@a.example.com:443?type=kcp&security=none")!!
        assertEquals("kcp", p["network"])
        val v = ConfigValidator.validate(InternalConnectionConfig.fromProxyMap(p)!!) as ValidationResult.Invalid
        assertEquals(ErrorCode.UNSUPPORTED_PROTOCOL, v.error.code)
    }

    @Test fun plainTcpAndRawStayTcp() {
        for (t in listOf("tcp", "raw")) {
            val p = ConfigUriParser.parseToProxy("vless://$uuid@a.example.com:443?type=$t&security=none")!!
            assertNull(p["network"])
        }
    }

    @Test fun shadowsocksLinkParses() {
        val userinfo = ConfigCodec.base64UrlEncode("aes-256-gcm:secret".toByteArray())
        val p = ConfigUriParser.parseToProxy("ss://$userinfo@a.example.com:8388#n")!!
        assertEquals("ss", p["type"]); assertEquals("aes-256-gcm", p["cipher"]); assertEquals("secret", p["password"])
    }
}
