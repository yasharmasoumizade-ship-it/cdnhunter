package com.cdnhunter.app.engine

import com.cdnhunter.app.data.CdnRanges
import com.cdnhunter.app.data.ScanResult
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*
import kotlin.math.min

/**
 * Geo/country lookup service — checks IP country codes via multiple APIs.
 * Filters out Iranian IPs.
 */
class GeoService {

    private val client: OkHttpClient by lazy { buildClient() }
    private val cache = mutableMapOf<String, String>()

    private val geoApis = listOf(
        GeoApi("https://ipwho.is/{ip}", listOf("country_code")),
        GeoApi("https://ipapi.co/{ip}/json/", listOf("country_code")),
        GeoApi("https://freeipapi.com/api/json/{ip}", listOf("countryCode")),
    )

    data class GeoApi(val urlTemplate: String, val fields: List<String>)

    /**
     * Look up country codes for a batch of IPs concurrently.
     * Returns map of IP -> 2-letter country code.
     */
    suspend fun lookupBatch(
        ips: List<String>,
        timeout: Float = 3.0f,
        concurrency: Int = 20
    ): Map<String, String> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, String>()
        val toFetch = ips.filter { it !in cache }

        // Return cached results immediately
        for (ip in ips) {
            cache[ip]?.let { result[ip] = it }
        }

        if (toFetch.isEmpty()) return@withContext result

        val semaphore = Semaphore(min(concurrency, toFetch.size))
        val lock = Any()

        coroutineScope {
            toFetch.map { ip ->
                launch {
                    semaphore.acquire()
                    try {
                        val cc = lookupSingle(ip, timeout)
                        synchronized(lock) {
                            cache[ip] = cc
                            result[ip] = cc
                        }
                    } finally {
                        semaphore.release()
                    }
                }
            }.joinAll()
        }

        result
    }

    /**
     * Look up a single IP's country code with fallback chain.
     */
    private fun lookupSingle(ip: String, timeout: Float): String {
        for (api in geoApis) {
            try {
                val url = api.urlTemplate.replace("{ip}", ip)
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Accept", "application/json")
                    .build()

                val timeoutMs = (timeout * 1000).toLong()
                val c = client.newBuilder()
                    .connectTimeout(java.time.Duration.ofMillis(timeoutMs))
                    .readTimeout(java.time.Duration.ofMillis(timeoutMs))
                    .build()

                val response = c.newCall(request).execute()
                val body = response.body?.string() ?: ""
                response.close()

                if (body.isNotBlank()) {
                    val cc = parseCountryCode(body, api.fields)
                    if (cc.isNotBlank()) return cc
                }
            } catch (e: Exception) {
                // Try next API
            }
        }
        return ""
    }

    private fun parseCountryCode(json: String, fields: List<String>): String {
        return try {
            val obj = JSONObject(json)
            for (field in fields) {
                val value = obj.optString(field, "")
                if (value.length == 2 && value.all { it.isLetter() }) {
                    return value.uppercase()
                }
            }
            // Fallback: check nested or regex
            if (json.lowercase().contains("\"ir\"") || json.lowercase().contains("iran")) {
                return "IR"
            }
            ""
        } catch (e: Exception) { "" }
    }

    /**
     * Filter out Iranian IPs from results.
     * Returns filtered list (IPs confirmed as IR are removed).
     */
    suspend fun filterIranIps(
        results: List<ScanResult>,
        timeout: Float = 3.0f
    ): List<ScanResult> {
        if (results.isEmpty()) return results

        val ips = results.map { it.ip }
        val ccMap = lookupBatch(ips, timeout)

        return results.mapNotNull { result ->
            val cc = ccMap[result.ip] ?: ""
            val isIran = cc.uppercase() == "IR" || hasIranDomain(result.frontingSni)
            if (isIran) null
            else result.copy(country = cc)
        }
    }

    private fun hasIranDomain(host: String): Boolean {
        if (host.isBlank()) return false
        val h = host.lowercase()
        return CdnRanges.iranDomains.any { h.contains(it) }
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
