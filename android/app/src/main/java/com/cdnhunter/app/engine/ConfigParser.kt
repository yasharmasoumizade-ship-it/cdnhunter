package com.cdnhunter.app.engine

import android.util.Base64
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Parses and rebuilds proxy config URIs (trojan / vless / vmess).
 * Core feature: swap the server IP while keeping SNI/host/path intact (CDN fronting).
 */
object ConfigParser {

    data class ParsedConfig(
        val protocol: String,      // trojan, vless, vmess
        val id: String,            // password or uuid
        val address: String,       // server IP/host (this is what we swap)
        val port: Int,
        val params: MutableMap<String, String>,
        val name: String,
        val raw: String,
    )

    fun parse(uri: String): ParsedConfig? {
        val trimmed = uri.trim()
        return when {
            trimmed.startsWith("trojan://") -> parseStandard(trimmed, "trojan")
            trimmed.startsWith("vless://") -> parseStandard(trimmed, "vless")
            trimmed.startsWith("vmess://") -> parseVmess(trimmed)
            else -> null
        }
    }

    private fun parseStandard(uri: String, proto: String): ParsedConfig? {
        try {
            val body = uri.removePrefix("$proto://")
            val name = if (body.contains("#")) URLDecoder.decode(body.substringAfterLast("#"), "UTF-8") else proto
            val main = body.substringBeforeLast("#").substringBefore("?")
            val query = if (body.contains("?")) body.substringAfter("?").substringBeforeLast("#") else ""
            val id = main.substringBefore("@")
            val hostPort = main.substringAfter("@")
            val address = hostPort.substringBeforeLast(":")
            val port = hostPort.substringAfterLast(":").toIntOrNull() ?: 443
            val params = mutableMapOf<String, String>()
            query.split("&").forEach { p ->
                if (p.contains("=")) {
                    val k = p.substringBefore("=")
                    val v = URLDecoder.decode(p.substringAfter("="), "UTF-8")
                    params[k] = v
                }
            }
            return ParsedConfig(proto, id, address, port, params, name, uri)
        } catch (e: Exception) { return null }
    }

    private fun parseVmess(uri: String): ParsedConfig? {
        try {
            val b64 = uri.removePrefix("vmess://")
            val json = String(Base64.decode(b64, Base64.DEFAULT))
            val obj = JSONObject(json)
            val params = mutableMapOf<String, String>()
            obj.keys().forEach { k -> params[k] = obj.optString(k) }
            return ParsedConfig("vmess", obj.optString("id"), obj.optString("add"),
                obj.optString("port").toIntOrNull() ?: 443, params, obj.optString("ps", "vmess"), uri)
        } catch (e: Exception) { return null }
    }

    /** Rebuild URI with a new server address (the clean CDN IP). */
    fun rebuildWithIp(cfg: ParsedConfig, newIp: String): String {
        return when (cfg.protocol) {
            "vmess" -> {
                val obj = JSONObject()
                cfg.params.forEach { (k, v) -> obj.put(k, v) }
                obj.put("add", newIp)
                "vmess://" + Base64.encodeToString(obj.toString().toByteArray(), Base64.NO_WRAP)
            }
            else -> {
                val q = cfg.params.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
                "${cfg.protocol}://${cfg.id}@$newIp:${cfg.port}?$q#${URLEncoder.encode(cfg.name, "UTF-8")}"
            }
        }
    }

    /** Human-readable summary of the config */
    fun summary(cfg: ParsedConfig): String {
        val sni = cfg.params["sni"] ?: cfg.params["host"] ?: "-"
        val net = cfg.params["type"] ?: cfg.params["net"] ?: "tcp"
        return "${cfg.protocol.uppercase()} • $net • SNI: $sni"
    }
}
