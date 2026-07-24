package com.cdnhunter.app.vpn

import android.content.Context

/**
 * Builds a mihomo (Clash.Meta) YAML config from the stored user proxy URI.
 * Replaces the old Xray-JSON builder now that CdnVpnService runs on the
 * mihomo core (see MihomoBridge).
 */
object VpnConfigBuilder {

    const val ERROR_LOG_NAME = "mihomo_error.log"

    fun buildConfig(ctx: Context): String {
        val prefs = ctx.getSharedPreferences("cdnhunter_vpn", Context.MODE_PRIVATE)
        val userConfig = prefs.getString("user_config", "") ?: ""
        return buildConfigFromUri(userConfig)
    }

    /** Builds a full mihomo YAML config string from a raw proxy URI (vless/trojan/vmess/ss). */
    fun buildConfigFromUri(uri: String): String {
        val proxy = ConfigUriParser.parseToProxy(uri) ?: defaultProxy()
        proxy["name"] = "proxy"
        return renderYaml(proxy)
    }

    private fun defaultProxy(): LinkedHashMap<String, Any> =
        linkedMapOf("name" to "proxy", "type" to "direct")

    private fun renderYaml(proxy: LinkedHashMap<String, Any>): String {
        val root = linkedMapOf<String, Any>(
            "mixed-port" to 10808,
            "external-controller" to "127.0.0.1:10809",
            "allow-lan" to false,
            "mode" to "rule",
            "log-level" to "info",
            "ipv6" to false,
            "dns" to linkedMapOf(
                "enable" to true,
                "listen" to "0.0.0.0:1053",
                "default-nameserver" to listOf("1.1.1.1", "8.8.8.8"),
                "nameserver" to listOf("1.1.1.1", "8.8.8.8"),
            ),
            "proxies" to listOf(proxy),
            "proxy-groups" to listOf(
                linkedMapOf(
                    "name" to "PROXY",
                    "type" to "select",
                    "proxies" to listOf("proxy"),
                )
            ),
            "rules" to listOf(
                // Keep private/LAN ranges off the tunnel to avoid a traffic loop.
                "IP-CIDR,10.0.0.0/8,DIRECT",
                "IP-CIDR,172.16.0.0/12,DIRECT",
                "IP-CIDR,192.168.0.0/16,DIRECT",
                "IP-CIDR,169.254.0.0/16,DIRECT",
                "IP-CIDR,127.0.0.0/8,DIRECT",
                "MATCH,PROXY",
            ),
        )
        val sb = StringBuilder()
        writeYamlValue(sb, root, 0)
        return sb.toString()
    }

    // A small hand-rolled YAML writer — the config shape here is fully known
    // (maps/lists/strings/numbers/booleans), so this avoids pulling in a full
    // YAML dependency just to serialize a few dozen fixed fields.
    private fun writeYamlValue(sb: StringBuilder, value: Any?, indent: Int) {
        val pad = "  ".repeat(indent)
        when (value) {
            is Map<*, *> -> {
                if (value.isEmpty()) { sb.append("{}\n"); return }
                for ((k, v) in value) {
                    sb.append(pad).append(k).append(":")
                    writeInline(sb, v, indent)
                }
            }
            is List<*> -> {
                if (value.isEmpty()) { sb.append("[]\n"); return }
                for (item in value) {
                    sb.append(pad).append("-")
                    if (item is Map<*, *> || item is List<*>) {
                        sb.append(" ")
                        writeYamlInlineFirstLine(sb, item, indent)
                    } else {
                        sb.append(" ").append(scalar(item)).append("\n")
                    }
                }
            }
            else -> sb.append(pad).append(scalar(value)).append("\n")
        }
    }

    // Writes ": <value>" / newline+nested-block after a "key:" or "- " prefix already on the line.
    private fun writeInline(sb: StringBuilder, v: Any?, indent: Int) {
        when (v) {
            is Map<*, *> -> {
                if (v.isEmpty()) { sb.append(" {}\n"); return }
                sb.append("\n")
                writeYamlValue(sb, v, indent + 1)
            }
            is List<*> -> {
                if (v.isEmpty()) { sb.append(" []\n"); return }
                sb.append("\n")
                writeYamlValue(sb, v, indent)
            }
            else -> sb.append(" ").append(scalar(v)).append("\n")
        }
    }

    // For "- <map>" / "- <list>" list items: first key goes on the dash line, rest indented under it.
    private fun writeYamlInlineFirstLine(sb: StringBuilder, item: Any?, indent: Int) {
        when (item) {
            is Map<*, *> -> {
                var first = true
                for ((k, v) in item) {
                    if (!first) sb.append("  ".repeat(indent + 1))
                    sb.append(k).append(":")
                    writeInline(sb, v, indent + 1)
                    first = false
                }
            }
            is List<*> -> writeYamlValue(sb, item, indent + 1)
            else -> sb.append(scalar(item)).append("\n")
        }
    }

    private fun scalar(v: Any?): String = when (v) {
        null -> "null"
        is Boolean, is Int, is Long, is Double -> v.toString()
        is String -> yamlQuoteIfNeeded(v)
        else -> yamlQuoteIfNeeded(v.toString())
    }

    // Quote any string that contains YAML-significant characters or could be
    // misread as another type (e.g. a bare "yes"/"no", a number-looking id).
    private fun yamlQuoteIfNeeded(s: String): String {
        val needsQuote = s.isEmpty() ||
            s.any { it in ":#{}[],&*!|>'\"%@`" } ||
            s.startsWith(" ") || s.endsWith(" ") ||
            s == "true" || s == "false" || s == "null" ||
            s.toDoubleOrNull() != null
        if (!needsQuote) return s
        val escaped = s.replace("\\", "\\\\").replace("\"", "\\\"")
        return "\"$escaped\""
    }
}
