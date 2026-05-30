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
     * Patch config: replace the server address with newIp.
     * Tries multiple patterns to handle any JSON formatting.
     */
    fun patchConfigWithIp(configJson: String, newIp: String): String {
        if (configJson.isBlank() || newIp.isBlank()) return configJson
        return try {
            var result = configJson
            // Pattern 1: "address" : "value" (any whitespace)
            val p1 = Regex("""("address"\s*:\s*")([^"]+)(")""")
            val m1 = p1.find(result)
            if (m1 != null) {
                val old = m1.groupValues[2]
                if (old != "127.0.0.1" && old != "localhost" && old != newIp) {
                    result = p1.replaceFirst(result, "$1$newIp$3")
                    return result
                }
            }
            // Pattern 2: first IP occurrence in the file (broader fallback)
            val ipPattern = Regex("""(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})""")
            val firstIp = ipPattern.find(result)
            if (firstIp != null) {
                val old = firstIp.value
                if (old != "127.0.0.1" && old != "0.0.0.0" && old != newIp) {
                    result = result.replaceFirst(old, newIp)
                }
            }
            result
        } catch (e: Exception) {
            configJson
        }
    }

    /**
     * Patch the active profile in Room database directly using raw SQLite.
     * No dependency on DAO method names — works with any version.
     */
    suspend fun patchActiveProfile(ctx: Context, newIp: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Try our saved profile ID first, fallback to app's selectedProfile
                var profileId = prefs(ctx).getLong("auto_ip_profile_id", 0L)
                if (profileId == 0L) {
                    val appPrefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
                    profileId = appPrefs.getLong("selectedProfile", 0L)
                }
                if (profileId == 0L) return@withContext false

                // Open DB directly with SQLiteDatabase (no Room DAO needed)
                val dbFile = ctx.getDatabasePath("xray")
                if (!dbFile.exists()) return@withContext false
                val db = android.database.sqlite.SQLiteDatabase.openDatabase(dbFile.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE)

                val cursor = db.rawQuery("SELECT config FROM profiles WHERE id = ?", arrayOf(profileId.toString()))
                if (!cursor.moveToFirst()) { cursor.close(); db.close(); return@withContext false }
                val oldConfig = cursor.getString(0)
                cursor.close()

                val patched = patchConfigWithIp(oldConfig, newIp)
                if (patched == oldConfig) {
                    db.close()
                    return@withContext oldConfig.contains(newIp)
                }

                db.execSQL("UPDATE profiles SET config = ? WHERE id = ?", arrayOf(patched, profileId.toString()))
                db.close()
                prefs(ctx).edit().putString(KEY_LAST_IP, newIp).apply()
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    /** Save a specific profile ID as the Auto-IP target profile */
    fun setAutoIpProfileId(ctx: Context, profileId: Long) {
        prefs(ctx).edit().putLong("auto_ip_profile_id", profileId).apply()
    }

    fun getAutoIpProfileId(ctx: Context): Long {
        return prefs(ctx).getLong("auto_ip_profile_id", 0L)
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
