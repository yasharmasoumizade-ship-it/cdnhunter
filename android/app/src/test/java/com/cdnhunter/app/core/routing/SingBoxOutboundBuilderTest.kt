package com.cdnhunter.app.core.routing

import com.cdnhunter.app.core.ErrorCode
import com.cdnhunter.app.core.InternalConnectionConfig
import com.cdnhunter.app.vpn.ConfigUriParser
import com.cdnhunter.app.vpn.ConfigUriParser.UriParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SingBoxOutboundBuilderTest {
    private val id = "b831381d-6324-4d53-ad4f-8cda48b30811"
    private val host = "srv.example.com"

    private fun cfg(link: String): InternalConnectionConfig =
        InternalConnectionConfig.fromProxyMap((ConfigUriParser.parse(link) as UriParseResult.Success).proxy)!!

    private fun ok(link: String, allowInsecure: Boolean = false) =
        (SingBoxOutboundBuilder.build(cfg(link), allowInsecure) as SingBoxBuild.Ok).outbound

    private fun err(link: String, allowInsecure: Boolean = false) =
        SingBoxOutboundBuilder.build(cfg(link), allowInsecure) as SingBoxBuild.Err

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.m(k: String) = this[k] as Map<String, Any?>

    @Test fun realityMapsKeyShortIdSniAndFingerprintVerbatim() {
        val o = ok("vless://$id@$host:443?security=reality&sni=www.example.org&pbk=PUBKEY123&sid=ab12&fp=firefox&flow=xtls-rprx-vision&type=tcp#r")
        assertEquals("vless", o["type"]); assertEquals(host, o["server"]); assertEquals(443, o["server_port"])
        assertEquals(id, o["uuid"]); assertEquals("xtls-rprx-vision", o["flow"])
        val tls = o.m("tls")
        assertEquals(true, tls["enabled"]); assertEquals("www.example.org", tls["server_name"])
        assertEquals("PUBKEY123", tls.m("reality")["public_key"]); assertEquals("ab12", tls.m("reality")["short_id"])
        assertEquals(true, tls.m("reality")["enabled"])
        assertEquals("firefox", tls.m("utls")["fingerprint"])
        assertNull("TLS verification must never be switched off", tls["insecure"])
        assertNull("plain tcp has no transport block", o["transport"])
    }

    @Test fun realityWithoutPublicKeyIsAValidationErrorNotABrokenConfig() {
        val e = err("vless://$id@$host:443?security=reality&sni=www.example.org&sid=ab12&type=tcp#r")
        assertEquals(ErrorCode.INVALID_CONFIG, e.code)
    }

    @Test fun errorMessagesNeverCarryCredentialsOrKeys() {
        val e = err("vless://$id@$host:443?security=reality&sni=www.example.org&sid=ab12&type=tcp#r")
        assertFalse(e.message.contains(id)); assertFalse(e.message.contains(host)); assertFalse(e.message.contains("ab12"))
    }

    @Test fun wsKeepsPathAndHostHeader() {
        val t = ok("vless://$id@$host:443?security=tls&sni=$host&type=ws&path=/ws&host=cdn.example.com#w").m("transport")
        assertEquals("ws", t["type"]); assertEquals("/ws", t["path"])
        assertEquals("cdn.example.com", t.m("headers")["Host"])
    }

    @Test fun grpcKeepsServiceName() {
        val t = ok("vless://$id@$host:443?security=tls&sni=$host&type=grpc&serviceName=svc#g").m("transport")
        assertEquals("grpc", t["type"]); assertEquals("svc", t["service_name"])
    }

    @Test fun h2BecomesSingBoxHttpTransportWithTls() {
        val o = ok("vless://$id@$host:443?security=tls&sni=$host&type=h2&path=/p&host=h.example.com#h")
        val t = o.m("transport")
        assertEquals("http", t["type"]); assertEquals("/p", t["path"]); assertEquals(listOf("h.example.com"), t["host"])
        assertEquals(true, o.m("tls")["enabled"])
    }

    @Test fun importedInsecureTlsIsRefusedByDefault() {
        val e = err("vless://$id@$host:443?security=tls&sni=$host&type=ws&path=/&allowInsecure=1#i")
        assertEquals(ErrorCode.INVALID_CONFIG, e.code)
    }

    @Test fun insecureTlsOnlyWhenTheProfileIsExplicitlyApproved() {
        val tls = ok("vless://$id@$host:443?security=tls&sni=$host&type=ws&path=/&allowInsecure=1#i", allowInsecure = true).m("tls")
        assertEquals(true, tls["insecure"])
    }

    @Test fun secureTlsNeverCarriesAnInsecureKey() {
        assertNull(ok("vless://$id@$host:443?security=tls&sni=$host&type=ws&path=/#s").m("tls")["insecure"])
    }

    @Test fun xhttpIsRefusedNotFaked() {
        val e = err("vless://$id@$host:443?security=tls&sni=$host&type=xhttp&path=/x#x")
        assertEquals(ErrorCode.UNSUPPORTED_PROTOCOL, e.code)
    }

    @Test fun vlessEncryptionIsNotSilentlyDropped() {
        val c = cfg("vless://$id@$host:443?security=tls&sni=$host&type=ws&path=/&encryption=mlkem768x25519plus.native.0rtt.KEY#e")
        assertTrue(SingBoxOutboundBuilder.build(c) is SingBoxBuild.Err)
        assertEquals(listOf(CoreType.CLASH_META), (CoreRouter.plan(c) as CorePlan.Route).order)
    }

    @Test fun trojanIsAlwaysTls() {
        val c = InternalConnectionConfig.fromProxyMap(linkedMapOf<String, Any>(
            "name" to "t", "type" to "trojan", "server" to host, "port" to 443, "password" to "pw", "sni" to host, "udp" to true))!!
        val o = (SingBoxOutboundBuilder.build(c) as SingBoxBuild.Ok).outbound
        assertEquals("trojan", o["type"]); assertEquals("pw", o["password"])
        assertEquals(true, o.m("tls")["enabled"]); assertEquals(host, o.m("tls")["server_name"])
    }

    @Test fun shadowsocksMapsMethodAndPassword() {
        val c = InternalConnectionConfig.fromProxyMap(linkedMapOf<String, Any>(
            "name" to "s", "type" to "ss", "server" to host, "port" to 8388, "cipher" to "aes-256-gcm", "password" to "pw", "udp" to true))!!
        val o = (SingBoxOutboundBuilder.build(c) as SingBoxBuild.Ok).outbound
        assertEquals("shadowsocks", o["type"]); assertEquals("aes-256-gcm", o["method"]); assertNull(o["tls"])
    }
}
