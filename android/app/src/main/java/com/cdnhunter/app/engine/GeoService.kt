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

    /**
     * Resolves the given host (which may already be an IP, or a domain name) to a
     * plain IP string suitable for the geo-IP providers below. The provider APIs
     * are documented and tested against raw IPs (e.g. ipwho.is/8.8.4.4) — passing a
     * domain straight through was silently failing for configs whose server address
     * is a hostname rather than an IP, which is why those showed a blank/gray flag.
     */
    private fun resolveToIp(host: String): String {
        // Already an IPv4/IPv6 literal — nothing to resolve.
        val isIpLiteral = host.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")) || host.contains(":")
        if (isIpLiteral) return host
        return try {
            java.net.InetAddress.getByName(host).hostAddress ?: host
        } catch (e: Exception) {
            host
        }
    }

    fun lookupGeoInfo(host: String, timeout: Float = 4.0f): GeoInfo {
        val ip = resolveToIp(host)
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

    /**
     * Looks up geo info for the ACTIVE TUNNEL'S REAL EXIT IP, by routing the
     * lookup request itself through the local mixed-port proxy instead of
     * resolving any hostname directly. This is the only reliable way to get
     * a server's true location: lookupGeoInfo() above resolves the config's
     * server/SNI hostname directly on-device (before the tunnel exists), so
     * for any domain sitting behind a CDN — Cloudflare fronting, some reality
     * setups, etc. — it reports the CDN edge node's location (e.g. wherever
     * Cloudflare happened to route THIS device's DNS query), not the actual
     * backend server's location. Once connected, asking "what IP does the
     * outside world see me as" through the tunnel itself sidesteps all of
     * that: the geo-IP service directly sees the real exit IP, matching how
     * Hiddify's getCurrentIpInfo(proxyOnly: true) works.
     *
     * mixedPort must be the mihomo mixed-port this app's VPN service actually
     * started (see VpnConfigBuilder — currently always 10808).
     */
    fun lookupCurrentExitGeoInfo(mixedPort: Int = 10808, timeout: Float = 5.0f): GeoInfo {
        val proxyClient = buildProxiedClient(mixedPort, timeout)
        // Same provider list/order as lookupGeoInfo, just with no host in the
        // URL — each of these returns info about the caller's own apparent IP
        // when queried with no path/query, which is exactly what we want here.
        val attempts = listOf(
            { proxyGet(proxyClient, "https://ipwho.is/", timeout) },
            { proxyGet(proxyClient, "https://ipnumberia.com/api/", timeout) },
            { proxyGet(proxyClient, "https://ipapi.co/json/", timeout) },
        )
        for (attempt in attempts) {
            try {
                val body = attempt() ?: continue
                if (body.isBlank()) continue
                val obj = JSONObject(body)
                val cc = (obj.optString("country_code", "").ifBlank { obj.optString("countryCode", "") }).uppercase()
                if (cc.isBlank()) continue
                val lat = obj.optDouble("latitude", 0.0)
                val lon = obj.optDouble("longitude", 0.0)
                val city = obj.optString("city", "")
                val isp = obj.optJSONObject("connection")?.optString("isp", "")
                    ?: obj.optString("isp", obj.optString("org", ""))
                return GeoInfo(cc, lat, lon, city, isp)
            } catch (e: Exception) {
                // try next provider
            }
        }
        return GeoInfo("", 0.0, 0.0, "", "")
    }

    /**
     * The public IP the outside world currently sees for this device: through the
     * live tunnel when [proxied] (mihomo's mixed port, the same path
     * lookupCurrentExitGeoInfo takes), otherwise over the normal network path.
     *
     * The distinction matters for the IP shown on Home — this app's own process is
     * excluded from its own VPN (see addDisallowedApplication in CdnVpnService), so
     * an unproxied lookup while connected reports the ISP's IP, not the tunnel's
     * exit. Returns "" when every provider fails.
     */
    fun lookupCurrentIp(proxied: Boolean, mixedPort: Int = 10808, timeout: Float = 5.0f): String {
        val proxyClient = if (proxied) buildProxiedClient(mixedPort, timeout) else null
        for (url in listOf("https://ipwho.is/", "https://ipapi.co/json/")) {
            try {
                val body =
                    if (proxyClient != null) proxyGet(proxyClient, url, timeout)
                    else httpGet(url, timeout)
                if (body.isNullOrBlank()) continue
                val ip = JSONObject(body).optString("ip", "")
                if (ip.isNotBlank()) return ip
            } catch (e: Exception) {
                // try the next provider
            }
        }
        return ""
    }

    private fun proxyGet(client: OkHttpClient, url: String, timeout: Float): String? {
        val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    // A client identical to the normal trust-all client, except every request
    // goes through the local SOCKS/HTTP mixed-port proxy mihomo exposes on
    // 127.0.0.1 instead of the device's normal network path.
    private fun buildProxiedClient(mixedPort: Int, timeout: Float): OkHttpClient {
        val timeoutMs = (timeout * 1000).toLong()
        val proxy = java.net.Proxy(
            java.net.Proxy.Type.HTTP,
            java.net.InetSocketAddress("127.0.0.1", mixedPort)
        )
        return client.newBuilder()
            .proxy(proxy)
            .connectTimeout(java.time.Duration.ofMillis(timeoutMs))
            .readTimeout(java.time.Duration.ofMillis(timeoutMs))
            .build()
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
