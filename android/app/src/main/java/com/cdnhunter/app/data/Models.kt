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

// ── Scan Profiles ───────────────────────────────────────────────────────────
enum class ScanProfile(val label: String, val desc: String, val config: ScanConfig) {
    QUICK("Quick Scan", "100 IPs, fast", ScanConfig(cdnProvider = CdnProvider.SMART, maxIps = 100, concurrency = 80, timeout = 3f, retries = 1)),
    NORMAL("Normal", "1000 IPs, balanced", ScanConfig(cdnProvider = CdnProvider.SMART, maxIps = 1000, concurrency = 60, timeout = 4f)),
    DEEP("Deep Scan", "5000 IPs, thorough", ScanConfig(cdnProvider = CdnProvider.ALL, maxIps = 5000, concurrency = 100, timeout = 5f)),
    CF_ONLY("Cloudflare Only", "Cloudflare ranges", ScanConfig(cdnProvider = CdnProvider.CLOUDFLARE, maxIps = 2000, concurrency = 80)),
    AKAMAI_ONLY("Akamai Only", "Akamai ranges", ScanConfig(cdnProvider = CdnProvider.AKAMAI, maxIps = 2000, concurrency = 80)),
    FASTLY_ONLY("Fastly Only", "Fastly ranges", ScanConfig(cdnProvider = CdnProvider.FASTLY, maxIps = 1500, concurrency = 60)),
}

// ── Config Generator ────────────────────────────────────────────────────────
enum class ProxyType(val label: String) {
    VLESS("VLESS (v2ray)"),
    VMESS("VMess (v2ray)"),
    TROJAN("Trojan"),
    SINGBOX("Sing-Box"),
    CLASH("Clash Meta"),
}

data class ProxyConfig(
    val type: ProxyType = ProxyType.VLESS,
    val ip: String = "",
    val port: Int = 443,
    val sni: String = "",
    val host: String = "",
    val uuid: String = "auto",
    val path: String = "/",
    val security: String = "tls",
)

// ── Navigation ──────────────────────────────────────────────────────────────
enum class NavScreen(val label: String, val icon: String) {
    SCAN("Scan", "radar"),
    CONFIG_GEN("Config Generator", "code"),
    EXPORT("Export & Share", "share"),
    PROFILES("Scan Profiles", "speed"),
    LOG("Activity Log", "log"),
    SETTINGS("Settings", "settings"),
}

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
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val city: String = "",
    val isp: String = "",
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
