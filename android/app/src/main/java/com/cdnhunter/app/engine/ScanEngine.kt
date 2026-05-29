package com.cdnhunter.app.engine

import com.cdnhunter.app.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*
import kotlin.random.Random
import kotlin.math.min

class ScanEngine {

    private val unsafeClient: OkHttpClient by lazy { buildUnsafeClient() }
    private var stopRequested = false

    // Progress callback
    var onProgress: ((scanned: Int, healthy: Int, total: Int) -> Unit)? = null
    var onLiveResult: ((ScanResult) -> Unit)? = null
    var onLog: ((String) -> Unit)? = null

    fun requestStop() { stopRequested = true }

    /**
     * Main scan entry: expand IPs from config, then scan them all.
     * Returns list of healthy ScanResults.
     */
    suspend fun scan(config: ScanConfig): List<ScanResult> = withContext(Dispatchers.IO) {
        stopRequested = false
        val ips = expandIps(config)
        if (ips.isEmpty()) {
            onLog?.invoke("No IPs to scan")
            return@withContext emptyList()
        }
        onLog?.invoke("Scanning ${ips.size} IPs (conc=${config.concurrency}, timeout=${config.timeout}s)")
        scanIps(ips, config)
    }

    /**
     * Expand IPs from config based on CDN provider selection.
     */
    fun expandIps(config: ScanConfig): List<String> {
        return when (config.cdnProvider) {
            CdnProvider.MANUAL -> parseManualIps(config.manualIps)
            CdnProvider.CIDR -> expandCidrs(parseCidrs(config.manualCidr), config.maxIps)
            CdnProvider.SMART -> expandSmartScan(config.maxIps)
            CdnProvider.ALL -> expandAllCdns(config.maxIps)
            else -> {
                val key = config.cdnProvider.label
                val cidrs = CdnRanges.ranges[key] ?: return emptyList()
                val picked = cidrs.shuffled().take(min(8, cidrs.size))
                expandCidrs(picked, config.maxIps)
            }
        }
    }

    private fun expandSmartScan(maxIps: Int): List<String> {
        val allCidrs = mutableListOf<String>()
        for ((_, cidrs) in CdnRanges.ranges) {
            allCidrs.addAll(cidrs.shuffled().take(4))
        }
        return expandCidrs(allCidrs.shuffled(), maxIps)
    }

    private fun expandAllCdns(maxIps: Int): List<String> {
        val perCdn = maxIps / CdnRanges.ranges.size
        val ips = mutableListOf<String>()
        for ((_, cidrs) in CdnRanges.ranges) {
            val picked = cidrs.shuffled().take(4)
            ips.addAll(expandCidrs(picked, perCdn))
        }
        return ips.shuffled().take(maxIps)
    }

    /**
     * Scan a list of IPs concurrently using coroutines + semaphore.
     */
    private suspend fun scanIps(ips: List<String>, config: ScanConfig): List<ScanResult> {
        val results = mutableListOf<ScanResult>()
        val semaphore = Semaphore(config.concurrency)
        val total = ips.size
        var scanned = 0
        val lock = Any()

        coroutineScope {
            val jobs = ips.map { ip ->
                launch {
                    if (stopRequested) return@launch
                    semaphore.acquire()
                    try {
                        if (stopRequested) return@launch
                        val result = checkIp(ip, config)
                        synchronized(lock) {
                            scanned++
                            if (result.ok) results.add(result)
                            onProgress?.invoke(scanned, results.size, total)
                            onLiveResult?.invoke(result)
                        }
                    } finally {
                        semaphore.release()
                    }
                }
            }
            jobs.joinAll()
        }

        return results.sortedBy { it.ms }
    }

    /**
     * Check a single IP: HTTPS GET with timeout.
     */
    private fun checkIp(ip: String, config: ScanConfig): ScanResult {
        val timeoutMs = (config.timeout * 1000).toLong()
        var lastCode = 0
        var bestMs = 9999

        repeat(config.retries) { attempt ->
            if (stopRequested) return ScanResult(ip = ip, ok = false, ms = bestMs, code = lastCode)
            val t0 = System.currentTimeMillis()
            try {
                val reqBuilder = Request.Builder()
                    .url("https://$ip:443/")
                    .header("User-Agent", "curl/7.88")
                    .header("Connection", "close")

                if (config.host.isNotBlank()) {
                    reqBuilder.header("Host", config.host)
                }

                val response = unsafeClient.newCall(reqBuilder.build()).execute()
                val ms = (System.currentTimeMillis() - t0).toInt()
                val code = response.code
                response.close()

                bestMs = min(bestMs, ms)
                lastCode = code

                if (code < 500) {
                    return ScanResult(ip = ip, ok = true, code = code, ms = ms)
                }
            } catch (e: Exception) {
                val ms = (System.currentTimeMillis() - t0).toInt()
                bestMs = min(bestMs, ms)
            }
            if (attempt < config.retries - 1) Thread.sleep(30)
        }

        return ScanResult(ip = ip, ok = false, ms = bestMs, code = lastCode)
    }

    // ── IP expansion helpers ──────────────────────────────────────────────────

    fun expandCidrs(cidrs: List<String>, maxIps: Int): List<String> {
        val ips = mutableSetOf<String>()
        for (cidr in cidrs) {
            if (ips.size >= maxIps) break
            val parts = cidr.split("/")
            if (parts.size != 2) continue
            val baseIp = CdnRanges.ipToLong(parts[0]) ?: continue
            val prefix = parts[1].toIntOrNull() ?: continue
            val size = 1L shl (32 - prefix)

            if (size <= 256) {
                // Small range: enumerate
                for (i in 1 until size - 1) {
                    if (ips.size >= maxIps) break
                    ips.add(CdnRanges.longToIp(baseIp + i))
                }
            } else {
                // Large range: sample random IPs
                val sampleCount = min(maxIps - ips.size, min(size.toInt() - 2, 500))
                repeat(sampleCount) {
                    val offset = Random.nextLong(1, size - 1)
                    ips.add(CdnRanges.longToIp(baseIp + offset))
                }
            }
        }
        return ips.toList().shuffled()
    }

    fun parseManualIps(text: String): List<String> {
        return text.split(Regex("[\\s,;\\n]+"))
            .map { it.trim() }
            .filter { it.matches(Regex("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) }
            .distinct()
    }

    fun parseCidrs(text: String): List<String> {
        return text.split(Regex("[\\s,;\\n]+"))
            .map { it.trim() }
            .filter { it.matches(Regex("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}/\\d{1,2}")) }
            .distinct()
    }

    // ── OkHttp client that trusts all certs (for IP scanning) ─────────────────

    private fun buildUnsafeClient(): OkHttpClient {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())

        val hostnameVerifier = HostnameVerifier { _, _ -> true }

        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier(hostnameVerifier)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(java.time.Duration.ofMillis(2600))
            .readTimeout(java.time.Duration.ofMillis(4000))
            .writeTimeout(java.time.Duration.ofMillis(2000))
            .connectionPool(okhttp3.ConnectionPool(100, 30, java.util.concurrent.TimeUnit.SECONDS))
            .retryOnConnectionFailure(false)
            .build()
    }
}
