package com.cdnhunter.app.data

data class ScanConfig(
    val cdnProvider: CdnProvider = CdnProvider.SMART,
    val host: String = "",
    val sni: String = "",
    val concurrency: Int = 60,
    val timeout: Float = 4.0f,
    val maxIps: Int = 3000,
    val retries: Int = 2,
    val manualIps: String = "",
    val manualCidr: String = "",
)

enum class CdnProvider(val label: String, val key: String) {
    SMART("Smart (Auto)", "smart"),
    CLOUDFLARE("Cloudflare", "cloudflare"),
    AKAMAI("Akamai", "akamai"),
    FASTLY("Fastly", "fastly"),
    GOOGLE("Google", "google"),
    CLOUDFRONT("CloudFront", "cloudfront"),
    GCORE("Gcore", "gcore"),
    ALL("All CDNs", "all"),
    MANUAL("Manual IPs", "manual"),
    CIDR("Manual CIDR", "cidr"),
}

enum class ScanPhase(val label: String) {
    IDLE("Idle"),
    SCANNING("Scanning"),
    IP_CHECK("IP Check"),
    FRONTING("Fronting"),
    THROUGHPUT("Throughput"),
    DONE("Done"),
}

data class ScanResult(
    val ip: String,
    val ms: Int = 9999,
    val code: Int = 0,
    val ok: Boolean = false,
    val cdn: String = "?",
    val country: String = "",
    val frontingOk: Boolean = false,
    val frontingSni: String = "",
    val frontingHost: String = "",
    val kbps: Float = 0f,
)

data class ScanState(
    val running: Boolean = false,
    val phase: ScanPhase = ScanPhase.IDLE,
    val phaseDetail: String = "",
    val scanned: Int = 0,
    val healthy: Int = 0,
    val failed: Int = 0,
    val pct: Int = 0,
    val source: String = "-",
    val results: List<ScanResult> = emptyList(),
    val liveIps: List<ScanResult> = emptyList(),
    val logs: List<String> = emptyList(),
)
