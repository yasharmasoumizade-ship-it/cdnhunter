package com.cdnhunter.app.core.routing

import com.cdnhunter.app.core.ErrorCode
import com.cdnhunter.app.core.InternalConnectionConfig
import com.cdnhunter.app.core.ProxyProtocol

sealed class SingBoxBuild {
    class Ok(val outbound: Map<String, Any?>) : SingBoxBuild()

    /** [message] names fields only; it never contains a credential, key or address. */
    class Err(val code: ErrorCode, val message: String) : SingBoxBuild()
}

/**
 * The sing-box side of the adapter pair: ONE validated [InternalConnectionConfig] in, one sing-box
 * 1.14.2 outbound out. It translates; it never repairs. A value that is missing stays missing and the
 * result is an error — no default is invented, and REALITY key / short id / SNI / fingerprint are copied
 * verbatim or refused, never adjusted to "make it connect".
 *
 * Schema: SagerNet/sing-box v1.14.2 docs (outbound/vless|vmess|trojan|shadowsocks, shared/tls, shared/v2ray-transport).
 */
object SingBoxOutboundBuilder {
    const val TAG = "proxy"

    /**
     * [allowInsecureTls] is false unless the user has explicitly approved this profile. An imported
     * `skip-cert-verify` is NOT carried over by default: certificate verification is never switched off
     * as a side effect of routing a config to another engine.
     */
    fun build(config: InternalConnectionConfig, allowInsecureTls: Boolean = false): SingBoxBuild {
        val profile = ConnectionProfile.of(config)
        val support = CoreCapabilities.supports(CoreType.SING_BOX, profile)
        if (support is Support.No) return SingBoxBuild.Err(ErrorCode.UNSUPPORTED_PROTOCOL, "sing-box cannot carry this profile: ${support.reason}")

        val p = config.proxy
        val server = p["server"]?.toString()?.trim().orEmpty()
        val port = (p["port"] as? Number)?.toInt() ?: p["port"]?.toString()?.toIntOrNull()
        if (server.isEmpty()) return err(ErrorCode.INVALID_SERVER, "server is missing")
        if (port == null || port !in 1..65535) return err(ErrorCode.INVALID_PORT, "port is missing or out of range")

        val out = linkedMapOf<String, Any?>("tag" to TAG, "server" to server, "server_port" to port)

        when (config.protocol) {
            ProxyProtocol.VLESS -> {
                out["type"] = "vless"
                out["uuid"] = required(p, "uuid") ?: return err(ErrorCode.INVALID_CREDENTIALS, "uuid is missing")
                p["flow"]?.toString()?.takeIf { it.isNotBlank() }?.let { out["flow"] = it }
            }
            ProxyProtocol.VMESS -> {
                out["type"] = "vmess"
                out["uuid"] = required(p, "uuid") ?: return err(ErrorCode.INVALID_CREDENTIALS, "uuid is missing")
                out["security"] = p["cipher"]?.toString()?.takeIf { it.isNotBlank() } ?: "auto"
                out["alter_id"] = (p["alterId"] as? Number)?.toInt() ?: 0
            }
            ProxyProtocol.TROJAN -> {
                out["type"] = "trojan"
                out["password"] = required(p, "password") ?: return err(ErrorCode.INVALID_CREDENTIALS, "password is missing")
            }
            ProxyProtocol.SHADOWSOCKS -> {
                out["type"] = "shadowsocks"
                out["method"] = required(p, "cipher") ?: return err(ErrorCode.INVALID_CONFIG, "cipher is missing")
                out["password"] = required(p, "password") ?: return err(ErrorCode.INVALID_CREDENTIALS, "password is missing")
            }
        }

        // TLS: trojan is always TLS; vless / vmess when the profile says so. Shadowsocks has none.
        val tlsOn = config.protocol == ProxyProtocol.TROJAN ||
            (config.protocol != ProxyProtocol.SHADOWSOCKS && profile.security != SecurityKind.NONE)
        if (tlsOn) {
            val tls = linkedMapOf<String, Any?>("enabled" to true)
            val sni = (p["servername"] ?: p["sni"])?.toString()?.trim().orEmpty()
            if (sni.isNotEmpty()) tls["server_name"] = sni
            (p["alpn"] as? List<*>)?.map { it.toString() }?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }?.let { tls["alpn"] = it }

            if (p["skip-cert-verify"] == true) {
                if (!allowInsecureTls) {
                    return err(ErrorCode.INVALID_CONFIG, "this profile asks to skip certificate verification; sing-box will not do that unless the profile is explicitly approved")
                }
                tls["insecure"] = true
            }

            val fp = p["client-fingerprint"]?.toString()?.trim().orEmpty()
            if (profile.security == SecurityKind.REALITY) {
                val ro = p["reality-opts"] as? Map<*, *> ?: return err(ErrorCode.INVALID_CONFIG, "REALITY options are missing")
                val pbk = ro["public-key"]?.toString()?.trim().orEmpty()
                if (pbk.isEmpty()) return err(ErrorCode.INVALID_CONFIG, "REALITY public key is missing")
                if (sni.isEmpty()) return err(ErrorCode.INVALID_CONFIG, "REALITY server name (SNI) is missing")
                if (fp.isEmpty()) return err(ErrorCode.INVALID_CONFIG, "REALITY needs a client fingerprint")
                tls["utls"] = linkedMapOf("enabled" to true, "fingerprint" to fp)
                tls["reality"] = linkedMapOf("enabled" to true, "public_key" to pbk, "short_id" to (ro["short-id"]?.toString() ?: ""))
            } else if (fp.isNotEmpty()) {
                tls["utls"] = linkedMapOf("enabled" to true, "fingerprint" to fp)
            }
            out["tls"] = tls
        }

        val transport = transportOf(profile.transport, p, tlsOn) ?: return err(ErrorCode.UNSUPPORTED_PROTOCOL, "transport ${profile.transport} is not translatable for sing-box")
        if (transport is String) return err(ErrorCode.INVALID_CONFIG, transport)
        @Suppress("UNCHECKED_CAST")
        if (transport is Map<*, *> && transport.isNotEmpty()) out["transport"] = transport as Map<String, Any?>

        return SingBoxBuild.Ok(out)
    }

    /** A Map = the transport block ({} for plain TCP), a String = a validation message, null = not translatable. */
    private fun transportOf(t: TransportKind, p: Map<String, Any>, tlsOn: Boolean): Any? = when (t) {
        TransportKind.TCP -> emptyMap<String, Any?>()
        TransportKind.WS -> {
            val ws = p["ws-opts"] as? Map<*, *>
            val m = linkedMapOf<String, Any?>("type" to "ws", "path" to (ws?.get("path")?.toString() ?: "/"))
            headersOf(ws)?.let { m["headers"] = it }
            (ws?.get("max-early-data") as? Number)?.toInt()?.takeIf { it > 0 }?.let { m["max_early_data"] = it }
            ws?.get("early-data-header-name")?.toString()?.takeIf { it.isNotBlank() }?.let { m["early_data_header_name"] = it }
            m
        }
        TransportKind.HTTPUPGRADE -> {
            val ws = p["ws-opts"] as? Map<*, *>
            val headers = headersOf(ws)
            val host = headers?.get("Host")
            val rest = headers?.filterKeys { !it.equals("Host", ignoreCase = true) }
            val m = linkedMapOf<String, Any?>("type" to "httpupgrade", "path" to (ws?.get("path")?.toString() ?: "/"))
            if (!host.isNullOrBlank()) m["host"] = host
            if (!rest.isNullOrEmpty()) m["headers"] = rest
            m
        }
        TransportKind.GRPC -> {
            val g = p["grpc-opts"] as? Map<*, *>
            linkedMapOf<String, Any?>("type" to "grpc", "service_name" to (g?.get("grpc-service-name")?.toString() ?: ""))
        }
        TransportKind.H2 -> if (!tlsOn) "the HTTP/2 transport requires TLS" else {
            val h = p["h2-opts"] as? Map<*, *>
            val m = linkedMapOf<String, Any?>("type" to "http")
            (h?.get("host") as? List<*>)?.map { it.toString() }?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }?.let { m["host"] = it }
            m["path"] = ((h?.get("path") as? List<*>)?.firstOrNull() ?: h?.get("path"))?.toString() ?: "/"
            m
        }
        TransportKind.QUIC -> if (!tlsOn) "the QUIC transport requires TLS" else linkedMapOf<String, Any?>("type" to "quic")
        TransportKind.XHTTP, TransportKind.HTTP_OBFS, TransportKind.UNKNOWN -> null
    }

    private fun headersOf(ws: Map<*, *>?): Map<String, String>? =
        (ws?.get("headers") as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value.toString() }?.takeIf { it.isNotEmpty() }

    private fun required(p: Map<String, Any>, key: String): String? = p[key]?.toString()?.takeIf { it.isNotBlank() }

    private fun err(code: ErrorCode, msg: String) = SingBoxBuild.Err(code, msg)
}
