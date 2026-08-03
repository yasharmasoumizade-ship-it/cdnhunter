package com.cdnhunter.app.vpn

import android.content.Context

/**
 * Builds a mihomo (Clash.Meta) YAML config from the stored user proxy URI.
 * Replaces the old Xray-JSON builder now that CdnVpnService runs on the
 * mihomo core (see MihomoBridge).
 */
object VpnConfigBuilder {

    const val ERROR_LOG_NAME = "mihomo_error.log"

    fun buildConfig(ctx: Context, tunFd: Int, forceX25519Mlkem768: Boolean = false): String {
        val prefs = ctx.getSharedPreferences("cdnhunter_vpn", Context.MODE_PRIVATE)
        val userConfig = prefs.getString("user_config", "") ?: ""
        val mtu = AppSettings.mtu(ctx)
        val allowLan = AppSettings.allowLan(ctx)
        val ipv6 = AppSettings.ipv6Enabled(ctx)
        val useDoh = AppSettings.useDoh(ctx)
        val adBlocker = AppSettings.adBlockerEnabled(ctx)
        val blockAds = AppSettings.blockAds(ctx)
        val blockTrackers = AppSettings.blockTrackers(ctx)
        val blockMalware = AppSettings.blockMalware(ctx)
        val customDnsEnabled = AppSettings.customDnsEnabled(ctx)
        val customDnsServers = AppSettings.customDnsServers(ctx)
        return buildConfigFromUri(
            userConfig, tunFd, forceX25519Mlkem768, mtu, allowLan, ipv6, useDoh,
            adBlocker, blockAds, blockTrackers, blockMalware, customDnsEnabled, customDnsServers
        )
    }

    /** Builds a full mihomo YAML config string from a raw proxy URI (vless/trojan/vmess/ss). */
    fun buildConfigFromUri(
        uri: String, tunFd: Int, forceX25519Mlkem768: Boolean = false,
        mtu: Int = 1500, allowLan: Boolean = false, ipv6: Boolean = false, useDoh: Boolean = true,
        adBlocker: Boolean = false, blockAds: Boolean = true,
        blockTrackers: Boolean = true, blockMalware: Boolean = true,
        customDnsEnabled: Boolean = false, customDnsServers: List<String> = emptyList()
    ): String {
        val proxy = ConfigUriParser.parseToProxy(uri, forceX25519Mlkem768) ?: defaultProxy()
        proxy["name"] = "proxy"
        return renderYaml(
            proxy, tunFd, mtu, allowLan, ipv6, useDoh,
            adBlocker, blockAds, blockTrackers, blockMalware,
            customDnsEnabled, customDnsServers
        )
    }


    private fun defaultProxy(): LinkedHashMap<String, Any> =
        linkedMapOf("name" to "proxy", "type" to "direct")

    private fun renderYaml(
        proxy: LinkedHashMap<String, Any>, tunFd: Int, mtu: Int = 1500,
        allowLan: Boolean = false, ipv6: Boolean = false, useDoh: Boolean = true,
        adBlocker: Boolean = false, blockAds: Boolean = true,
        blockTrackers: Boolean = true, blockMalware: Boolean = true,
        customDnsEnabled: Boolean = false, customDnsServers: List<String> = emptyList()
    ): String {
        // DNS nameservers: either user-provided custom servers, or default Cloudflare+Google
        // When using custom DNS, respect the DoH setting: if useDoh && custom server is IP,
        // user should provide https://... URLs; if DoH is off, provide plain IP:port or just IP.
        // Validate custom DNS to prevent leaks: remove empty strings, warn invalid entries.
        val validCustomDns = customDnsServers
            .filter { it.isNotBlank() }
            .map { it.trim() }
            .take(2)  // Limit to primary + secondary
        
        val nameservers = if (customDnsEnabled && validCustomDns.isNotEmpty()) {
            // User-provided custom DNS — ensure they're properly formatted
            validCustomDns.map { server ->
                when {
                    // DoH URL (https://...) — Clash will use as-is
                    server.startsWith("https://") -> server
                    // DoQ URL (quic://...) — mihomo supports DoQ
                    server.startsWith("quic://") -> server
                    // Plain IP or IP:port — assume port 53 if not specified
                    server.contains(":") -> server
                    // Just IP — assume port 53
                    else -> "$server:53"
                }
            }
        } else if (useDoh) {
            // DoH by default — TLS-wrapped, safe from ISP poisoning
            listOf("https://1.1.1.1/dns-query", "https://8.8.8.8/dns-query")
        } else {
            // Plain DNS fallback — only if user explicitly disables DoH
            listOf("1.1.1.1:53", "8.8.8.8:53")
        }
        
        val root = linkedMapOf<String, Any>(
            "mixed-port" to 10808,
            "external-controller" to "127.0.0.1:10809",
            "allow-lan" to allowLan,
            "mode" to "rule",
            "log-level" to "error",
            "ipv6" to ipv6,
            "dns" to linkedMapOf(
                "enable" to true,
                "listen" to "0.0.0.0:1053",
                // fake-ip is required here, not just an option: with the default
                // "mapping" mode, mihomo only learns a domain<->IP pairing when a
                // client actually queries mihomo's own DNS server first. Nothing on
                // Android forces that — apps use the system/private DNS resolver by
                // default — so real IPs reached the tun with no domain attached, the
                // rule engine had nothing to match against, and traffic silently went
                // nowhere even though the tun and mihomo both reported "connected".
                // fake-ip returns synthetic addresses from fake-ip-range for every
                // hijacked query, forcing all DNS through mihomo and giving every
                // outbound connection a domain to match against `rules:` below.
                "enhanced-mode" to "fake-ip",
                "fake-ip-range" to "198.18.0.1/16",
                "fake-ip-filter" to listOf("*.lan", "localhost.ptlogin2.qq.com"),
                // default-nameserver stays plain IP (bootstrap only, used if a
                // nameserver entry below were ever a hostname instead of an IP).
                "default-nameserver" to listOf("1.1.1.1", "8.8.8.8"),
                // ===== DNS CONFIGURATION =====
                // IMPORTANT: System DoH (Android Settings > Private DNS) can BYPASS the VPN!
                // 
                // WHY:
                // - System DoH uses HTTPS (port 443) with encrypted queries
                // - TUN-level dns-hijack only catches UDP:53 and TCP:53 (plain DNS)
                // - HTTPS traffic on 443 is NOT intercepted → DoH queries escape tunnel!
                //
                // SOLUTION:
                // 1. Clash's nameserver config handles DoH queries through Clash proxy
                // 2. SNI sniffer detects HTTPS traffic to DNS servers
                // 3. Rules route DNS domains (dns.google, 1.1.1.1, etc) through proxy
                // 
                // USER MUST:
                // - Disable "Private DNS" in Android Settings when VPN is active
                // - OR Clash will try to tunnel DoH but may create DNS loops
                //
                // Clash-side DoH: these HTTPS URLs will be proxied through Clash
                "nameserver" to nameservers,
            ),
            // Wire mihomo directly to the TUN device Android already created via
            // VpnService.Builder.establish(). Without this block mihomo only opened
            // a local mixed-proxy port with nothing feeding it any TUN traffic —
            // the VPN looked "connected" but no packets ever reached the tunnel.
            // - file-descriptor: hands mihomo the already-open fd directly (the
            //   only way to do TUN on Android without root/CAP_NET_ADMIN).
            // - auto-route/auto-detect-interface: false, because routing is already
            //   configured on the Android side (see CdnVpnService.establishTun()).
            // - stack: gvisor — avoids kernel/user-space switches, better throughput
            //   on Android than "system" (which also needs privileges we don't have).
            "tun" to linkedMapOf(
                "enable" to true,
                "stack" to "gvisor",
                "file-descriptor" to tunFd,
                "auto-route" to false,
                // Reverted back to false. The real ClashMetaForAndroid client
                // (the reference implementation for embedding mihomo on Android)
                // sets this to false explicitly, with the comment "implements by
                // VpnService::protect" — see core/src/main/golang/native/tun/tun.go
                // in metacubex/ClashMetaForAndroid. Reason: mihomo's sing_tun.New()
                // only starts a NetworkUpdateMonitor when AutoRoute OR
                // AutoDetectInterface is true, and that monitor depends on
                // netlink — which Google blocks for non-system apps on Android
                // 14+. If it fails to start there, sing_tun.New() returns an
                // error... which executor.ApplyConfig() swallows (it only logs
                // Go-side errors, never surfaces them to Start()'s caller). That
                // means mihomo could report "started OK" while the TUN listener
                // was never actually created — indistinguishable from a healthy
                // connection except that literally nothing ever reaches it, which
                // matches "connects, zero dial attempts, whole device offline"
                // exactly. protect() (already wired via MihomoBridge.setProtector
                // -> dialer.DefaultSocketHook) is what actually needs to keep
                // mihomo's own sockets out of the tun — auto-detect-interface was
                // solving a problem protect() already solves, at the cost of this
                // Android-14+ failure mode.
                "auto-detect-interface" to false,
                // DNS hijacking: catch plain DNS queries (UDP:53 and TCP:53)
                // Note: DoH (DNS over HTTPS on port 443) cannot be intercepted at TUN level
                // since it uses encrypted HTTPS traffic. Instead, we rely on:
                // 1. SNI sniffer to detect DNS-over-HTTPS domains
                // 2. Clash rules to route HTTPS through proxy (which decrypts via cert pinning)
                // 3. Users enabling DoH within Clash config (not system-level)
                // For maximum compatibility: hijack plain DNS (53) and DNS-over-TLS
                // (853) -- Android's "Automatic" Private DNS mode opportunistically
                // upgrades to DoT against whatever IPs are configured (see
                // CdnVpnService.establishTun's addDnsServer calls), which used to
                // slip past a :53-only hijack entirely and leak query content to
                // whichever provider Android picked, regardless of the app's own
                // custom DNS / DoH setting.
                "dns-hijack" to listOf("any:53", "any:853"),
                // Must match VpnService.Builder().setMtu() in CdnVpnService
                // — a mismatch here means mihomo builds packets sized for a
                // different MTU than the actual OS-level tun device, and they
                // get silently dropped once they leave the tun.
                // Now read from AppSettings (user-adjustable in Settings UI).
                "mtu" to mtu,
            ),
            // Sniffs the real destination straight out of the TLS ClientHello/HTTP
            // Host header for any connection, regardless of what DNS resolved (or
            // didn't). Always on -- this isn't something worth exposing as a
            // toggle, it only ever helps and never changes routing behavior for
            // traffic that was already going to be classified correctly anyway.
            "sniffer" to linkedMapOf(
                "enable" to true,
                "parse-pure-ip" to true,
                "sniff" to linkedMapOf(
                    "TLS" to linkedMapOf("ports" to listOf("443", "8443")),
                    "HTTP" to linkedMapOf("ports" to listOf("80", "8080")),
                ),
            ),
            // Iran domains/IPs always go DIRECT, never through the tunnel -- not a
            // setting, since there's no real tradeoff: local sites are faster and
            // more reliable without an unnecessary round trip through a foreign
            // proxy, and this never affects anything actually blocked in Iran
            // (which lives outside these rulesets by definition).
            //
            // Ad/tracker/malware blocking is user-toggleable (AppSettings.adBlocker*
            // — see Settings > AD BLOCKING). When enabled we pull domain blocklists
            // from well-known clash-compatible sources and REJECT them before any
            // proxy/MATCH rule, so the request never leaves the device.
            "rule-providers" to buildMap {
                put("ir-domain", linkedMapOf(
                    "type" to "http",
                    "format" to "text",
                    "behavior" to "domain",
                    "url" to "https://raw.githubusercontent.com/Chocolate4U/Iran-clash-rules/release/ir.txt",
                    "path" to "./ruleset/ir-domain.txt",
                    "interval" to 86400,
                ))
                put("ir-ip", linkedMapOf(
                    "type" to "http",
                    "format" to "yaml",
                    "behavior" to "ipcidr",
                    "url" to "https://raw.githubusercontent.com/Chocolate4U/Iran-clash-rules/release/ircidr.yaml",
                    "path" to "./ruleset/ir-ip.yaml",
                    "interval" to 86400,
                ))
                // Ad blocker rule-providers — only added when the user has the
                // corresponding toggle on, so disabled users pay no download
                // cost and the rule engine has nothing extra to match.
                if (adBlocker) {
                    // Ads + trackers (Loyalsoldier reject list covers both in one file).
                    if (blockAds || blockTrackers) {
                        put("ad-block", linkedMapOf(
                            "type" to "http",
                            "format" to "text",
                            "behavior" to "domain",
                            "url" to "https://raw.githubusercontent.com/Loyalsoldier/clash-rules/release/reject.txt",
                            "path" to "./ruleset/ad-block.txt",
                            "interval" to 86400,
                        ))
                    }
                    // Malware domains (Loyalsoldier's separate anti-malware list).
                    if (blockMalware) {
                        put("malware-block", linkedMapOf(
                            "type" to "http",
                            "format" to "text",
                            "behavior" to "domain",
                            "url" to "https://raw.githubusercontent.com/Loyalsoldier/clash-rules/release/banad.txt",
                            "path" to "./ruleset/malware-block.txt",
                            "interval" to 86400,
                        ))
                    }
                }
            },
            "proxies" to listOf(proxy),
            "proxy-groups" to listOf(
                linkedMapOf(
                    "name" to "PROXY",
                    "type" to "select",
                    "proxies" to listOf("proxy"),
                )
            ),
            "rules" to buildList {
                // Keep private/LAN ranges off the tunnel to avoid a traffic loop.
                add("IP-CIDR,10.0.0.0/8,DIRECT")
                add("IP-CIDR,172.16.0.0/12,DIRECT")
                add("IP-CIDR,192.168.0.0/16,DIRECT")
                add("IP-CIDR,169.254.0.0/16,DIRECT")
                add("IP-CIDR,127.0.0.0/8,DIRECT")
                // Ad/tracker/malware blocking — REJECT before anything else so
                // the request dies on the device and never reaches the proxy.
                // Only the rules for the providers that were actually added
                // above go in here; if the provider wasn't added (toggle off),
                // the RULE-SET would match nothing and is omitted.
                if (adBlocker) {
                    if (blockAds || blockTrackers) add("RULE-SET,ad-block,REJECT")
                    if (blockMalware) add("RULE-SET,malware-block,REJECT")
                }
                // If the rule-provider fetch fails (e.g. no internet yet on first
                // ever launch), these RULE-SET lines just never match anything and
                // everything falls through to MATCH,PROXY same as before --
                // non-fatal either way.
                add("RULE-SET,ir-domain,DIRECT")
                add("RULE-SET,ir-ip,DIRECT")
                add("MATCH,PROXY")
            },
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

