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
    private const val KEY_LANGUAGE = "language" // "en" or "fa"

    const val DEFAULT_MTU = 9000
    const val MIN_MTU = 576   // smallest MTU any IPv4 path is guaranteed to carry
    const val MAX_MTU = 9000  // jumbo-frame ceiling; see VpnConfigBuilder's mtu comment

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
}
