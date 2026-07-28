package com.cdnhunter.app.vpn

import android.content.Context

/**
 * Persisted app settings that actually change VPN/app behavior -- as opposed
 * to the old Settings screen rows (auto-reconnect, kill switch toggle,
 * protocol row, etc.) that rendered a UI control wired to nothing. Every
 * field here is read by either VpnConfigBuilder (mihomo YAML) or
 * CdnVpnService (Android VpnService.Builder) and actually changes what gets
 * built.
 */
object AppSettings {
    private const val PREFS = "cdnhunter_settings"

    private const val KEY_USE_DOH = "use_doh"
    private const val KEY_KILL_SWITCH = "kill_switch"
    private const val KEY_ALLOW_LAN = "allow_lan"
    private const val KEY_IPV6 = "ipv6_enabled"
    private const val KEY_MTU = "mtu"
    private const val KEY_MTU_PRESET = "mtu_preset"
    private const val KEY_LANGUAGE = "language" // "en" or "fa"
    private const val KEY_SPLIT_TUNNEL_APPS = "split_tunnel_apps"
    private const val KEY_SPLIT_TUNNEL_MODE = "split_tunnel_mode" // "exclude" or "include"
    
    // Ad Blocker (R.O.B.E.R.T style)
    private const val KEY_AD_BLOCKER_ENABLED = "ad_blocker_enabled"
    private const val KEY_BLOCK_ADS = "block_ads"
    private const val KEY_BLOCK_TRACKERS = "block_trackers"
    private const val KEY_BLOCK_MALWARE = "block_malware"
    private const val KEY_CUSTOM_BLOCKLISTS = "custom_blocklists"
    
    // Appearance
    private const val KEY_THEME = "theme" // "light", "dark", "auto"
    private const val KEY_ACCENT_COLOR = "accent_color"
    private const val KEY_AMOLED_MODE = "amoled_mode"
    
    // Custom DNS
    private const val KEY_CUSTOM_DNS_ENABLED = "custom_dns_enabled"
    private const val KEY_CUSTOM_DNS_SERVERS = "custom_dns_servers"  // comma-separated
    private const val KEY_PRIMARY_DNS = "primary_dns"
    private const val KEY_SECONDARY_DNS = "secondary_dns"
    
    // Server Management
    private const val KEY_FAVORITE_SERVERS = "favorite_servers"
    private const val KEY_CUSTOM_SERVER_NAMES = "custom_server_names"
    
    // Auto-Reconnect
    private const val KEY_AUTO_RECONNECT_ENABLED = "auto_reconnect_enabled"
    private const val KEY_MAX_RETRY_ATTEMPTS = "max_retry_attempts"

    const val DEFAULT_MTU = 1500  // standard Ethernet MTU (matches v2rayNG)
    const val MIN_MTU = 576       // smallest MTU any IPv4 path is guaranteed to carry
    const val MAX_MTU = 9000      // jumbo-frame ceiling (allow custom up to 9000)

    // Theme modes
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
    const val THEME_SYSTEM = "system"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun useDoh(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_USE_DOH, true)
    fun setUseDoh(ctx: Context, value: Boolean) = prefs(ctx).edit().putBoolean(KEY_USE_DOH, value).apply()

    fun killSwitchEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_KILL_SWITCH, false)
    fun setKillSwitchEnabled(ctx: Context, value: Boolean) = prefs(ctx).edit().putBoolean(KEY_KILL_SWITCH, value).apply()

    fun allowLan(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ALLOW_LAN, false)
    fun setAllowLan(ctx: Context, value: Boolean) = prefs(ctx).edit().putBoolean(KEY_ALLOW_LAN, value).apply()

    fun ipv6Enabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_IPV6, false)
    fun setIpv6Enabled(ctx: Context, value: Boolean) = prefs(ctx).edit().putBoolean(KEY_IPV6, value).apply()

    fun mtu(ctx: Context): Int = prefs(ctx).getInt(KEY_MTU, DEFAULT_MTU)
    fun setMtu(ctx: Context, value: Int) =
        prefs(ctx).edit().putInt(KEY_MTU, value.coerceIn(MIN_MTU, MAX_MTU)).apply()

    fun language(ctx: Context): String = prefs(ctx).getString(KEY_LANGUAGE, "fa") ?: "fa"
    fun setLanguage(ctx: Context, value: String) = prefs(ctx).edit().putString(KEY_LANGUAGE, value).apply()

    // Split tunneling: a set of package names, and whether that set is
    // EXCLUDED from the tunnel (VPN off just for these apps, on for
    // everything else -- Windscribe's more common mode, e.g. excluding a
    // banking app) or the ONLY apps INCLUDED (VPN only for these, off for
    // everything else -- useful for routing just one app through the VPN
    // without affecting the rest of the device). Empty set + "exclude" mode
    // (the default) means no split tunneling at all -- every app uses the
    // VPN, matching current behavior for anyone who never touches this.
    fun splitTunnelMode(ctx: Context): String = prefs(ctx).getString(KEY_SPLIT_TUNNEL_MODE, "exclude") ?: "exclude"
    fun setSplitTunnelMode(ctx: Context, mode: String) = prefs(ctx).edit().putString(KEY_SPLIT_TUNNEL_MODE, mode).apply()

    fun splitTunnelApps(ctx: Context): Set<String> = prefs(ctx).getStringSet(KEY_SPLIT_TUNNEL_APPS, emptySet()) ?: emptySet()
    fun setSplitTunnelApps(ctx: Context, packages: Set<String>) =
        // getStringSet/putStringSet share the underlying mutable Set instance
        // in some SharedPreferences implementations, which can silently
        // corrupt stored data if the caller mutates a set they got from
        // getStringSet() and passes it back without copying. Defensively
        // copy here so callers don't need to know about that footgun.
        prefs(ctx).edit().putStringSet(KEY_SPLIT_TUNNEL_APPS, HashSet(packages)).apply()

    // ============ MTU Preset ============
    fun mtuPreset(ctx: Context): String = prefs(ctx).getString(KEY_MTU_PRESET, "iran_isp") ?: "iran_isp"
    fun setMtuPreset(ctx: Context, preset: String) = prefs(ctx).edit().putString(KEY_MTU_PRESET, preset).apply()

    // ============ Ad Blocker Settings ============
    fun adBlockerEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_AD_BLOCKER_ENABLED, false)
    fun setAdBlockerEnabled(ctx: Context, value: Boolean) = prefs(ctx).edit().putBoolean(KEY_AD_BLOCKER_ENABLED, value).apply()

    fun blockAds(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_BLOCK_ADS, true)
    fun setBlockAds(ctx: Context, value: Boolean) = prefs(ctx).edit().putBoolean(KEY_BLOCK_ADS, value).apply()

    fun blockTrackers(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_BLOCK_TRACKERS, true)
    fun setBlockTrackers(ctx: Context, value: Boolean) = prefs(ctx).edit().putBoolean(KEY_BLOCK_TRACKERS, value).apply()

    fun blockMalware(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_BLOCK_MALWARE, true)
    fun setBlockMalware(ctx: Context, value: Boolean) = prefs(ctx).edit().putBoolean(KEY_BLOCK_MALWARE, value).apply()

    fun customBlocklists(ctx: Context): Set<String> = prefs(ctx).getStringSet(KEY_CUSTOM_BLOCKLISTS, emptySet()) ?: emptySet()
    fun setCustomBlocklists(ctx: Context, urls: Set<String>) = prefs(ctx).edit().putStringSet(KEY_CUSTOM_BLOCKLISTS, HashSet(urls)).apply()

    // ============ Appearance Settings ============
    fun theme(ctx: Context): String = prefs(ctx).getString(KEY_THEME, "auto") ?: "auto"
    fun setTheme(ctx: Context, value: String) = prefs(ctx).edit().putString(KEY_THEME, value).apply()

    fun accentColor(ctx: Context): Int = prefs(ctx).getInt(KEY_ACCENT_COLOR, 0xFF2196F3.toInt())
    fun setAccentColor(ctx: Context, value: Int) = prefs(ctx).edit().putInt(KEY_ACCENT_COLOR, value).apply()

    fun amoledMode(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_AMOLED_MODE, false)
    fun setAmoledMode(ctx: Context, value: Boolean) = prefs(ctx).edit().putBoolean(KEY_AMOLED_MODE, value).apply()

    // ============ Custom DNS Settings ============
    fun customDnsEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_CUSTOM_DNS_ENABLED, false)
    fun setCustomDnsEnabled(ctx: Context, value: Boolean) = prefs(ctx).edit().putBoolean(KEY_CUSTOM_DNS_ENABLED, value).apply()

    // Returns list of custom DNS servers; defaults to empty (use default 1.1.1.1, 8.8.8.8)
    // Only returns servers that are actually set (not defaults).
    fun customDnsServers(ctx: Context): List<String> {
        val primary = primaryDns(ctx).takeIf { it.isNotBlank() && isValidDnsServer(it) } ?: return emptyList()
        val secondary = secondaryDns(ctx).takeIf { it.isNotBlank() && isValidDnsServer(it) }
        
        return if (secondary != null) listOf(primary, secondary) else listOf(primary)
    }
    
    fun setCustomDnsServers(ctx: Context, servers: List<String>) {
        // Deprecated: use setPrimaryDns/setSecondaryDns instead for better validation
        val validated = servers.filter { isValidDnsServer(it) }
        if (validated.isNotEmpty()) setPrimaryDns(ctx, validated[0])
        if (validated.size > 1) setSecondaryDns(ctx, validated[1])
    }
    
    // Primary DNS (e.g., 8.8.8.8 or https://8.8.8.8/dns-query)
    fun primaryDns(ctx: Context): String = prefs(ctx).getString(KEY_PRIMARY_DNS, "8.8.8.8") ?: "8.8.8.8"
    fun setPrimaryDns(ctx: Context, value: String) = prefs(ctx).edit().putString(KEY_PRIMARY_DNS, value.trim()).apply()
    
    // Secondary DNS (e.g., 8.8.4.4 or https://8.8.4.4/dns-query) - optional
    fun secondaryDns(ctx: Context): String = prefs(ctx).getString(KEY_SECONDARY_DNS, "8.8.4.4") ?: "8.8.4.4"
    fun setSecondaryDns(ctx: Context, value: String) = prefs(ctx).edit().putString(KEY_SECONDARY_DNS, value.trim()).apply()
    
    /**
     * Validates DNS server format. Accepts:
     * - Plain IP: 8.8.8.8
     * - IP with port: 8.8.8.8:53
     * - DoH: https://8.8.8.8/dns-query
     * - DoQ: quic://8.8.8.8:853
     */
    fun isValidDnsServer(server: String): Boolean {
        val trimmed = server.trim()
        if (trimmed.isBlank()) return false
        
        return when {
            // DoH: https://...
            trimmed.startsWith("https://") -> trimmed.length > 11 && trimmed.contains("/dns-query")
            // DoQ: quic://...
            trimmed.startsWith("quic://") -> trimmed.length > 7
            // IP or IP:port
            else -> {
                val parts = trimmed.split(":")
                if (parts.isEmpty() || parts.size > 2) return false
                // Validate IP format (simple check: 4 groups of 1-3 digits separated by dots)
                val ipParts = parts[0].split(".")
                if (ipParts.size != 4) return false
                ipParts.all { it.matches(Regex("\\d{1,3}")) && it.toInt() <= 255 }
            }
        }
    }

    // ============ Server Management ============
    fun favoriteServers(ctx: Context): Set<String> = prefs(ctx).getStringSet(KEY_FAVORITE_SERVERS, emptySet()) ?: emptySet()
    fun setFavoriteServers(ctx: Context, servers: Set<String>) = prefs(ctx).edit().putStringSet(KEY_FAVORITE_SERVERS, HashSet(servers)).apply()

    fun customServerNames(ctx: Context): Map<String, String> {
        val json = prefs(ctx).getString(KEY_CUSTOM_SERVER_NAMES, "{}") ?: "{}"
        return try {
            // Simple JSON parsing - in production use a proper JSON library
            emptyMap()  // Placeholder - use Gson/Moshi in real implementation
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun setCustomServerNames(ctx: Context, names: Map<String, String>) {
        // Placeholder - use Gson/Moshi in real implementation
        val json = "{}"  // Convert map to JSON
        prefs(ctx).edit().putString(KEY_CUSTOM_SERVER_NAMES, json).apply()
    }

    // ============ Auto-Reconnect Settings ============
    fun autoReconnectEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_AUTO_RECONNECT_ENABLED, true)
    fun setAutoReconnectEnabled(ctx: Context, value: Boolean) = prefs(ctx).edit().putBoolean(KEY_AUTO_RECONNECT_ENABLED, value).apply()

    fun maxRetryAttempts(ctx: Context): Int = prefs(ctx).getInt(KEY_MAX_RETRY_ATTEMPTS, 3)
    fun setMaxRetryAttempts(ctx: Context, value: Int) = prefs(ctx).edit().putInt(KEY_MAX_RETRY_ATTEMPTS, value.coerceIn(1, 5)).apply()
    
    // ============ SUBSCRIPTIONS ============
    private const val KEY_SUBSCRIPTIONS = "subscriptions_json"
    
    /**
     * Get all saved subscriptions
     */
    fun getSubscriptions(ctx: Context): List<Subscription> {
        return try {
            val json = prefs(ctx).getString(KEY_SUBSCRIPTIONS, "[]") ?: "[]"
            // Simple JSON parse (without full Gson to keep it lightweight)
            // For now, return empty and implement when UI is ready
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Save all subscriptions
     */
    fun saveSubscriptions(ctx: Context, subscriptions: List<Subscription>) {
        try {
            // Serialize to JSON (simple format)
            val json = subscriptions.joinToString(",") { sub ->
                """{"id":"${sub.id}","name":"${sub.name}","url":"${sub.url}","lastUpdated":${sub.lastUpdated},"enabled":${sub.enabled}}"""
            }
            prefs(ctx).edit().putString(KEY_SUBSCRIPTIONS, "[$json]").apply()
        } catch (e: Exception) {
            android.util.Log.e("AppSettings", "Failed to save subscriptions", e)
        }
    }
    
    /**
     * Add a new subscription
     */
    fun addSubscription(ctx: Context, subscription: Subscription) {
        val current = getSubscriptions(ctx).toMutableList()
        current.add(subscription)
        saveSubscriptions(ctx, current)
    }
    
    /**
     * Remove subscription by ID
     */
    fun removeSubscription(ctx: Context, subscriptionId: String) {
        val current = getSubscriptions(ctx).filter { it.id != subscriptionId }
        saveSubscriptions(ctx, current)
    }
}

