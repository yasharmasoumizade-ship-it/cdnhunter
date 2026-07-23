package com.cdnhunter.app.vpn

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Builds xray JSON config from stored user config (URI).
 * Adds fragment outbound, SOCKS inbound, DNS, routing.
 */
object VpnConfigBuilder {

    const val ERROR_LOG_NAME = "xray_error.log"

    fun buildConfig(ctx: Context): String {
        val prefs = ctx.getSharedPreferences("cdnhunter_vpn", Context.MODE_PRIVATE)
        val userConfig = prefs.getString("user_config", "") ?: ""
        val fragmentEnabled = prefs.getBoolean("fragment_enabled", true)
        val fragLength = prefs.getString("frag_length", "100-200") ?: "100-200"
        val fragInterval = prefs.getString("frag_interval", "10-20") ?: "10-20"

        // Parse user config URI to outbound
        val outbound = ConfigUriParser.parseToOutbound(userConfig) ?: defaultOutbound()

        // Write xray's own error log to a file so we can surface it in the UI
        val errorLogPath = File(ctx.filesDir, ERROR_LOG_NAME).absolutePath

        return buildFullConfig(outbound, fragmentEnabled, fragLength, fragInterval, errorLogPath).toString(2)
    }

    /**
     * Convenience method: builds a full xray JSON config from a raw URI string
     * with fragment enabled (length=100-200, interval=10-20).
     * Useful for testing and direct URI usage without Context/SharedPreferences.
     */
    fun buildConfigFromUri(uri: String): String {
        val outbound = ConfigUriParser.parseToOutbound(uri) ?: defaultOutbound()
        return buildFullConfig(
            proxyOutbound = outbound,
            fragmentEnabled = true,
            fragLength = "100-200",
            fragInterval = "10-20",
            errorLogPath = ""
        ).toString(2)
    }


    private fun buildFullConfig(
        proxyOutbound: JSONObject,
        fragmentEnabled: Boolean,
        fragLength: String,
        fragInterval: String,
        errorLogPath: String
    ): JSONObject {
        val config = JSONObject()

        // Log (use "info" level so we capture connection failures; write to file for UI)
        val log = JSONObject().put("loglevel", "info")
        if (errorLogPath.isNotBlank()) log.put("error", errorLogPath)
        config.put("log", log)

        // DNS
        val dns = JSONObject()
        dns.put("servers", JSONArray().put("1.1.1.1").put("8.8.8.8"))
        config.put("dns", dns)

        // Inbounds - SOCKS proxy
        val socks = JSONObject()
        socks.put("listen", "127.0.0.1")
        socks.put("port", 10808)
        socks.put("protocol", "socks")
        val socksSettings = JSONObject().put("udp", true)
        socks.put("settings", socksSettings)
        val sniffing = JSONObject().put("enabled", true)
            .put("destOverride", JSONArray().put("http").put("tls").put("quic"))
        socks.put("sniffing", sniffing)
        socks.put("tag", "socks")
        config.put("inbounds", JSONArray().put(socks))

        // Outbounds
        val outbounds = JSONArray()

        // Proxy outbound (from user config)
        proxyOutbound.put("tag", "proxy")
        if (fragmentEnabled) {
            // Add dialerProxy to route through fragment
            val ss = proxyOutbound.optJSONObject("streamSettings") ?: JSONObject()
            val so = ss.optJSONObject("sockopt") ?: JSONObject()
            so.put("dialerProxy", "fragment")
            ss.put("sockopt", so)
            proxyOutbound.put("streamSettings", ss)
        }
        outbounds.put(proxyOutbound)

        // Fragment outbound
        if (fragmentEnabled) {
            val fragment = JSONObject()
            fragment.put("protocol", "freedom")
            fragment.put("tag", "fragment")
            val fragSettings = JSONObject()
            fragSettings.put("domainStrategy", "AsIs")
            val frag = JSONObject()
            frag.put("packets", "tlshello")
            frag.put("length", fragLength)
            frag.put("interval", fragInterval)
            fragSettings.put("fragment", frag)
            fragment.put("settings", fragSettings)
            val fragSs = JSONObject()
            fragSs.put("sockopt", JSONObject().put("tcpNoDelay", true))
            fragment.put("streamSettings", fragSs)
            outbounds.put(fragment)
        }

        // Direct + Block
        outbounds.put(JSONObject().put("protocol", "freedom").put("tag", "direct"))
        outbounds.put(JSONObject().put("protocol", "blackhole").put("tag", "block"))
        config.put("outbounds", outbounds)

        // Routing
        val routing = JSONObject().put("domainStrategy", "IPIfNonMatch")
        val rules = JSONArray()
        // DNS rule
        val dnsRule = JSONObject()
        dnsRule.put("type", "field")
        dnsRule.put("ip", JSONArray().put("1.1.1.1").put("8.8.8.8"))
        dnsRule.put("port", 53)
        dnsRule.put("outboundTag", "proxy")
        rules.put(dnsRule)
        // Private IP -> direct (CRITICAL: prevents VPN traffic loop)
        val privateRule = JSONObject()
        privateRule.put("type", "field")
        privateRule.put("ip", JSONArray()
            .put("10.0.0.0/8")
            .put("172.16.0.0/12")
            .put("192.168.0.0/16")
            .put("169.254.0.0/16"))
        privateRule.put("outboundTag", "direct")
        rules.put(privateRule)
        // Localhost -> direct
        val localRule = JSONObject()
        localRule.put("type", "field")
        localRule.put("ip", JSONArray().put("127.0.0.0/8"))
        localRule.put("outboundTag", "direct")
        rules.put(localRule)
        routing.put("rules", rules)
        config.put("routing", routing)

        // Stats (for traffic monitoring)
        config.put("stats", JSONObject())
        val policy = JSONObject()
        val sysPolicy = JSONObject()
        sysPolicy.put("statsOutboundUplink", true)
        sysPolicy.put("statsOutboundDownlink", true)
        policy.put("system", sysPolicy)
        config.put("policy", policy)

        return config
    }

    private fun defaultOutbound(): JSONObject {
        val ob = JSONObject()
        ob.put("protocol", "freedom")
        return ob
    }
}
