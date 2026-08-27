package com.cdnhunter.app.vpn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Resolves which custom Firebase Auth domain to use, so the app never has to hardcode a
 * plaintext, filterable domain in a static string a decompiler can grep for.
 *
 * The gateway URL below is the only address baked into the APK, and it is a lightweight
 * Cloudflare Worker that serves nothing but a JSON list of the real auth domains behind a
 * simple shared-secret header -- visiting it without the header returns a plain 404, so a
 * scanner probing the URL directly learns nothing. The real domains (fronted through
 * Cloudflare on a personal domain) are fetched at runtime and never appear as a literal
 * string anywhere in the compiled code.
 *
 * If the gateway itself is unreachable (network filtering, gateway down, etc.), [FALLBACK]
 * is used -- a single, rarely-used backup domain, kept separate from the primary rotation
 * so exhausting it doesn't cost anything the gateway already offers.
 */
object AuthDomainResolver {

    private const val GATEWAY_URL = "https://nx7-svc.mowzyz.workers.dev/"
    private const val GATEWAY_KEY = "th-9f2k"

    // Held as char codes rather than a literal string, so "zx88.bkbshop.ir" (or whatever the
    // fallback is) does not appear as a plain grep-able string in the compiled class file.
    private val FALLBACK_CHARS = intArrayOf(
        122, 120, 56, 56, 46, 98, 107, 98, 115, 104, 111, 112, 46, 105, 114,
    )
    private val FALLBACK: String
        get() = String(FALLBACK_CHARS.map { it.toChar() }.toCharArray())

    private val client = OkHttpClient()

    /** Fetches the current domain list from the gateway; returns null on any failure so the
     *  caller can fall back without this throwing. */
    suspend fun resolveDomains(): List<String>? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GATEWAY_URL)
                .addHeader("x-app-key", GATEWAY_KEY)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val arr = json.getJSONArray("domains")
                (0 until arr.length()).map { arr.getString(it) }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** The domain to actually use: the first entry from the gateway's live list, or
     *  [FALLBACK] if the gateway could not be reached at all. */
    suspend fun resolveActiveDomain(): String {
        val domains = resolveDomains()
        return domains?.firstOrNull() ?: FALLBACK
    }
}
