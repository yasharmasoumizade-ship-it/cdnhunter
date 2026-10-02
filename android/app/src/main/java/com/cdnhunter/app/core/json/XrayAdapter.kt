package com.cdnhunter.app.core.json

import com.cdnhunter.app.core.ErrorCode
import com.cdnhunter.app.vpn.ConfigUriParser
import org.json.JSONObject

/**
 * Xray / V2Ray outbound objects:
 *
 *     { "tag": "...", "protocol": "vless",
 *       "settings": { "vnext": [ { "address", "port", "users": [ { "id", "flow", "encryption" } ] } ] },
 *       "streamSettings": { "network", "security", "tlsSettings" | "realitySettings", "wsSettings" ... } }
 *
 * Both the classic nested form (`vnext` / `servers`) and Xray's newer flat form
 * (`settings: { address, port, id }`) are read. Transport and security options are not
 * translated here: they are handed to [ConfigUriParser.applyTransport] in the same
 * parameter vocabulary a share link uses, so a server reaches the core identically whether
 * it came in as a link or as JSON.
 */
internal object XrayAdapter {

    fun adapt(ob: JSONObject): Adapted {
        val protocol = (ob.str("protocol") ?: return rejected("?", ErrorCode.INVALID_CONFIG, "outbound has no 'protocol'", "protocol"))
            .lowercase()
        val tag = ob.str("tag") ?: protocol
        if (protocol in NON_PROXY_TYPES) return Adapted.NotAProxy(tag, "'$protocol' is not a proxy outbound")

        val settings = ob.obj("settings")
        val stream = ob.obj("streamSettings")

        val proxy = linkedMapOf<String, Any>()
        val params = linkedMapOf<String, String>()

        when (protocol) {
            "vless", "vmess" -> {
                val vnext = settings?.arr("vnext")?.objects()?.firstOrNull()
                val src = vnext ?: settings
                    ?: return rejected(tag, ErrorCode.INVALID_CONFIG, "$protocol outbound has no 'settings'", "settings")
                val user = src.arr("users")?.objects()?.firstOrNull() ?: src
                val address = src.str("address") ?: return rejected(tag, ErrorCode.INVALID_SERVER, "$protocol outbound has no server address", "address")
                val port = src.int("port") ?: return rejected(tag, ErrorCode.INVALID_PORT, "$protocol outbound has no valid port", "port")
                val id = user.str("id") ?: return rejected(tag, ErrorCode.INVALID_CREDENTIALS, "$protocol outbound has no user id", "id")
                proxy["type"] = protocol
                proxy["server"] = address
                proxy["port"] = port
                proxy["uuid"] = id
                proxy["udp"] = true
                if (protocol == "vless") {
                    // Passed through as-is; ConfigValidator decides whether the format is one the core can use.
                    user.str("encryption")?.takeIf { !it.equals("none", ignoreCase = true) }?.let { proxy["encryption"] = it }
                    user.str("flow")?.let { proxy["flow"] = it }
                } else {
                    proxy["alterId"] = user.int("alterId") ?: 0
                    proxy["cipher"] = user.str("security") ?: "auto"
                }
            }
            "trojan", "shadowsocks" -> {
                val server = settings?.arr("servers")?.objects()?.firstOrNull() ?: settings
                    ?: return rejected(tag, ErrorCode.INVALID_CONFIG, "$protocol outbound has no 'settings'", "settings")
                val address = server.str("address") ?: return rejected(tag, ErrorCode.INVALID_SERVER, "$protocol outbound has no server address", "address")
                val port = server.int("port") ?: return rejected(tag, ErrorCode.INVALID_PORT, "$protocol outbound has no valid port", "port")
                val password = server.str("password") ?: return rejected(tag, ErrorCode.INVALID_CREDENTIALS, "$protocol outbound has no password", "password")
                proxy["server"] = address
                proxy["port"] = port
                proxy["password"] = password
                proxy["udp"] = true
                if (protocol == "trojan") {
                    proxy["type"] = "trojan"
                } else {
                    proxy["type"] = "ss"
                    proxy["cipher"] = server.str("method") ?: return rejected(tag, ErrorCode.INVALID_CREDENTIALS, "shadowsocks outbound has no method", "method")
                }
            }
            else -> return rejected(tag, ErrorCode.UNSUPPORTED_PROTOCOL, "protocol '$protocol' is not supported", "protocol")
        }

        // Stream settings -> the share-link parameter vocabulary.
        var network = (stream?.str("network") ?: "tcp").lowercase()
        network = when (network) {
            "websocket" -> "ws"
            "raw" -> "tcp"
            else -> network
        }
        val security = (stream?.str("security") ?: "none").lowercase()
        params["type"] = network
        params["security"] = security

        val tls = stream?.obj("tlsSettings")
        val reality = stream?.obj("realitySettings")
        if (security == "tls" && tls != null) {
            tls.str("serverName")?.let { params["sni"] = it }
            val alpn = tls.strList("alpn")
            if (alpn.isNotEmpty()) params["alpn"] = alpn.joinToString(",")
            if (tls.bool("allowInsecure") == true) params["allowInsecure"] = "1"
            tls.str("fingerprint")?.let { params["fp"] = it }
        }
        if (security == "reality" && reality != null) {
            reality.str("serverName")?.let { params["sni"] = it }
            reality.str("publicKey")?.let { params["pbk"] = it }
            reality.str("shortId")?.let { params["sid"] = it }
            reality.str("fingerprint")?.let { params["fp"] = it }
        }

        // Classic TCP + HTTP header obfuscation: tcpSettings.header { type: "http", request: { path, headers.Host } }.
        if (network == "tcp") {
            val header = stream?.obj("tcpSettings")?.obj("header")
            if (header?.str("type").equals("http", ignoreCase = true)) {
                params["headerType"] = "http"
                val req = header?.obj("request")
                req?.strList("path")?.firstOrNull()?.let { params["path"] = it }
                req?.obj("headers")?.strList("Host")?.firstOrNull()?.let { params["host"] = it }
            }
        }

        when (network) {
            "ws" -> stream?.obj("wsSettings")?.let { ws ->
                ws.str("path")?.let { params["path"] = it }
                (ws.obj("headers")?.str("Host") ?: ws.obj("headers")?.str("host") ?: ws.str("host"))?.let { params["host"] = it }
            }
            "grpc" -> stream?.obj("grpcSettings")?.str("serviceName")?.let { params["serviceName"] = it }
            "h2", "http" -> (stream?.obj("httpSettings") ?: stream?.obj("h2Settings"))?.let { h ->
                h.str("path")?.let { params["path"] = it }
                h.strList("host").firstOrNull()?.let { params["host"] = it }
            }
            "xhttp", "splithttp" -> (stream?.obj("xhttpSettings") ?: stream?.obj("splithttpSettings"))?.let { x ->
                x.str("path")?.let { params["path"] = it }
                x.str("host")?.let { params["host"] = it }
                x.str("mode")?.let { params["mode"] = it }
            }
        }

        // A trojan proxy carries its SNI on its own key, as the share-link parser does.
        if (protocol == "trojan") params["sni"]?.let { proxy["sni"] = it }
        if (protocol == "trojan" && (params["allowInsecure"] == "1")) proxy["skip-cert-verify"] = true

        ConfigUriParser.applyTransport(proxy, params)
        return Adapted.Proxy(tag, proxy)
    }
}
