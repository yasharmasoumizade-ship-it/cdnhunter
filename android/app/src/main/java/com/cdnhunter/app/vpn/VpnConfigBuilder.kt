package com.cdnhunter.app.vpn

import android.content.Context

/**
 * Builds a mihomo (Clash.Meta) YAML config from the stored user proxy URI.
 * Replaces the old Xray-JSON builder now that CdnVpnService runs on the
 * mihomo core (see MihomoBridge).
 */
object VpnConfigBuilder {

    const val ERROR_LOG_NAME = "mihomo_error.log"

    fun buildConfig(ctx: Context, tunFd: Int, forceX25519Mlkem768: Boolean = false, disableGeoRules: Boolean = false): String {
        val prefs = SecurePrefs.vpn(ctx)
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
        // Whether the bundled geo databases are actually present in mihomo's home
        // dir (CdnVpnService copies them from assets before this runs). The
        // GEOSITE,category-ir / GEOIP,ir DIRECT rules are only emitted when both exist:
        // referencing a geo db that isn't there makes mihomo fail to start, which
        // would break the connection entirely. If they're missing we fall back to
        // the RULE-SET,ir-* providers alone — exactly the pre-change behavior — so
        // this can never regress connectivity.
        val geoDir = java.io.File(ctx.filesDir, "mihomo")
        val geoDbPresent = !disableGeoRules &&
            java.io.File(geoDir, "geosite.dat").let { it.exists() && it.length() > 0 } &&
            java.io.File(geoDir, "geoip.metadb").let { it.exists() && it.length() > 0 }
        return buildConfigFromUri(
            userConfig, tunFd, forceX25519Mlkem768, mtu, allowLan, ipv6, useDoh,
            adBlocker, blockAds, blockTrackers, blockMalware, customDnsEnabled, customDnsServers,
            geoDbPresent
        )
    }

    /** Builds a full mihomo YAML config string from a raw proxy URI (vless/trojan/vmess/ss). */
    fun buildConfigFromUri(
        uri: String, tunFd: Int, forceX25519Mlkem768: Boolean = false,
        mtu: Int = 1500, allowLan: Boolean = false, ipv6: Boolean = false, useDoh: Boolean = true,
        adBlocker: Boolean = false, blockAds: Boolean = true,
        blockTrackers: Boolean = true, blockMalware: Boolean = true,
        customDnsEnabled: Boolean = false, customDnsServers: List<String> = emptyList(),
        geoDbPresent: Boolean = false
    ): String {
        val proxy = ConfigUriParser.parseToProxy(uri, forceX25519Mlkem768) ?: defaultProxy()
        proxy["name"] = "proxy"
        return renderYaml(
            proxy, tunFd, mtu, allowLan, ipv6, useDoh,
            adBlocker, blockAds, blockTrackers, blockMalware,
            customDnsEnabled, customDnsServers, geoDbPresent
        )
    }


    private fun defaultProxy(): LinkedHashMap<String, Any> =
        linkedMapOf("name" to "proxy", "type" to "direct")

    private fun renderYaml(
        proxy: LinkedHashMap<String, Any>, tunFd: Int, mtu: Int = 1500,
        allowLan: Boolean = false, ipv6: Boolean = false, useDoh: Boolean = true,
        adBlocker: Boolean = false, blockAds: Boolean = true,
        blockTrackers: Boolean = true, blockMalware: Boolean = true,
        customDnsEnabled: Boolean = false, customDnsServers: List<String> = emptyList(),
        geoDbPresent: Boolean = false
    ): String {
        // DNS nameservers: either user-provided custom servers, or default Google
        // (8.8.8.8 / 8.8.4.4). When using custom DNS, respect the DoH setting:
        // if useDoh && custom server is IP, user should provide https://... URLs;
        // if DoH is off, provide plain IP:port or just IP.
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
            // DoH by default — TLS-wrapped, safe from ISP poisoning.
            // Google only (not Cloudflare+Google): a single provider means a
            // deterministic, always-the-same-provider answer path instead of
            // mihomo racing two different providers and returning whichever
            // answers first — which is what made every dnsleaktest.com run
            // show a different frontend IP/provider for no functional reason.
            listOf("https://8.8.8.8/dns-query", "https://8.8.4.4/dns-query")
        } else {
            // Plain DNS fallback — only if user explicitly disables DoH
            listOf("8.8.8.8:53", "8.8.4.4:53")
        }
        
        val root = linkedMapOf<String, Any>(
            "mixed-port" to 10808,
            "external-controller" to "127.0.0.1:10809",
            "allow-lan" to allowLan,
            "mode" to "rule",
            "log-level" to "error",
            "ipv6" to ipv6,
            // Geo databases for the GEOSITE,category-ir / GEOIP,ir DIRECT rules below. These
            // are loaded from the bundled files CdnVpnService copies into mihomo's
            // home dir (geosite.dat + geoip.metadb) — NOT downloaded. geodata-mode
            // false is what makes GEOIP read the shipped .metadb (geodata-mode true
            // would look for geoip.dat, which we do not ship); GEOSITE always uses
            // geosite.dat regardless. geo-auto-update off so mihomo never tries to
            // fetch a newer copy over the network (pointless in Iran, where the
            // download host is itself often blocked — the whole reason the Iran
            // routing must not depend on a live fetch).
            "geodata-mode" to false,
            "geo-auto-update" to false,
            "dns" to linkedMapOf(
                "enable" to true,
                "listen" to "0.0.0.0:1053",
                // دیگه AAAA fake-ip صادر نکن وقتی کاربر IPv6 رو خاموش کرده —
                // باید با تنظیم ipv6 در سطح tun sync باشه.
                "ipv6" to ipv6,
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
                "default-nameserver" to listOf("8.8.8.8", "8.8.4.4"),
                // ===== respect-rules / proxy-server-nameserver =====
                // Without this, mihomo's own DNS client NEVER goes through the
                // rule engine / proxy chain — it always dials the configured
                // `nameserver` entries DIRECT (only protect()-ed against the tun
                // loop, nothing else). That was the actual DNS leak: the query
                // itself — encrypted DoH or not — went straight from the device
                // to Google, bypassing the user's chosen proxy server entirely,
                // fully visible/interceptable to the local network/ISP as a
                // direct connection. respect-rules makes DNS queries go through
                // the same `rules:` matching as everything else (MATCH,PROXY),
                // which is why proxy-server-nameserver must be set too — mihomo
                // refuses to start with respect-rules on and this empty.
                "respect-rules" to true,
                "proxy-server-nameserver" to nameservers,
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
                // دفاع دوم: حتی با روت سطح Android هم اگه بسته IPv6ای به
                // rule engine برسه و کاربر IPv6 رو خاموش کرده، صریح reject
                // بشه — نه اینکه به‌طور ضمنی جایی route بشه.
                if (!ipv6) add("IP-CIDR6,::/0,REJECT")
                // Keep private/LAN ranges off the tunnel to avoid a traffic loop.
                add("IP-CIDR,10.0.0.0/8,DIRECT")
                add("IP-CIDR,172.16.0.0/12,DIRECT")
                add("IP-CIDR,192.168.0.0/16,DIRECT")
                add("IP-CIDR,169.254.0.0/16,DIRECT")
                add("IP-CIDR,127.0.0.0/8,DIRECT")
                // ===== mihomo's OWN resolver IPs — exempt, route via PROXY =====
                // CRITICAL: with respect-rules:true (set above) mihomo's own DNS
                // client dials its `nameserver` entries THROUGH this same rule list,
                // exactly like any app's traffic. The `nameserver` here is Google DoH
                // (https://8.8.8.8/dns-query, https://8.8.4.4/dns-query — or the user's
                // custom DNS). The IP-CIDR REJECT rules just below were added a day
                // after respect-rules, in a separate commit, and list those very same
                // IPs — so mihomo was REJECTING ITS OWN DNS RESOLVER. Effect:
                //   • every DIRECT-rule destination (all the Iran GEOSITE/RULE-SET
                //     domains) needs a local resolve to get a real IP to dial direct —
                //     that resolve went to 8.8.8.8, got REJECTed, so Iran traffic could
                //     not be dialed DIRECT and silently failed;
                //   • DoH itself never worked — the resolver could never reach its DoH
                //     endpoint at all.
                //   • proxied (foreign) traffic still worked, because the proxy resolves
                //     those domains remotely — which is exactly why the app looked
                //     "connected and working" while Iran-direct and DoH were both dead.
                // Route the resolver's own IPs to PROXY (honouring respect-rules' intent
                // that DNS travel encrypted through the tunnel — the reliable path from
                // inside Iran, where direct :443 to 8.8.8.8 is often blocked), placed
                // ABOVE the anti-bypass rejects so they win, and drop them from the
                // reject set below. proxy-server-nameserver resolves the proxy host
                // itself out-of-band, so this cannot loop.
                val resolverIps = nameservers.mapNotNull { literalDnsIp(it) }.distinct()
                for (ip in resolverIps) add("IP-CIDR,$ip/32,PROXY,no-resolve")
                // A HOSTNAME-based resolver (a custom DoH like https://dns.google/dns-query)
                // has no literal IP to exempt above — and its host collides head-on with the
                // DOMAIN,...,REJECT list below: `dns.google` IS in that list. Reproduced from
                // this exact code (see /tmp doh repro): a custom resolver of
                // "https://dns.google/dns-query" yields resolverIps=[] (so 8.8.8.8 is no longer
                // exempted and gets REJECTed) AND a `DOMAIN,dns.google,REJECT` that rejects the
                // user's own resolver — killing all DNS. Exempt resolver hosts to PROXY here,
                // mirroring the IP exemption, and drop them from the reject set below.
                val resolverHosts = nameservers.mapNotNull { literalDnsHost(it) }.distinct()
                for (host in resolverHosts) add("DOMAIN,$host,PROXY")

                // Known DoH/DoT provider IPs -- catches direct-to-IP DNS bypass
                // attempts with no hostname/SNI at all for the sniffer to see
                // (e.g. an app hardcoded to dial 8.8.8.8:443 or 1.1.1.1:853
                // instead of resolving a DoH hostname first). Any IP we ourselves
                // use as a resolver is exempted above, so we never reject our own DNS.
                val dohBypassIps = listOf(
                    "8.8.8.8", "8.8.4.4", "1.1.1.1", "1.0.0.1", "9.9.9.9",
                    "149.112.112.112", "208.67.222.222", "208.67.220.220",
                )
                for (ip in dohBypassIps) if (ip !in resolverIps) add("IP-CIDR,$ip/32,REJECT")
                // Known DoH/DoT provider hostnames -- REJECT before anything else, EXCEPT any
                // host the user configured as their own resolver (exempted to PROXY just above).
                // sniffer (enabled above) extracts these from the TLS SNI of the
                // HTTPS/DoT connection itself, so this catches an app dialing a
                // DoH endpoint directly even though mihomo's own DNS client never
                // saw a query for it (the actual DNS-leak-around-TUN case: plain
                // dns-hijack only catches :53/:853 traffic that LOOKS like DNS at
                // the transport level, not an HTTPS connection to a DoH host on
                // :443 that looks identical to any other website until you read
                // its SNI). Rejecting forces the app's own fallback path to the
                // system resolver, which dns-hijack already fully controls.
                val dohRejectHosts = listOf(
                    "dns.google", "dns.google.com", "cloudflare-dns.com",
                    "mozilla.cloudflare-dns.com", "dns.quad9.net", "doh.opendns.com",
                    "dns.adguard.com", "doh.dns.sb", "dns.alidns.com", "doh.pub",
                    "dns.nextdns.io", "ordns.he.net",
                )
                for (host in dohRejectHosts) if (host !in resolverHosts) add("DOMAIN,$host,REJECT")

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
                // ===== Iran direct routing (always on) =====
                // Two layers, in order, both pointing DIRECT:
                //
                // 1. GEOSITE,category-ir / GEOIP,ir — matched against the BUNDLED geosite.dat
                //    and geoip.metadb (shipped in assets, copied to mihomo's home dir
                //    by CdnVpnService). This is the primary, reliable layer: it needs
                //    no network fetch (so it works on the very first connect and in
                //    Iran where the ruleset download host is itself blocked), and
                //    critically GEOSITE matches on the DOMAIN — which fake-ip always
                //    makes available by recovering it from the synthetic IP — so Iran
                //    domains route DIRECT even though their connections carry a
                //    198.18.x fake IP that an ip-only rule could never match. GEOIP,ir
                //    carries no-resolve so it only matches connections that already
                //    have a real Iran IP (direct-to-IP), never forcing a resolve that
                //    would loop back through the rule engine under respect-rules.
                //
                // 2. RULE-SET,ir-domain / ir-ip — the HTTP rule-providers (Chocolate4U
                //    lists). Kept as a secondary layer for anything the bundled geo
                //    data misses, but no longer the ONLY thing standing between Iran
                //    traffic and MATCH,PROXY: if their GitHub fetch fails (common in
                //    Iran / on first launch), the GEOSITE/GEOIP layer above still
                //    routes Iran DIRECT. Previously these were the sole Iran rules, so
                //    a failed fetch silently sent all Iran traffic through the proxy.
                //
                // GEOSITE/GEOIP are only emitted when the geo db files are actually
                // present (geoDbPresent) — referencing a missing db makes mihomo fail
                // to start; without them we degrade to the RULE-SET layer alone.
                //
                // IMPORTANT — the geosite list name is "category-ir", NOT "ir".
                // Verified by parsing the actual bundled databases from
                // MetaCubeX/meta-rules-dat: geosite.dat has NO list named "ir" (that
                // made mihomo reject the whole config with "list ir not found in
                // geosite.dat" — a total connection failure). Its Iran aggregate list
                // is "CATEGORY-IR" (mihomo lowercases → category-ir), alongside the
                // granular CATEGORY-*-IR lists. geoip.metadb DOES carry the ISO code
                // "ir", so GEOIP,ir is correct as-is. As a belt-and-braces safety net,
                // CdnVpnService retries WITHOUT these two rules if mihomo ever fails to
                // start with a geodata error, so a wrong/corrupt bundled db can never
                // leave the user unable to connect.
                if (geoDbPresent) {
                    add("GEOSITE,category-ir,DIRECT")
                    add("GEOIP,ir,DIRECT,no-resolve")
                }
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

    // Pulls the literal IP out of a nameserver entry so it can be exempted from
    // the DoH-bypass REJECT rules (see the resolver-exemption block in rules).
    // "https://8.8.8.8/dns-query" -> "8.8.8.8", "8.8.4.4:53" -> "8.8.4.4",
    // "quic://1.1.1.1" -> "1.1.1.1", "[2606:4700::1111]:53" -> "2606:4700::1111".
    // A hostname-based resolver ("https://dns.google/dns-query") has no literal to
    // pull and returns null — nothing to exempt by IP (it would be caught, if at
    // all, by the DOMAIN rules, which our default IP-based resolvers never hit).
    private val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    private fun literalDnsIp(entry: String): String? {
        var s = entry.trim()
            .removePrefix("https://").removePrefix("http://")
            .removePrefix("quic://").removePrefix("tls://").removePrefix("h3://")
            .substringBefore("/")   // strip DoH path
        // Bracketed IPv6, optional :port — "[::1]:53" -> "::1".
        if (s.startsWith("[")) {
            return s.substringAfter("[").substringBefore("]").takeIf { it.contains(":") }
        }
        // Bare IPv6 literal (more than one colon, so not "ipv4:port").
        if (s.count { it == ':' } > 1) return s
        // IPv4, optionally ":port".
        s = s.substringBefore(":")
        return s.takeIf { IPV4.matches(it) }
    }

    // The hostname of a hostname-based resolver entry ("https://dns.google/dns-query"
    // -> "dns.google", "tls://dns.adguard.com" -> "dns.adguard.com"), or null when the
    // entry is an IP literal (handled by [literalDnsIp]) or a bare IPv6. Used to exempt a
    // user's custom DoH host from the DOMAIN,...,REJECT block so we never reject the very
    // resolver we were told to use. A host must look like a hostname (has a dot, not an
    // IPv4 literal) to qualify.
    private fun literalDnsHost(entry: String): String? {
        var s = entry.trim()
            .removePrefix("https://").removePrefix("http://")
            .removePrefix("quic://").removePrefix("tls://").removePrefix("h3://")
            .substringBefore("/")   // strip DoH path
        if (s.startsWith("[")) return null      // bracketed IPv6 — no hostname
        s = s.substringBefore(":")               // strip :port
        return s.takeIf { it.isNotBlank() && it.contains(".") && !IPV4.matches(it) }
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

