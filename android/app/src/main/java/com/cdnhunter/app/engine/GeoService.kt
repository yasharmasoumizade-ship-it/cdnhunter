package com.cdnhunter.app.engine

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*

/**
 * Geo/country lookup for a server's IP — used to resolve the real flag/location
 * shown for each saved VPN config. Always does a live IP-based lookup (never
 * trusts a flag emoji embedded in a config's name).
 *
 * Falls back across multiple providers, including an Iranian one (ipnumberia.com),
 * so lookups still work even if a foreign geo-IP service is unreachable or rate
 * limits requests from Iranian IP ranges.
 */
class GeoService {

    private val client: OkHttpClient by lazy { buildClient() }

    data class GeoInfo(val cc: String, val lat: Double, val lon: Double, val city: String, val isp: String)

    /**
     * Providers tried in order until one returns a usable result.
     * ipnumberia.com is an Iranian geo-IP service — kept first isn't required, but
     * having it in the chain means Iranian networks/servers still resolve reliably
     * even when a foreign provider is slow, blocked, or rate-limited.
     */
    private enum class Provider { IPWHOIS, IPNUMBERIA, IPAPI_CO }

    fun lookupGeoInfo(ip: String, timeout: Float = 4.0f): GeoInfo {
        for (provider in Provider.values()) {
            try {
                val info = when (provider) {
                    Provider.IPWHOIS -> lookupIpWhoIs(ip, timeout)
                    Provider.IPNUMBERIA -> lookupIpNumberia(ip, timeout)
                    Provider.IPAPI_CO -> lookupIpApiCo(ip, timeout)
                }
                if (info != null && info.cc.isNotBlank()) return info
            } catch (e: Exception) {
                // try next provider
            }
        }
        return GeoInfo("", 0.0, 0.0, "", "")
    }

    private fun httpGet(url: String, timeout: Float): String {
        val timeoutMs = (timeout * 1000).toLong()
        val c = client.newBuilder()
            .connectTimeout(java.time.Duration.ofMillis(timeoutMs))
            .readTimeout(java.time.Duration.ofMillis(timeoutMs))
            .build()
        val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
        val response = c.newCall(request).execute()
        val body = response.body?.string() ?: ""
        response.close()
        return body
    }

    private fun lookupIpWhoIs(ip: String, timeout: Float): GeoInfo? {
        val body = httpGet("https://ipwho.is/$ip", timeout)
        if (body.isBlank()) return null
        val obj = JSONObject(body)
        val cc = obj.optString("country_code", "").uppercase()
        if (cc.isBlank()) return null
        val lat = obj.optDouble("latitude", 0.0)
        val lon = obj.optDouble("longitude", 0.0)
        val city = obj.optString("city", "")
        val isp = obj.optJSONObject("connection")?.optString("isp", "") ?: obj.optString("isp", "")
        return GeoInfo(cc, lat, lon, city, isp)
    }

    // ipnumberia.com — Iranian geo-IP service. Response shape: { "country_code": "..",
    // "city": "..", "latitude": .., "longitude": .., "isp": ".." } (falls back to
    // whatever fields are present; unknown fields default safely).
    private fun lookupIpNumberia(ip: String, timeout: Float): GeoInfo? {
        val body = httpGet("https://ipnumberia.com/api/$ip", timeout)
        if (body.isBlank()) return null
        val obj = JSONObject(body)
        val cc = (obj.optString("country_code", "").ifBlank { obj.optString("countryCode", "") }).uppercase()
        if (cc.isBlank()) return null
        val lat = obj.optDouble("latitude", 0.0)
        val lon = obj.optDouble("longitude", 0.0)
        val city = obj.optString("city", "")
        val isp = obj.optString("isp", obj.optString("org", ""))
        return GeoInfo(cc, lat, lon, city, isp)
    }

    private fun lookupIpApiCo(ip: String, timeout: Float): GeoInfo? {
        val body = httpGet("https://ipapi.co/$ip/json/", timeout)
        if (body.isBlank()) return null
        val obj = JSONObject(body)
        val cc = obj.optString("country_code", "").uppercase()
        if (cc.isBlank() || cc.length != 2) return null
        val lat = obj.optDouble("latitude", 0.0)
        val lon = obj.optDouble("longitude", 0.0)
        val city = obj.optString("city", "")
        val isp = obj.optString("org", "")
        return GeoInfo(cc, lat, lon, city, isp)
    }

    private fun buildClient(): OkHttpClient {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, a: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, a: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, trustAll, SecureRandom())

        return OkHttpClient.Builder()
            .sslSocketFactory(ctx.socketFactory, trustAll[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .followRedirects(true)
            .connectTimeout(java.time.Duration.ofSeconds(4))
            .readTimeout(java.time.Duration.ofSeconds(4))
            .build()
    }
}
