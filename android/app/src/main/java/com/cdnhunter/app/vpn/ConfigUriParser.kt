package com.cdnhunter.app.vpn

import android.util.Base64
import org.json.JSONObject
import java.net.URLDecoder

/**
 * Parses a proxy URI (vless:// trojan:// vmess:// ss://) into the flat
 * key-value shape mihomo/Clash expects under `proxies:` in its YAML config.
 * Unlike Xray, mihomo has no separate streamSettings/tls block — transport,
 * TLS and REALITY options all live directly on the proxy object.
 */
object ConfigUriParser {

    /** Returns a LinkedHashMap ready to be YAML-serialized as one `proxies:` entry, or null. */
    fun parseToProxy(uri: String): LinkedHashMap<String, Any>? {
        val trimmed = uri.trim()
        return when {
            trimmed.startsWith("trojan://") -> parseTrojan(trimmed)
            trimmed.startsWith("vless://") -> parseVless(trimmed)
            trimmed.startsWith("vmess://") -> parseVmess(trimmed)
            trimmed.startsWith("ss://") -> parseShadowsocks(trimmed)
            else -> null
        }
    }

    private fun parseTrojan(uri: String): LinkedHashMap<String, Any> {
        val body = uri.removePrefix("trojan://")
        val password = body.substringBefore("@")
        val rest = body.substringAfter("@").substringBefore("#")
        val hostPort = rest.substringBefore("?")
        val query = if (rest.contains("?")) rest.substringAfter("?") else ""
        val address = hostPort.substringBeforeLast(":")
        val port = hostPort.substringAfterLast(":").toIntOrNull() ?: 443
        val params = parseQuery(query)

        val p = linkedMapOf<String, Any>(
            "name" to "trojan-out",
            "type" to "trojan",
            "server" to address,
            "port" to port,
            "password" to password,
            "udp" to true,
        )
        val sni = params["sni"] ?: params["peer"] ?: ""
        if (sni.isNotBlank()) p["sni"] = sni
        if ((params["allowInsecure"] ?: params["insecure"]) == "1") p["skip-cert-verify"] = true
        applyTransport(p, params)
        return p
    }

    private fun parseVless(uri: String): LinkedHashMap<String, Any> {
        val body = uri.removePrefix("vless://")
        val uuid = body.substringBefore("@")
        val rest = body.substringAfter("@").substringBefore("#")
        val hostPort = rest.substringBefore("?")
        val query = if (rest.contains("?")) rest.substringAfter("?") else ""
        val address = hostPort.substringBeforeLast(":")
        val port = hostPort.substringAfterLast(":").toIntOrNull() ?: 443
        val params = parseQuery(query)

        val p = linkedMapOf<String, Any>(
            "name" to "vless-out",
            "type" to "vless",
            "server" to address,
            "port" to port,
            "uuid" to uuid,
            "udp" to true,
        )
        val flow = params["flow"] ?: ""
        if (flow.isNotBlank()) p["flow"] = flow
        applyTransport(p, params)
        return p
    }

    private fun parseVmess(uri: String): LinkedHashMap<String, Any> {
        val b64 = uri.removePrefix("vmess://")
        val json = String(Base64.decode(padBase64(b64), Base64.DEFAULT))
        val obj = JSONObject(json)

        val p = linkedMapOf<String, Any>(
            "name" to "vmess-out",
            "type" to "vmess",
            "server" to obj.optString("add"),
            "port" to (obj.optString("port").toIntOrNull() ?: 443),
            "uuid" to obj.optString("id"),
            "alterId" to (obj.optString("aid", "0").toIntOrNull() ?: 0),
            "cipher" to obj.optString("scy", "auto"),
            "udp" to true,
        )
        val params = mapOf(
            "type" to obj.optString("net", "tcp"),
            "security" to obj.optString("tls", ""),
            "sni" to obj.optString("sni", ""),
            "host" to obj.optString("host", ""),
            "path" to obj.optString("path", ""),
            "alpn" to obj.optString("alpn", ""),
            "fp" to obj.optString("fp", ""),
        )
        applyTransport(p, params)
        return p
    }

    private fun parseShadowsocks(uri: String): LinkedHashMap<String, Any>? {
        val body = uri.removePrefix("ss://")
        val withoutTag = body.substringBefore("#")
        val hasAt = withoutTag.contains("@")

        val method: String
        val password: String
        val hostPortRaw: String

        if (hasAt) {
            val userInfo = withoutTag.substringBefore("@")
            hostPortRaw = withoutTag.substringAfter("@")
            val decodedUserInfo = try {
                String(Base64.decode(padBase64(userInfo), Base64.URL_SAFE or Base64.NO_WRAP))
            } catch (e: Exception) {
                try { String(Base64.decode(padBase64(userInfo), Base64.DEFAULT)) }
                catch (e2: Exception) { userInfo }
            }
            val credSource = if (decodedUserInfo.contains(":")) decodedUserInfo else userInfo
            method = credSource.substringBefore(":")
            password = credSource.substringAfter(":")
        } else {
            val decoded = try {
                String(Base64.decode(padBase64(withoutTag), Base64.DEFAULT))
            } catch (e: Exception) { return null }
            if (!decoded.contains("@")) return null
            val credPart = decoded.substringBefore("@")
            hostPortRaw = decoded.substringAfter("@")
            method = credPart.substringBefore(":")
            password = credPart.substringAfter(":")
        }

        val hostPortClean = hostPortRaw.substringBefore("?").substringBefore("#")
        val address = hostPortClean.substringBeforeLast(":")
        val port = hostPortClean.substringAfterLast(":").toIntOrNull() ?: 8388
        if (address.isBlank() || method.isBlank() || password.isBlank()) return null

        return linkedMapOf(
            "name" to "ss-out",
            "type" to "ss",
            "server" to address,
            "port" to port,
            "cipher" to method,
            "password" to password,
            "udp" to true,
        )
    }

    private fun padBase64(s: String): String {
        var str = s.replace('-', '+').replace('_', '/')
        val mod = str.length % 4
        if (mod > 0) str += "=".repeat(4 - mod)
        return str
    }

    /** Applies TLS/REALITY + transport (ws/grpc/h2/tcp) settings directly onto the flat proxy map. */
    private fun applyTransport(p: LinkedHashMap<String, Any>, params: Map<String, String>) {
        val network = params["type"] ?: "tcp"
        val security = params["security"] ?: ""

        when (security) {
            "tls" -> {
                p["tls"] = true
                val sni = params["sni"] ?: params["host"] ?: ""
                if (sni.isNotBlank()) p["servername"] = sni
                val alpn = params["alpn"] ?: ""
                if (alpn.isNotBlank()) p["alpn"] = alpn.split(",").filter { it.isNotBlank() }
                val fp = params["fp"] ?: ""
                if (fp.isNotBlank()) p["client-fingerprint"] = fp
                if ((params["allowInsecure"] ?: params["insecure"]) == "1") p["skip-cert-verify"] = true
            }
            "reality" -> {
                p["tls"] = true
                val sni = params["sni"] ?: ""
                if (sni.isNotBlank()) p["servername"] = sni
                p["client-fingerprint"] = params["fp"]?.takeIf { it.isNotBlank() } ?: "chrome"
                val realityOpts = linkedMapOf<String, Any>()
                (params["pbk"] ?: "").takeIf { it.isNotBlank() }?.let { realityOpts["public-key"] = it }
                (params["sid"] ?: "").let { realityOpts["short-id"] = it } // mihomo accepts empty short-id
                if (realityOpts.isNotEmpty()) p["reality-opts"] = realityOpts
            }
        }

        when (network) {
            "ws" -> {
                p["network"] = "ws"
                val wsOpts = linkedMapOf<String, Any>("path" to (params["path"]?.takeIf { it.isNotBlank() } ?: "/"))
                val host = params["host"] ?: ""
                if (host.isNotBlank()) wsOpts["headers"] = linkedMapOf("Host" to host)
                p["ws-opts"] = wsOpts
            }
            "grpc" -> {
                p["network"] = "grpc"
                val serviceName = params["serviceName"] ?: params["path"] ?: ""
                p["grpc-opts"] = linkedMapOf("grpc-service-name" to serviceName)
            }
            "h2", "http" -> {
                p["network"] = "h2"
                val h2Opts = linkedMapOf<String, Any>("path" to listOf(params["path"]?.takeIf { it.isNotBlank() } ?: "/"))
                val host = params["host"] ?: ""
                if (host.isNotBlank()) h2Opts["host"] = listOf(host)
                p["h2-opts"] = h2Opts
            }
            "xhttp", "splithttp" -> {
                p["network"] = "xhttp"
                val xhttpOpts = linkedMapOf<String, Any>(
                    "path" to (params["path"]?.takeIf { it.isNotBlank() } ?: "/"),
                    "mode" to (params["mode"]?.takeIf { it.isNotBlank() } ?: "auto"),
                )
                val host = params["host"] ?: ""
                if (host.isNotBlank()) xhttpOpts["host"] = host
                p["xhttp-opts"] = xhttpOpts
            }
            // "tcp" (the default) needs no network/*-opts entry in mihomo.
        }
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split("&").associate { part ->
            val k = part.substringBefore("=")
            val v = try { URLDecoder.decode(part.substringAfter("="), "UTF-8") } catch (_: Exception) { part.substringAfter("=") }
            k to v
        }
    }
}
