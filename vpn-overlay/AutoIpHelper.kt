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
     * Uses regex replacement to avoid JSON re-serialization issues.
     * Only patches the FIRST "address" field in the proxy outbound.
     */
    fun patchConfigWithIp(configJson: String, newIp: String): String {
        return try {
            // Find the first "address": "..." in the config and replace its value
            // This is safe because the first address in outbounds[0] is always the server IP
            val regex = Regex(""""address"\s*:\s*"([^"]+)"""")
            val match = regex.find(configJson) ?: return configJson
            val oldAddress = match.groupValues[1]
            // Only patch if it looks like an IP or domain (not localhost)
            if (oldAddress == "127.0.0.1" || oldAddress == "localhost") return configJson
            configJson.replaceFirst(""""address": "$oldAddress"""", """"address": "$newIp"""")
                .replaceFirst(""""address":"$oldAddress"""", """"address":"$newIp"""")
        } catch (e: Exception) {
            configJson
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
