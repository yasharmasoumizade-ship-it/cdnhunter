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
    
    // Notifications & Sounds
    private const val KEY_ALERTS_ENABLED = "alerts_enabled"
    private const val KEY_CONNECT_SOUND = "connect_sound"
    private const val KEY_DISCONNECT_SOUND = "disconnect_sound"
    private const val KEY_SILENT_MODE = "silent_mode"
    
    // Server Management
    private const val KEY_FAVORITE_SERVERS = "favorite_servers"
    private const val KEY_CUSTOM_SERVER_NAMES = "custom_server_names"
    
    // Auto-Reconnect
    private const val KEY_AUTO_RECONNECT_ENABLED = "auto_reconnect_enabled"
    private const val KEY_MAX_RETRY_ATTEMPTS = "max_retry_attempts"

    const val DEFAULT_MTU = 1500  // standard Ethernet MTU (matches v2rayNG)
    const val MIN_MTU = 576       // smallest MTU any IPv4 path is guaranteed to carry
    const val MAX_MTU = 9000      // jumbo-frame ceiling (allow custom up to 9000)

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

    // ============ Notification Settings ============
    fun alertsEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ALERTS_ENABLED, true)
    fun setAlertsEnabled(ctx: Context, value: Boolean) = prefs(ctx).edit().putBoolean(KEY_ALERTS_ENABLED, value).apply()

    fun connectSound(ctx: Context): String = prefs(ctx).getString(KEY_CONNECT_SOUND, "beep") ?: "beep"
    fun setConnectSound(ctx: Context, value: String) = prefs(ctx).edit().putString(KEY_CONNECT_SOUND, value).apply()

    fun disconnectSound(ctx: Context): String = prefs(ctx).getString(KEY_DISCONNECT_SOUND, "none") ?: "none"
    fun setDisconnectSound(ctx: Context, value: String) = prefs(ctx).edit().putString(KEY_DISCONNECT_SOUND, value).apply()

    fun silentMode(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_SILENT_MODE, false)
    fun setSilentMode(ctx: Context, value: Boolean) = prefs(ctx).edit().putBoolean(KEY_SILENT_MODE, value).apply()

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
}
