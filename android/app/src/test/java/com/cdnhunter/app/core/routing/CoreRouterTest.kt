package com.cdnhunter.app.core.routing

import com.cdnhunter.app.core.ErrorCode
import com.cdnhunter.app.core.InternalConnectionConfig
import com.cdnhunter.app.core.ProxyProtocol
import com.cdnhunter.app.vpn.ConfigUriParser
import com.cdnhunter.app.vpn.ConfigUriParser.UriParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreRouterTest {
    private val id = "b831381d-6324-4d53-ad4f-8cda48b30811"
    private val host = "srv.example.com"

    private fun cfg(link: String): InternalConnectionConfig {
        val r = ConfigUriParser.parse(link) as UriParseResult.Success
        return InternalConnectionConfig.fromProxyMap(r.proxy)!!
    }

    private fun route(link: String) = CoreRouter.plan(cfg(link)) as CorePlan.Route

    private val reality = "vless://$id@$host:443?security=reality&sni=www.example.org&pbk=PUBKEY&sid=ab12&fp=chrome&flow=xtls-rprx-vision&type=tcp#r"

    // A. VLESS + Reality -> sing-box directly
    @Test fun realityGoesStraightToSingBox() {
        val r = route(reality)
        assertEquals(listOf(CoreType.SING_BOX), r.order)
    }

    // K. Reality: Clash Meta must never be in the plan, before or after
    @Test fun realityNeverIncludesClashMeta() {
        for (t in listOf("tcp", "grpc", "h2")) {
            val extra = if (t == "grpc") "&serviceName=svc" else if (t == "h2") "&path=/p&host=$host" else ""
            val r = route("vless://$id@$host:443?security=reality&sni=www.example.org&pbk=PUBKEY&sid=ab12&type=$t$extra#r")
            assertTrue("reality/$t", CoreType.CLASH_META !in r.order)
            assertEquals(listOf(CoreType.SING_BOX), r.order)
        }
    }

    // B. VLESS + TLS + WS
    @Test fun tlsWsIsClashFirstThenSingBox() {
        val r = route("vless://$id@$host:443?security=tls&sni=$host&type=ws&path=/ws&host=$host#w")
        assertEquals(listOf(CoreType.CLASH_META, CoreType.SING_BOX), r.order)
    }

    // C. VLESS + TLS + gRPC
    @Test fun tlsGrpcIsClashFirstThenSingBox() {
        val r = route("vless://$id@$host:443?security=tls&sni=$host&type=grpc&serviceName=svc#g")
        assertEquals(listOf(CoreType.CLASH_META, CoreType.SING_BOX), r.order)
    }

    // E. VLESS + TLS + HTTP (HTTP/2): both engines carry it
    @Test fun tlsH2IsClashFirstThenSingBox() {
        val r = route("vless://$id@$host:443?security=tls&sni=$host&type=h2&path=/p&host=$host#h")
        assertEquals(listOf(CoreType.CLASH_META, CoreType.SING_BOX), r.order)
    }

    // E'. TCP + HTTP-header obfuscation is NOT HTTP/2: sing-box cannot do it, so no fallback exists.
    @Test fun httpHeaderObfuscationIsClashOnly() {
        val r = route("vless://$id@$host:80?security=none&type=tcp&headerType=http&host=$host&path=/#o")
        assertEquals(listOf(CoreType.CLASH_META), r.order)
    }

    // D/F. HTTPUpgrade and QUIC are not mapped by the parser yet: they are refused, never routed blindly.
    @Test fun transportsTheParserDoesNotMapYetAreRefusedByTheProfile() {
        val p = ConnectionProfile(ProxyProtocol.VLESS, SecurityKind.TLS, TransportKind.UNKNOWN)
        assertTrue(CoreRouter.plan(p) is CorePlan.Unsupported)
    }

    // F. QUIC (when the parser maps it): Clash Meta cannot carry it, so it goes to sing-box ONLY
    @Test fun quicWithTlsIsSingBoxOnly() {
        val p = ConnectionProfile(ProxyProtocol.VLESS, SecurityKind.TLS, TransportKind.QUIC)
        assertEquals(listOf(CoreType.SING_BOX), (CoreRouter.plan(p) as CorePlan.Route).order)
    }

    @Test fun quicWithoutTlsIsUnsupported() {
        val p = ConnectionProfile(ProxyProtocol.VLESS, SecurityKind.NONE, TransportKind.QUIC)
        assertTrue(CoreRouter.plan(p) is CorePlan.Unsupported)
    }

    // D. HTTPUpgrade: both engines
    @Test fun httpUpgradeIsClashFirstThenSingBox() {
        val p = ConnectionProfile(ProxyProtocol.VLESS, SecurityKind.TLS, TransportKind.HTTPUPGRADE)
        assertEquals(listOf(CoreType.CLASH_META, CoreType.SING_BOX), (CoreRouter.plan(p) as CorePlan.Route).order)
    }

    // G. sing-box 1.14.2 has no XHTTP: no fake equivalent, Clash Meta alone
    @Test fun xhttpIsClashOnlyAndNeverFakedOnSingBox() {
        val p = ConnectionProfile(ProxyProtocol.VLESS, SecurityKind.TLS, TransportKind.XHTTP)
        assertEquals(listOf(CoreType.CLASH_META), (CoreRouter.plan(p) as CorePlan.Route).order)
        assertTrue(CoreCapabilities.supports(CoreType.SING_BOX, p) is Support.No)
    }

    // G. nothing can carry it -> a clear error, and no plan to start anything
    @Test fun unknownTransportIsAClearError() {
        val p = ConnectionProfile(ProxyProtocol.VMESS, SecurityKind.TLS, TransportKind.UNKNOWN)
        val u = CoreRouter.plan(p) as CorePlan.Unsupported
        assertEquals(ErrorCode.UNSUPPORTED_PROTOCOL, u.code)
    }

    @Test fun realityOnAnUnsupportedTransportOnlyUsesTheEngineThatCanCarryIt() {
        val p = ConnectionProfile(ProxyProtocol.VLESS, SecurityKind.REALITY, TransportKind.XHTTP)
        val r = CoreRouter.plan(p) as CorePlan.Route
        assertEquals(listOf(CoreType.CLASH_META), r.order)
        assertNull(r.after(CoreType.CLASH_META))
    }

    @Test fun realityOverWebSocketHasNoEngine() {
        val p = ConnectionProfile(ProxyProtocol.VLESS, SecurityKind.REALITY, TransportKind.WS)
        assertTrue(CoreRouter.plan(p) is CorePlan.Unsupported)
    }

    // I/J/17. The sequence is bounded: each core once, never back, never a third try
    @Test fun fallbackHappensExactlyOnceAndNeverLoops() {
        val r = route("vless://$id@$host:443?security=tls&sni=$host&type=ws&path=/ws&host=$host#w")
        assertEquals(CoreType.CLASH_META, r.first)
        assertEquals(CoreType.SING_BOX, r.after(CoreType.CLASH_META))
        assertNull(r.after(CoreType.SING_BOX))
    }

    @Test fun routeSingleCoreHasNoFallback() {
        val r = route(reality)
        assertNull(r.after(CoreType.SING_BOX))
    }

    // Detection must not depend on how the config was spelled: same profile from every representation
    @Test fun profileDoesNotDependOnServerOrCredentials() {
        val a = ConnectionProfile.of(cfg("vless://$id@a.example.com:443?security=tls&sni=a.example.com&type=ws&path=/x&host=a.example.com#a"))
        val b = ConnectionProfile.of(cfg("vless://${id.replace('b', 'c')}@b.example.com:8443?security=tls&sni=b.example.com&type=ws&path=/y&host=b.example.com#b"))
        assertEquals(a, b)
    }

    @Test fun realityWithMissingKeyIsStillRecognisedAsReality() {
        // Incomplete REALITY must be caught by validation, never silently demoted to plain TLS.
        val c = cfg("vless://$id@$host:443?security=reality&sni=www.example.org&sid=ab12&type=tcp#r")
        assertEquals(SecurityKind.REALITY, ConnectionProfile.of(c).security)
    }
}
