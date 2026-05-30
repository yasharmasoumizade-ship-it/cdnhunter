package io.github.saeeddev94.xray.helper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Self-contained CDN clean-IP scanner.
 * Tests TCP connectivity to port 443 across CDN ranges and ranks by latency.
 */
object CdnScanner {

    data class Result(val ip: String, val ms: Int)

    val cdnRanges: Map<String, List<String>> = mapOf(
        "Cloudflare" to listOf(
            "104.16.0.0/13", "104.24.0.0/14", "172.64.0.0/13", "162.158.0.0/15",
            "141.101.64.0/18", "108.162.192.0/18", "173.245.48.0/20", "188.114.96.0/20",
            "190.93.240.0/20", "197.234.240.0/22", "198.41.128.0/17", "131.0.72.0/22",
            "103.21.244.0/22", "103.22.200.0/22", "103.31.4.0/22"
        ),
        "Fastly" to listOf(
            "151.101.0.0/16", "146.75.0.0/16", "199.232.0.0/16", "23.235.32.0/20"
        ),
        "Akamai" to listOf(
            "23.32.0.0/11", "23.192.0.0/11", "104.64.0.0/10", "184.24.0.0/13"
        ),
    )

    @Volatile private var stop = false
    fun requestStop() { stop = true }

    private fun ipToLong(ip: String): Long? {
        val p = ip.split("."); if (p.size != 4) return null
        return try { p.fold(0L) { a, s -> (a shl 8) or s.toLong() } } catch (e: Exception) { null }
    }
    private fun longToIp(v: Long) = "${(v shr 24) and 0xFF}.${(v shr 16) and 0xFF}.${(v shr 8) and 0xFF}.${v and 0xFF}"

    private fun sampleIps(cidrs: List<String>, count: Int): List<String> {
        val ips = LinkedHashSet<String>()
        val perCidr = (count / cidrs.size).coerceAtLeast(1)
        for (cidr in cidrs) {
            val parts = cidr.split("/"); if (parts.size != 2) continue
            val base = ipToLong(parts[0]) ?: continue
            val prefix = parts[1].toIntOrNull() ?: continue
            val size = 1L shl (32 - prefix)
            repeat(perCidr) {
                if (size > 2) ips.add(longToIp(base + Random.nextLong(1, size - 1)))
            }
        }
        return ips.shuffled()
    }

    /**
     * Scan a CDN for responsive IPs.
     * @param cdn provider name (key of cdnRanges) or "All"
     * @param maxIps how many IPs to probe
     * @param timeoutMs connect timeout per IP
     * @param onProgress (scanned, found, total)
     * @param onFound called for each healthy IP
     */
    suspend fun scan(
        cdn: String,
        maxIps: Int = 800,
        concurrency: Int = 100,
        timeoutMs: Int = 1500,
        onProgress: (Int, Int, Int) -> Unit = { _, _, _ -> },
        onFound: (Result) -> Unit = {},
    ): List<Result> = withContext(Dispatchers.IO) {
        stop = false
        val cidrs = if (cdn == "All") cdnRanges.values.flatten() else (cdnRanges[cdn] ?: emptyList())
        val ips = sampleIps(cidrs, maxIps)
        val results = java.util.Collections.synchronizedList(mutableListOf<Result>())
        val sem = Semaphore(concurrency)
        val scanned = AtomicInteger(0)
        val found = AtomicInteger(0)
        val total = ips.size
        val updateEvery = (total / 100).coerceAtLeast(1)

        coroutineScope {
            ips.forEach { ip ->
                launch(Dispatchers.IO) {
                    if (stop) return@launch
                    sem.acquire()
                    try {
                        if (stop) return@launch
                        val t0 = System.currentTimeMillis()
                        val ok = try {
                            Socket().use { s -> s.connect(InetSocketAddress(ip, 443), timeoutMs); true }
                        } catch (e: Exception) { false }
                        val sc = scanned.incrementAndGet()
                        if (ok) {
                            val ms = (System.currentTimeMillis() - t0).toInt()
                            val r = Result(ip, ms)
                            results.add(r); found.incrementAndGet(); onFound(r)
                        }
                        if (sc % updateEvery == 0 || sc == total) onProgress(sc, found.get(), total)
                    } finally { sem.release() }
                }
            }
        }
        onProgress(total, found.get(), total)
        results.sortedBy { it.ms }
    }
}
