package com.cdnhunter.app.engine

import com.cdnhunter.app.data.CdnRanges
import com.cdnhunter.app.data.ScanResult
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*
import kotlin.math.min

class FrontingEngine {

    var onLog: ((String) -> Unit)? = null

    /**
     * Check fronting for all healthy IPs.
     * Returns updated ScanResults with fronting info.
     */
    suspend fun checkFronting(
        healthy: List<ScanResult>,
        timeout: Float = 4.0f,
        sniOverride: String = "",
        concurrency: Int = 50
    ): List<ScanResult> = withContext(Dispatchers.IO) {
        if (healthy.isEmpty()) return@withContext emptyList()

        val semaphore = Semaphore(min(concurrency, healthy.size))
        val results = Array<ScanResult?>(healthy.size) { null }

        coroutineScope {
            healthy.forEachIndexed { idx, result ->
                launch {
                    semaphore.acquire()
                    try {
                        results[idx] = checkSingleFronting(result, timeout, sniOverride)
                    } finally {
                        semaphore.release()
                    }
                }
            }
        }

        results.filterNotNull()
    }

    private fun checkSingleFronting(
        result: ScanResult,
        timeout: Float,
        sniOverride: String
    ): ScanResult {
        val ip = result.ip
        val cdn = CdnRanges.detectCdn(ip)
        val pairs = CdnRanges.frontingPairs[cdn] ?: CdnRanges.frontingPairs["Unknown"]!!

        // If user provided SNI override, prepend it
        val allPairs = if (sniOverride.isNotBlank()) {
            val overridePairs = pairs.map { sniOverride to it.second }
            (overridePairs + pairs).distinctBy { "${it.first}|${it.second}" }
        } else pairs

        for ((sni, hostHeader) in allPairs) {
            val fr = doFrontingCheck(ip, sni, hostHeader, timeout)
            if (fr.ok) {
                return result.copy(
                    frontingOk = true,
                    frontingSni = sni,
                    frontingHost = hostHeader,
                    cdn = cdn,
                )
            }
        }

        // No pair worked
        val firstSni = allPairs.firstOrNull()?.first ?: ""
        return result.copy(
            frontingOk = false,
            frontingSni = firstSni,
            cdn = cdn,
        )
    }

    private data class FrontingResult(val ok: Boolean, val code: Int, val ms: Int)

    private fun doFrontingCheck(
        ip: String,
        sni: String,
        hostHeader: String,
        timeout: Float
    ): FrontingResult {
        val timeoutMs = (timeout * 1000).toInt()
        try {
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, a: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, a: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }), SecureRandom())

            val t0 = System.currentTimeMillis()
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, 443), timeoutMs)
            socket.soTimeout = timeoutMs

            val sslSocket = sslContext.socketFactory.createSocket(
                socket, sni, 443, true
            ) as SSLSocket

            sslSocket.sslParameters = sslSocket.sslParameters.apply {
                serverNames = listOf(SNIHostName(sni))
            }
            sslSocket.startHandshake()

            // Send HEAD request
            val req = "HEAD / HTTP/1.1\r\nHost: $hostHeader\r\n" +
                    "User-Agent: Mozilla/5.0\r\nAccept: */*\r\nConnection: close\r\n\r\n"
            sslSocket.outputStream.write(req.toByteArray())
            sslSocket.outputStream.flush()

            // Read response
            val buf = ByteArray(1024)
            val read = sslSocket.inputStream.read(buf)
            val ms = (System.currentTimeMillis() - t0).toInt()

            sslSocket.close()
            socket.close()

            if (read > 0) {
                val resp = String(buf, 0, read)
                if (resp.startsWith("HTTP/")) {
                    val parts = resp.split(" ")
                    if (parts.size >= 2) {
                        val code = parts[1].toIntOrNull() ?: 0
                        val ok = code in CdnRanges.frontingOkCodes
                        return FrontingResult(ok, code, ms)
                    }
                }
            }
            return FrontingResult(false, 0, ms)
        } catch (e: Exception) {
            return FrontingResult(false, 0, 9999)
        }
    }
}

/**
 * Throughput testing engine.
 */
class ThroughputEngine {

    private val paths = listOf("/robots.txt", "/", "/favicon.ico", "/cdn-cgi/trace")

    var onLog: ((String) -> Unit)? = null

    /**
     * Test throughput for fronting-verified IPs.
     */
    suspend fun testThroughput(
        ips: List<ScanResult>,
        timeout: Float = 5.0f,
        concurrency: Int = 20
    ): List<ScanResult> = withContext(Dispatchers.IO) {
        if (ips.isEmpty()) return@withContext emptyList()

        val semaphore = Semaphore(min(concurrency, ips.size))
        val results = Array<ScanResult?>(ips.size) { null }

        coroutineScope {
            ips.forEachIndexed { idx, result ->
                launch {
                    semaphore.acquire()
                    try {
                        results[idx] = testSingleThroughput(result, timeout)
                    } finally {
                        semaphore.release()
                    }
                }
            }
        }

        results.filterNotNull().sortedBy { if (it.kbps > 0) -it.kbps.toInt() else 9999 }
    }

    private fun testSingleThroughput(result: ScanResult, timeout: Float): ScanResult {
        val ip = result.ip
        val host = result.frontingSni.ifBlank { ip }
        val timeoutMs = (timeout * 1000).toInt()

        for (path in paths) {
            try {
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, a: String?) {}
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, a: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                }), SecureRandom())

                val socket = Socket()
                socket.connect(InetSocketAddress(ip, 443), timeoutMs)
                socket.soTimeout = timeoutMs

                val sslSocket = sslContext.socketFactory.createSocket(
                    socket, host, 443, true
                ) as SSLSocket
                sslSocket.startHandshake()

                val req = "GET $path HTTP/1.0\r\nHost: $host\r\n" +
                        "User-Agent: curl/7.88\r\nConnection: close\r\n\r\n"

                val t0 = System.currentTimeMillis()
                sslSocket.outputStream.write(req.toByteArray())
                sslSocket.outputStream.flush()

                var totalBytes = 0
                val buf = ByteArray(4096)
                while (totalBytes < 8192) {
                    val read = sslSocket.inputStream.read(buf)
                    if (read <= 0) break
                    totalBytes += read
                }

                val elapsed = (System.currentTimeMillis() - t0).coerceAtLeast(1)
                sslSocket.close()
                socket.close()

                if (totalBytes > 0) {
                    val kbps = (totalBytes.toFloat() / 1024f) / (elapsed.toFloat() / 1000f)
                    return result.copy(kbps = kbps)
                }
            } catch (e: Exception) {
                // Try next path
            }
        }
        return result.copy(kbps = 0f)
    }
}
