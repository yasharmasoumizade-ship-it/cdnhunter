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
     * Patch a config JSON string: replace the FIRST "address" value with newIp.
     */
    fun patchConfigWithIp(configJson: String, newIp: String): String {
        return try {
            val regex = Regex(""""address"\s*:\s*"([^"]+)"""")
            val match = regex.find(configJson) ?: return configJson
            val oldAddress = match.groupValues[1]
            if (oldAddress == "127.0.0.1" || oldAddress == "localhost") return configJson
            configJson.replace(""""address":"$oldAddress"""", """"address":"$newIp"""")
                      .replace(""""address": "$oldAddress"""", """"address": "$newIp"""")
        } catch (e: Exception) {
            configJson
        }
    }

    /**
     * Patch the active profile in Room database directly.
     * This is the CORRECT way because TProxyService reads from DB each time.
     */
    suspend fun patchActiveProfile(ctx: Context, newIp: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val db = io.github.saeeddev94.xray.database.XrayDatabase.ref(ctx)
                val settings = io.github.saeeddev94.xray.Settings(ctx)
                val profileId = settings.selectedProfile
                if (profileId == 0L) return@withContext false
                val profile = db.profile().find(profileId) ?: return@withContext false
                val patched = patchConfigWithIp(profile.config, newIp)
                if (patched != profile.config) {
                    profile.config = patched
                    db.profile().update(profile)
                    prefs(ctx).edit().putString(KEY_LAST_IP, newIp).apply()
                    true
                } else false
            } catch (e: Exception) {
                false
            }
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
