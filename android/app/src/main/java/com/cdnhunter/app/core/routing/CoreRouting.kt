package com.cdnhunter.app.core.routing

import com.cdnhunter.app.core.ErrorCode
import com.cdnhunter.app.core.InternalConnectionConfig
import com.cdnhunter.app.core.ProxyProtocol

/** The engines that can carry a connection. The UI never sees this; it lives in the connection layer. */
enum class CoreType { CLASH_META, SING_BOX }

enum class SecurityKind { NONE, TLS, REALITY }

/**
 * The stream transport as the CONNECTION needs it, not as a share link spells it.
 * [HTTP_OBFS] is TCP with a fake HTTP header (v2ray "headerType=http"); [H2] is real HTTP/2.
 */
enum class TransportKind { TCP, WS, GRPC, H2, HTTP_OBFS, XHTTP, HTTPUPGRADE, QUIC, UNKNOWN }

/**
 * What decides which core runs: derived ONLY from the normalized, validated config —
 * never from a UI label, a user pick or a guess. Two configs that differ only in server,
 * port or credentials have the same profile.
 */
data class ConnectionProfile(
    val protocol: ProxyProtocol,
    val security: SecurityKind,
    val transport: TransportKind,
) {
    companion object {
        fun of(config: InternalConnectionConfig): ConnectionProfile {
            val p = config.proxy
            // Every input format (share URI, Xray / sing-box / Clash JSON) is converted to the same
            // core-shaped map before this point, so REALITY is recognised by one thing: reality-opts.
            val security = when {
                p["reality-opts"] is Map<*, *> -> SecurityKind.REALITY
                p["tls"] == true -> SecurityKind.TLS
                else -> SecurityKind.NONE
            }
            val transport = when (p["network"]?.toString()?.trim()?.lowercase().orEmpty()) {
                "", "tcp", "raw" -> TransportKind.TCP
                "ws" -> if ((p["ws-opts"] as? Map<*, *>)?.get("v2ray-http-upgrade") == true) TransportKind.HTTPUPGRADE else TransportKind.WS
                "httpupgrade" -> TransportKind.HTTPUPGRADE
                "grpc" -> TransportKind.GRPC
                "h2" -> TransportKind.H2
                "http" -> TransportKind.HTTP_OBFS
                "xhttp", "splithttp" -> TransportKind.XHTTP
                "quic" -> TransportKind.QUIC
                else -> TransportKind.UNKNOWN
            }
            return ConnectionProfile(config.protocol, security, transport)
        }
    }
}

sealed class Support {
    object Yes : Support()
    class No(val reason: String) : Support()
}

/**
 * What each engine can ACTUALLY carry. Nothing here is "the parser accepts the name": each
 * row is a claim about the engine's own implementation, so a wrong row is a wrong route.
 *
 *  - sing-box 1.14.2 V2Ray transports (docs/configuration/shared/v2ray-transport.md): http (= HTTP/2),
 *    ws, quic, grpc, httpupgrade. No XHTTP, and no TCP+HTTP-header obfuscation.
 *  - mihomo 1.19.x: tcp, ws (+ httpupgrade via ws-opts), grpc, h2, http (header obfuscation), xhttp
 *    (VLESS). No V2Ray QUIC transport — its QUIC protocols are Hysteria/TUIC, different things.
 *  - REALITY in sing-box is a VLESS TLS mode and runs over tcp / http(h2) / grpc.
 */
object CoreCapabilities {

    fun supports(core: CoreType, p: ConnectionProfile): Support = when (core) {
        CoreType.CLASH_META -> clash(p)
        CoreType.SING_BOX -> singBox(p)
    }

    private fun clash(p: ConnectionProfile): Support {
        if (p.protocol == ProxyProtocol.SHADOWSOCKS && p.transport != TransportKind.TCP) {
            return Support.No("shadowsocks with a stream transport is not supported")
        }
        return when (p.transport) {
            TransportKind.TCP, TransportKind.WS, TransportKind.GRPC, TransportKind.H2,
            TransportKind.HTTP_OBFS, TransportKind.HTTPUPGRADE -> Support.Yes
            TransportKind.XHTTP ->
                if (p.protocol == ProxyProtocol.VLESS) Support.Yes else Support.No("XHTTP is only available for VLESS")
            TransportKind.QUIC -> Support.No("Clash Meta has no V2Ray QUIC transport")
            TransportKind.UNKNOWN -> Support.No("unknown transport")
        }
    }

    private fun singBox(p: ConnectionProfile): Support {
        if (p.protocol == ProxyProtocol.SHADOWSOCKS && p.transport != TransportKind.TCP) {
            return Support.No("shadowsocks with a stream transport is not supported")
        }
        if (p.security == SecurityKind.REALITY) {
            if (p.protocol != ProxyProtocol.VLESS) return Support.No("REALITY is only available for VLESS")
            if (p.transport !in REALITY_TRANSPORTS) return Support.No("sing-box runs REALITY over tcp, http(h2) or grpc only")
            return Support.Yes
        }
        return when (p.transport) {
            TransportKind.TCP, TransportKind.WS, TransportKind.GRPC, TransportKind.H2,
            TransportKind.HTTPUPGRADE -> Support.Yes
            TransportKind.QUIC ->
                if (p.security == SecurityKind.TLS) Support.Yes else Support.No("the QUIC transport requires TLS")
            TransportKind.XHTTP -> Support.No("sing-box 1.14.2 has no XHTTP transport")
            TransportKind.HTTP_OBFS -> Support.No("sing-box has no TCP HTTP-header obfuscation (its \"http\" transport is HTTP/2)")
            TransportKind.UNKNOWN -> Support.No("unknown transport")
        }
    }

    private val REALITY_TRANSPORTS = setOf(TransportKind.TCP, TransportKind.GRPC, TransportKind.H2)
}

/**
 * The bounded engine sequence for ONE connection attempt: at most two entries, each core at
 * most once. There is no "third try" and no way back to an engine already used, so a fallback
 * loop is impossible by construction. A new attempt (a new Connect tap) builds a new plan.
 */
sealed class CorePlan {
    class Route(val order: List<CoreType>, val reason: String) : CorePlan() {
        init {
            require(order.isNotEmpty() && order.size <= 2 && order.distinct().size == order.size) { "a plan is 1..2 distinct cores" }
        }
        val first: CoreType get() = order.first()

        /** The engine to try after [failed] failed for real, or null when the sequence is exhausted. */
        fun after(failed: CoreType): CoreType? {
            val i = order.indexOf(failed)
            return if (i < 0) null else order.getOrNull(i + 1)
        }
    }

    class Unsupported(val code: ErrorCode, val reason: String) : CorePlan()
}

object CoreRouter {

    fun plan(config: InternalConnectionConfig): CorePlan = plan(ConnectionProfile.of(config))

    fun plan(p: ConnectionProfile): CorePlan {
        val singBox = CoreCapabilities.supports(CoreType.SING_BOX, p)
        val clash = CoreCapabilities.supports(CoreType.CLASH_META, p)

        if (p.security == SecurityKind.REALITY) {
            // REALITY goes straight to sing-box and never visits Clash Meta first. If sing-box cannot
            // carry this particular REALITY combination, the only engine that can is used alone — and
            // there is no fallback in either case.
            return when {
                singBox is Support.Yes -> CorePlan.Route(listOf(CoreType.SING_BOX), "REALITY: sing-box directly")
                clash is Support.Yes -> CorePlan.Route(
                    listOf(CoreType.CLASH_META),
                    "REALITY over ${p.transport}: sing-box cannot carry it (${(singBox as Support.No).reason}); Clash Meta only",
                )
                else -> unsupported(p, singBox, clash)
            }
        }

        return when {
            clash is Support.Yes && singBox is Support.Yes ->
                CorePlan.Route(listOf(CoreType.CLASH_META, CoreType.SING_BOX), "Clash Meta first, sing-box on a real failure")
            clash is Support.Yes ->
                CorePlan.Route(listOf(CoreType.CLASH_META), "Clash Meta only (${(singBox as Support.No).reason})")
            singBox is Support.Yes ->
                CorePlan.Route(listOf(CoreType.SING_BOX), "sing-box only (${(clash as Support.No).reason})")
            else -> unsupported(p, singBox, clash)
        }
    }

    private fun unsupported(p: ConnectionProfile, singBox: Support, clash: Support): CorePlan.Unsupported {
        val why = listOfNotNull(
            (clash as? Support.No)?.let { "Clash Meta: ${it.reason}" },
            (singBox as? Support.No)?.let { "sing-box: ${it.reason}" },
        ).joinToString("; ")
        return CorePlan.Unsupported(
            ErrorCode.UNSUPPORTED_PROTOCOL,
            "no engine can carry ${p.protocol.wire}/${p.security}/${p.transport} ($why)",
        )
    }
}
