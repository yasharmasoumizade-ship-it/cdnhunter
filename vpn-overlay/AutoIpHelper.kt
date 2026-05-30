package io.github.saeeddev94.xray.helper

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Auto-IP: Scans CDN ranges, finds the best clean IP,
 * and patches the active profile config's server address.
 */
object AutoIpHelper {

    private const val PREFS = "cdnhunter_autoip"
    private const val KEY_ENABLED = "auto_ip_enabled"
    private const val KEY_LAST_IP = "last_best_ip"
    private const val KEY_CDN = "selected_cdn"

    fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isAutoEnabled(ctx: Context) = prefs(ctx).getBoolean(KEY_ENABLED, false)
    fun setAutoEnabled(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_ENABLED, v).apply()
    fun getLastIp(ctx: Context) = prefs(ctx).getString(KEY_LAST_IP, "") ?: ""
    fun getSelectedCdn(ctx: Context) = prefs(ctx).getString(KEY_CDN, "Cloudflare") ?: "Cloudflare"
    fun setSelectedCdn(ctx: Context, cdn: String) = prefs(ctx).edit().putString(KEY_CDN, cdn).apply()

    /**
     * Scan for best IP and return it (or null if none found).
     */
    suspend fun findBestIp(
        cdn: String = "Cloudflare",
        maxIps: Int = 500,
        concurrency: Int = 80,
        timeoutMs: Int = 1500,
    ): String? = withContext(Dispatchers.IO) {
        val results = CdnScanner.scan(cdn = cdn, maxIps = maxIps, concurrency = concurrency, timeoutMs = timeoutMs)
        results.firstOrNull()?.ip // already sorted by ms
    }

    /**
     * Patch a config JSON to use the given IP as server address.
     * Returns the patched config string.
     */
    fun patchConfigWithIp(configJson: String, newIp: String): String {
        return try {
            val config = JSONObject(configJson)
            val outbounds = config.optJSONArray("outbounds") ?: return configJson
            for (i in 0 until outbounds.length()) {
                val ob = outbounds.optJSONObject(i) ?: continue
                val tag = ob.optString("tag")
                if (tag == "proxy" || i == 0) {
                    val settings = ob.optJSONObject("settings") ?: continue
                    // vnext (vless/vmess)
                    val vnext = settings.optJSONArray("vnext")
                    if (vnext != null && vnext.length() > 0) {
                        vnext.getJSONObject(0).put("address", newIp)
                    }
                    // servers (trojan/shadowsocks)
                    val servers = settings.optJSONArray("servers")
                    if (servers != null && servers.length() > 0) {
                        servers.getJSONObject(0).put("address", newIp)
                    }
                    break
                }
            }
            config.toString(2)
        } catch (e: Exception) {
            configJson // return unchanged on error
        }
    }

    /**
     * Full auto flow: scan → find best IP → patch config → save.
     * Returns the best IP found, or null.
     */
    suspend fun autoApplyBestIp(
        ctx: Context,
        configJson: String,
        cdn: String? = null,
    ): Pair<String?, String> {
        val cdnToUse = cdn ?: getSelectedCdn(ctx)
        val bestIp = findBestIp(cdn = cdnToUse) ?: return null to configJson
        val patched = patchConfigWithIp(configJson, bestIp)
        prefs(ctx).edit().putString(KEY_LAST_IP, bestIp).apply()
        return bestIp to patched
    }
}
