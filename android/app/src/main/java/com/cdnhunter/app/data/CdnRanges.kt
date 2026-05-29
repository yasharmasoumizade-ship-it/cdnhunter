package com.cdnhunter.app.data

/**
 * CDN IP ranges — same as the Python version.
 * Used for scanning and CDN detection.
 */
object CdnRanges {

    val ranges: Map<String, List<String>> = mapOf(
        "Akamai" to listOf(
            "92.122.0.0/15","92.123.0.0/16","88.221.64.0/18","88.221.128.0/18","88.221.192.0/18",
            "95.100.0.0/15","95.101.0.0/16","213.42.0.0/17","213.42.128.0/18","62.149.0.0/17",
            "212.118.0.0/16","212.119.0.0/17","195.229.0.0/17","195.229.128.0/18","217.33.0.0/18",
            "109.200.192.0/18","217.25.128.0/18","62.200.0.0/17","62.200.128.0/18",
            "82.178.0.0/16","82.179.0.0/17",
        ),
        "Fastly" to listOf(
            "151.101.0.0/22","151.101.64.0/22","151.101.128.0/22","151.101.192.0/22",
            "146.75.64.0/20","146.75.128.0/20","146.75.0.0/20","146.75.16.0/20","146.75.32.0/20",
            "146.75.192.0/20","199.232.64.0/22","199.232.128.0/22",
            "23.235.32.0/22","43.249.72.0/22","103.244.50.0/24","103.245.222.0/23",
            "104.156.80.0/20","167.82.64.0/18",
        ),
        "Cloudflare" to listOf(
            "173.245.48.0/20","103.21.244.0/22","103.22.200.0/22","103.31.4.0/22",
            "141.101.64.0/18","108.162.192.0/18","190.93.240.0/20","188.114.96.0/20",
            "197.234.240.0/22","198.41.128.0/17","162.158.0.0/15","104.16.0.0/13",
            "104.24.0.0/14","172.64.0.0/13","131.0.72.0/22",
        ),
        "Google" to listOf(
            "8.8.4.0/24","8.8.8.0/24","8.34.208.0/20","8.35.192.0/20",
            "23.236.48.0/20","23.251.128.0/19","34.0.0.0/15","34.2.0.0/16",
            "34.64.0.0/10","34.128.0.0/10","35.184.0.0/13","35.192.0.0/12",
            "35.208.0.0/12","35.224.0.0/12","35.240.0.0/13",
            "64.233.160.0/19","66.102.0.0/20","66.249.64.0/19",
            "72.14.192.0/18","74.125.0.0/16","108.177.8.0/21",
            "172.217.0.0/16","172.253.0.0/16","173.194.0.0/16",
            "209.85.128.0/17","216.58.192.0/19","216.239.32.0/19",
        ),
        "CloudFront" to listOf(
            "13.32.0.0/15","13.35.0.0/16","13.224.0.0/14","13.249.0.0/16",
            "54.182.0.0/16","54.192.0.0/16","54.230.0.0/16","54.239.128.0/18",
            "54.240.128.0/18","52.46.0.0/18","52.84.0.0/15","52.222.128.0/17",
            "64.252.64.0/18","64.252.128.0/18","70.132.0.0/18","71.152.0.0/17",
            "99.86.0.0/16","130.176.0.0/17","143.204.0.0/16","204.246.164.0/22",
            "204.246.168.0/22","204.246.174.0/23","204.246.176.0/20",
            "205.251.192.0/19","205.251.249.0/24","205.251.250.0/23",
            "205.251.252.0/23","205.251.254.0/24","216.137.32.0/19",
        ),
        "Gcore" to listOf(
            "92.223.64.0/18","92.223.0.0/19","5.188.24.0/22","5.188.106.0/23",
            "5.188.108.0/22","185.22.152.0/22","185.254.196.0/22","185.254.200.0/22",
            "199.34.28.0/22","193.178.88.0/21","212.92.128.0/17","45.9.152.0/22",
            "46.8.48.0/20","46.8.80.0/20","46.8.64.0/20","77.83.240.0/22",
            "87.246.0.0/20","89.185.224.0/20","91.213.48.0/22","91.213.56.0/22",
        ),
    )

    /** Fronting SNI/Host pairs per CDN */
    val frontingPairs: Map<String, List<Pair<String, String>>> = mapOf(
        "Akamai" to listOf(
            "a248.e.akamai.net" to "a.akamaihd.net",
            "a248.e.akamai.net" to "a.akamaiedge.net",
            "a.akamaihd.net" to "a248.e.akamai.net",
            "a248.e.akamai.net" to "psiphon3.com",
            "a.akamaihd.net" to "psiphon3.com",
        ),
        "Fastly" to listOf(
            "prod.global.ssl.fastly.net" to "global.ssl.fastly.net",
            "global.ssl.fastly.net" to "prod.global.ssl.fastly.net",
            "a.ssl.fastly.net" to "b.sni.global.fastly.net",
            "prod.global.ssl.fastly.net" to "psiphon3.com",
            "b.sni.global.fastly.net" to "prod.global.ssl.fastly.net",
        ),
        "Cloudflare" to listOf(
            "cloudflare.com" to "cloudflare.com",
            "www.cloudflare.com" to "www.cloudflare.com",
            "one.one.one.one" to "one.one.one.one",
            "cloudflare.com" to "ajax.cloudflare.com",
        ),
        "Google" to listOf(
            "www.google.com" to "www.google.com",
            "www.googleapis.com" to "www.googleapis.com",
            "accounts.google.com" to "accounts.google.com",
            "www.google.com" to "www.googleapis.com",
        ),
        "CloudFront" to listOf(
            "cloudfront.net" to "cloudfront.net",
            "d111111abcdef8.cloudfront.net" to "d111111abcdef8.cloudfront.net",
            "d111111abcdef8.cloudfront.net" to "psiphon3.com",
        ),
        "Gcore" to listOf(
            "gcore.com" to "gcore.com",
            "api.gcore.com" to "api.gcore.com",
        ),
        "Unknown" to listOf(
            "a248.e.akamai.net" to "a.akamaihd.net",
            "prod.global.ssl.fastly.net" to "global.ssl.fastly.net",
            "www.google.com" to "www.googleapis.com",
            "cloudfront.net" to "cloudfront.net",
        ),
    )

    val frontingOkCodes = setOf(200, 204, 206, 301, 302, 303, 307, 308, 400, 403, 404, 405, 406, 426)
    val frontingFailCodes = setOf(421, 502, 503, 504)

    /** Iran domains to filter out */
    val iranDomains = listOf(".ir", "persiangig", "iranserver", "pars", "ictco", "arvancloud")

    /** Detect which CDN an IP belongs to */
    fun detectCdn(ip: String): String {
        val ipLong = ipToLong(ip) ?: return "Unknown"
        for ((cdn, cidrs) in ranges) {
            for (cidr in cidrs) {
                if (isIpInCidr(ipLong, cidr)) return cdn
            }
        }
        return "Unknown"
    }

    fun ipToLong(ip: String): Long? {
        val parts = ip.split(".")
        if (parts.size != 4) return null
        return try {
            parts.fold(0L) { acc, part -> (acc shl 8) or part.toLong() }
        } catch (e: Exception) { null }
    }

    fun isIpInCidr(ipLong: Long, cidr: String): Boolean {
        val parts = cidr.split("/")
        if (parts.size != 2) return false
        val netIp = ipToLong(parts[0]) ?: return false
        val prefix = parts[1].toIntOrNull() ?: return false
        val mask = if (prefix == 0) 0L else ((0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL)
        return (ipLong and mask) == (netIp and mask)
    }

    fun longToIp(long: Long): String {
        return "${(long shr 24) and 0xFF}.${(long shr 16) and 0xFF}.${(long shr 8) and 0xFF}.${long and 0xFF}"
    }
}
