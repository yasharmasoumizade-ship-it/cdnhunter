package com.cdnhunter.app.vpn

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder

/**
 * Parses proxy URI (trojan:// vless:// vmess://) into xray outbound JSON.
 */
object ConfigUriParser {

    fun parseToOutbound(uri: String): JSONObject? {
        val trimmed = uri.trim()
        return when {
            trimmed.startsWith("trojan://") -> parseTrojan(trimmed)
            trimmed.startsWith("vless://") -> parseVless(trimmed)
            trimmed.startsWith("vmess://") -> parseVmess(trimmed)
            else -> null
        }
    }

    private fun parseTrojan(uri: String): JSONObject {
        val body = uri.removePrefix("trojan://")
        val password = body.substringBefore("@")
        val rest = body.substringAfter("@").substringBefore("#")
        val hostPort = rest.substringBefore("?")
        val query = if (rest.contains("?")) rest.substringAfter("?") else ""
        val address = hostPort.substringBeforeLast(":")
        val port = hostPort.substringAfterLast(":").toIntOrNull() ?: 443
        val params = parseQuery(query)

        val ob = JSONObject()
        ob.put("protocol", "trojan")
        val settings = JSONObject()
        val server = JSONObject()
        server.put("address", address)
        server.put("port", port)
        server.put("password", password)
        settings.put("servers", JSONArray().put(server))
        ob.put("settings", settings)
        ob.put("streamSettings", buildStreamSettings(params))
        return ob
    }

    private fun parseVless(uri: String): JSONObject {
        val body = uri.removePrefix("vless://")
        val uuid = body.substringBefore("@")
        val rest = body.substringAfter("@").substringBefore("#")
        val hostPort = rest.substringBefore("?")
        val query = if (rest.contains("?")) rest.substringAfter("?") else ""
        val address = hostPort.substringBeforeLast(":")
        val port = hostPort.substringAfterLast(":").toIntOrNull() ?: 443
        val params = parseQuery(query)

        val ob = JSONObject()
        ob.put("protocol", "vless")
        val settings = JSONObject()
        val vnext = JSONObject()
        vnext.put("address", address)
        vnext.put("port", port)
        val user = JSONObject()
        user.put("id", uuid)
        user.put("encryption", params["encryption"] ?: "none")
        user.put("flow", params["flow"] ?: "")
        vnext.put("users", JSONArray().put(user))
        settings.put("vnext", JSONArray().put(vnext))
        ob.put("settings", settings)
        ob.put("streamSettings", buildStreamSettings(params))
        return ob
    }

    private fun parseVmess(uri: String): JSONObject {
        val b64 = uri.removePrefix("vmess://")
        val json = String(Base64.decode(b64, Base64.DEFAULT))
        val obj = JSONObject(json)

        val ob = JSONObject()
        ob.put("protocol", "vmess")
        val settings = JSONObject()
        val vnext = JSONObject()
        vnext.put("address", obj.optString("add"))
        vnext.put("port", obj.optString("port").toIntOrNull() ?: 443)
        val user = JSONObject()
        user.put("id", obj.optString("id"))
        user.put("alterId", obj.optString("aid", "0").toIntOrNull() ?: 0)
        user.put("security", obj.optString("scy", "auto"))
        vnext.put("users", JSONArray().put(user))
        settings.put("vnext", JSONArray().put(vnext))
        ob.put("settings", settings)

        val params = mapOf(
            "type" to obj.optString("net", "tcp"),
            "security" to obj.optString("tls", ""),
            "sni" to obj.optString("sni", ""),
            "host" to obj.optString("host", ""),
            "path" to obj.optString("path", ""),
            "alpn" to obj.optString("alpn", ""),
            "fp" to obj.optString("fp", ""),
            "mode" to obj.optString("mode", ""),
        )
        ob.put("streamSettings", buildStreamSettings(params))
        return ob
    }

    private fun buildStreamSettings(params: Map<String, String>): JSONObject {
        val ss = JSONObject()
        val network = params["type"] ?: "tcp"
        ss.put("network", network)

        // Security: TLS or REALITY
        val security = params["security"] ?: ""
        when (security) {
            "tls" -> {
                ss.put("security", "tls")
                val tls = JSONObject()
                val sni = params["sni"] ?: params["host"] ?: ""
                if (sni.isNotBlank()) tls.put("serverName", sni)
                val alpn = params["alpn"] ?: ""
                if (alpn.isNotBlank()) tls.put("alpn", JSONArray().apply { alpn.split(",").forEach { put(it) } })
                val fp = params["fp"] ?: ""
                if (fp.isNotBlank()) tls.put("fingerprint", fp)
                ss.put("tlsSettings", tls)
            }
            "reality" -> {
                ss.put("security", "reality")
                val reality = JSONObject()
                val sni = params["sni"] ?: ""
                if (sni.isNotBlank()) reality.put("serverName", sni)
                // fingerprint is REQUIRED for REALITY; default to chrome
                reality.put("fingerprint", params["fp"]?.takeIf { it.isNotBlank() } ?: "chrome")
                val pbk = params["pbk"] ?: ""
                if (pbk.isNotBlank()) reality.put("publicKey", pbk)
                val sid = params["sid"] ?: ""
                if (sid.isNotBlank()) reality.put("shortId", sid)
                val spx = params["spx"] ?: ""
                if (spx.isNotBlank()) reality.put("spiderX", spx)
                ss.put("realitySettings", reality)
            }
        }

        // Transport
        when (network) {
            "ws" -> {
                val ws = JSONObject()
                ws.put("path", params["path"] ?: "/")
                val headers = JSONObject()
                val host = params["host"] ?: ""
                if (host.isNotBlank()) headers.put("Host", host)
                ws.put("headers", headers)
                ss.put("wsSettings", ws)
            }
            "xhttp", "splithttp" -> {
                val xhttp = JSONObject()
                xhttp.put("path", params["path"] ?: "/")
                val host = params["host"] ?: ""
                if (host.isNotBlank()) xhttp.put("host", host)
                val mode = params["mode"] ?: ""
                if (mode.isNotBlank()) xhttp.put("mode", mode)
                // "extra" carries advanced xhttp settings (scMaxEachPostBytes, xPaddingBytes,
                // xmux, downloadSettings, ...). Xray merges it over host/path/mode at build time.
                val extra = params["extra"] ?: ""
                if (extra.isNotBlank()) {
                    try { xhttp.put("extra", JSONObject(extra)) } catch (_: Exception) {}
                }
                ss.put("xhttpSettings", xhttp)
            }
            "grpc" -> {
                val grpc = JSONObject()
                grpc.put("serviceName", params["serviceName"] ?: params["path"] ?: "")
                ss.put("grpcSettings", grpc)
            }
            "h2", "http" -> {
                val h2 = JSONObject()
                h2.put("path", params["path"] ?: "/")
                val host = params["host"] ?: ""
                if (host.isNotBlank()) h2.put("host", JSONArray().put(host))
                ss.put("httpSettings", h2)
            }
        }

        return ss
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split("&").associate { p ->
            val k = p.substringBefore("=")
            val v = try { URLDecoder.decode(p.substringAfter("="), "UTF-8") } catch (_: Exception) { p.substringAfter("=") }
            k to v
        }
    }
}
