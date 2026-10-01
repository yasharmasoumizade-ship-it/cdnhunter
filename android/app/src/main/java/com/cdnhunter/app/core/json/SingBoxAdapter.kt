package com.cdnhunter.app.core.json

import com.cdnhunter.app.core.ErrorCode
import com.cdnhunter.app.vpn.ConfigUriParser
import org.json.JSONObject

/**
 * sing-box outbound objects:
 *
 *     { "type": "vless", "tag": "...", "server": "...", "server_port": 443, "uuid": "...",
 *       "flow": "...", "tls": { "enabled", "server_name", "insecure", "alpn",
 *                               "utls": { "fingerprint" }, "reality": { "enabled", "public_key", "short_id" } },
 *       "transport": { "type": "ws" | "grpc" | "http", ... } }
 *
 * Like the Xray adapter, transport/TLS options are expressed in the share-link parameter
 * vocabulary and translated by [ConfigUriParser.applyTransport].
 */
internal object SingBoxAdapter {

    fun adapt(ob: JSONObject): Adapted {
        val type = (ob.str("type") ?: return rejected("?", ErrorCode.INVALID_CONFIG, "outbound has no 'type'", "type")).lowercase()
        val tag = ob.str("tag") ?: type
        if (type in NON_PROXY_TYPES) return Adapted.NotAProxy(tag, "'$type' is not a proxy outbound")

        val server = ob.str("server") ?: return rejected(tag, ErrorCode.INVALID_SERVER, "$type outbound has no 'server'", "server")
        val port = ob.int("server_port") ?: return rejected(tag, ErrorCode.INVALID_PORT, "$type outbound has no valid 'server_port'", "server_port")

        val proxy = linkedMapOf<String, Any>()
        proxy["server"] = server
        proxy["port"] = port
        proxy["udp"] = true

        when (type) {
            "vless", "vmess" -> {
                val uuid = ob.str("uuid") ?: return rejected(tag, ErrorCode.INVALID_CREDENTIALS, "$type outbound has no 'uuid'", "uuid")
                proxy["type"] = type
                proxy["uuid"] = uuid
                if (type == "vless") {
                    ob.str("flow")?.let { proxy["flow"] = it }
                } else {
                    proxy["alterId"] = ob.int("alter_id") ?: 0
                    proxy["cipher"] = ob.str("security") ?: "auto"
                }
            }
            "trojan" -> {
                proxy["type"] = "trojan"
                proxy["password"] = ob.str("password") ?: return rejected(tag, ErrorCode.INVALID_CREDENTIALS, "trojan outbound has no 'password'", "password")
            }
            "shadowsocks" -> {
                proxy["type"] = "ss"
                proxy["cipher"] = ob.str("method") ?: return rejected(tag, ErrorCode.INVALID_CREDENTIALS, "shadowsocks outbound has no 'method'", "method")
                proxy["password"] = ob.str("password") ?: return rejected(tag, ErrorCode.INVALID_CREDENTIALS, "shadowsocks outbound has no 'password'", "password")
            }
            else -> return rejected(tag, ErrorCode.UNSUPPORTED_PROTOCOL, "outbound type '$type' is not supported", "type")
        }

        val params = linkedMapOf<String, String>()
        val tls = ob.obj("tls")
        val reality = tls?.obj("reality")
        val security = when {
            tls?.bool("enabled") == true && reality?.bool("enabled") == true -> "reality"
            tls?.bool("enabled") == true -> "tls"
            else -> "none"
        }
        params["security"] = security
        if (tls != null && security != "none") {
            tls.str("server_name")?.let { params["sni"] = it }
            val alpn = tls.strList("alpn")
            if (alpn.isNotEmpty()) params["alpn"] = alpn.joinToString(",")
            if (tls.bool("insecure") == true) params["allowInsecure"] = "1"
            tls.obj("utls")?.takeIf { it.bool("enabled") != false }?.str("fingerprint")?.let { params["fp"] = it }
            if (security == "reality" && reality != null) {
                reality.str("public_key")?.let { params["pbk"] = it }
                reality.str("short_id")?.let { params["sid"] = it }
            }
        }

        val transport = ob.obj("transport")
        val transportType = (transport?.str("type") ?: "tcp").lowercase()
        params["type"] = when (transportType) {
            "http" -> "h2"
            else -> transportType
        }
        if (transport != null) {
            when (transportType) {
                "ws" -> {
                    transport.str("path")?.let { params["path"] = it }
                    (transport.obj("headers")?.str("Host") ?: transport.obj("headers")?.str("host"))?.let { params["host"] = it }
                }
                "grpc" -> transport.str("service_name")?.let { params["serviceName"] = it }
                "http" -> {
                    transport.str("path")?.let { params["path"] = it }
                    transport.strList("host").firstOrNull()?.let { params["host"] = it }
                }
            }
        }

        if (type == "trojan") params["sni"]?.let { proxy["sni"] = it }
        if (type == "trojan" && params["allowInsecure"] == "1") proxy["skip-cert-verify"] = true

        ConfigUriParser.applyTransport(proxy, params)
        return Adapted.Proxy(tag, proxy)
    }
}
