package com.cdnhunter.app.core.json

import com.cdnhunter.app.core.ErrorCode
import com.cdnhunter.app.core.ProxyProtocol
import org.json.JSONObject

/**
 * Clash / Clash.Meta `proxies:` entries written as JSON. They are already in the core's
 * flat shape, but they are not passed through: a config from outside is untrusted, so
 * every field is copied by name, with its type coerced, and anything not on the list is
 * dropped. That is what stops an unexpected key — or a crafted one — from reaching the
 * core's YAML config.
 */
internal object ClashAdapter {

    fun adapt(ob: JSONObject): Adapted {
        val type = (ob.str("type") ?: return rejected("?", ErrorCode.INVALID_CONFIG, "proxy has no 'type'", "type")).lowercase()
        val tag = ob.str("name") ?: type
        if (type in NON_PROXY_TYPES) return Adapted.NotAProxy(tag, "'$type' is not a proxy")
        if (ProxyProtocol.fromWire(type) == null) {
            return rejected(tag, ErrorCode.UNSUPPORTED_PROTOCOL, "proxy type '$type' is not supported", "type")
        }
        val server = ob.str("server") ?: return rejected(tag, ErrorCode.INVALID_SERVER, "proxy has no 'server'", "server")
        val port = ob.int("port") ?: return rejected(tag, ErrorCode.INVALID_PORT, "proxy has no valid 'port'", "port")

        val p = linkedMapOf<String, Any>()
        p["type"] = type
        p["server"] = server
        p["port"] = port
        p["udp"] = ob.bool("udp") ?: true

        for (k in listOf("uuid", "cipher", "password", "servername", "sni", "client-fingerprint", "flow", "network")) {
            ob.str(k)?.let { p[k] = if (k == "network") it.lowercase() else it }
        }
        ob.int("alterId")?.let { p["alterId"] = it }
        for (k in listOf("tls", "skip-cert-verify")) ob.bool(k)?.let { p[k] = it }
        ob.strList("alpn").takeIf { it.isNotEmpty() }?.let { p["alpn"] = it }

        ob.obj("ws-opts")?.let { ws ->
            val o = linkedMapOf<String, Any>()
            ws.str("path")?.let { o["path"] = it }
            ws.obj("headers")?.str("Host")?.let { o["headers"] = linkedMapOf<String, Any>("Host" to it) }
            if (o.isNotEmpty()) p["ws-opts"] = o
        }
        ob.obj("grpc-opts")?.str("grpc-service-name")?.let {
            p["grpc-opts"] = linkedMapOf<String, Any>("grpc-service-name" to it)
        }
        ob.obj("h2-opts")?.let { h2 ->
            val o = linkedMapOf<String, Any>()
            h2.strList("path").takeIf { it.isNotEmpty() }?.let { o["path"] = it }
            h2.strList("host").takeIf { it.isNotEmpty() }?.let { o["host"] = it }
            if (o.isNotEmpty()) p["h2-opts"] = o
        }
        ob.obj("xhttp-opts")?.let { x ->
            val o = linkedMapOf<String, Any>()
            x.str("path")?.let { o["path"] = it }
            x.str("mode")?.let { o["mode"] = it }
            x.str("host")?.let { o["host"] = it }
            if (o.isNotEmpty()) p["xhttp-opts"] = o
        }
        ob.obj("reality-opts")?.let { r ->
            val o = linkedMapOf<String, Any>()
            r.str("public-key")?.let { o["public-key"] = it }
            o["short-id"] = r.str("short-id") ?: ""
            r.bool("support-x25519mlkem768")?.let { o["support-x25519mlkem768"] = it }
            p["reality-opts"] = o
        }
        ob.obj("ech-opts")?.let { e ->
            val o = linkedMapOf<String, Any>()
            e.bool("enable")?.let { o["enable"] = it }
            e.str("config")?.let { o["config"] = it }
            if (o.isNotEmpty()) p["ech-opts"] = o
        }
        return Adapted.Proxy(tag, p)
    }
}
