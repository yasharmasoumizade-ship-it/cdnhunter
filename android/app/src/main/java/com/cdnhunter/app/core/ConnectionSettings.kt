package com.cdnhunter.app.core

/**
 * The network settings a connection is built from, snapshotted once at the start of an
 * attempt. Reading [com.cdnhunter.app.vpn.AppSettings] repeatedly while connecting meant
 * a change made mid-attempt could make the tunnel and the core disagree about MTU or
 * IPv6; one snapshot per attempt cannot.
 */
data class ConnectionSettings(
    val mtu: Int,
    val ipv6: Boolean,
    val allowLan: Boolean,
    val useDoh: Boolean,
    val customDnsEnabled: Boolean,
    val customDnsServers: List<String>,
    val splitTunnelMode: String,
    val adBlocker: Boolean,
    val blockAds: Boolean,
    val blockTrackers: Boolean,
    val blockMalware: Boolean,
)

object SettingsValidator {

    const val MIN_MTU = 576
    const val MAX_MTU = 9000

    private val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    fun validate(s: ConnectionSettings): ConnectionError.ConfigError? {
        if (s.mtu !in MIN_MTU..MAX_MTU) {
            return ConnectionError.ConfigError(ErrorCode.INVALID_CONFIG, "MTU ${s.mtu} is outside $MIN_MTU..$MAX_MTU", "mtu")
        }
        if (s.splitTunnelMode != "include" && s.splitTunnelMode != "exclude") {
            return ConnectionError.ConfigError(ErrorCode.INVALID_CONFIG, "unknown split-tunnel mode '${s.splitTunnelMode}'", "split_tunnel_mode")
        }
        if (s.customDnsEnabled) {
            val entries = s.customDnsServers.map { it.trim() }.filter { it.isNotEmpty() }
            for (e in entries) {
                if (!isValidDnsEntry(e)) {
                    return ConnectionError.ConfigError(ErrorCode.INVALID_CONFIG, "custom DNS entry is not valid: $e", "custom_dns")
                }
            }
        }
        return null
    }

    /** IP, IP:port, [v6]:port, https://host/path, tls://host, quic://host. */
    fun isValidDnsEntry(entry: String): Boolean {
        val e = entry.trim()
        if (e.isEmpty() || e.any { it.isWhitespace() || it.code < 0x20 }) return false
        for (scheme in listOf("https://", "tls://", "quic://", "h3://")) {
            if (e.startsWith(scheme)) {
                val rest = e.removePrefix(scheme).substringBefore('/')
                val host = if (rest.startsWith("[")) rest.substringBefore(']') + "]" else rest.substringBefore(':')
                return ConfigValidator.validateHost(host) == null
            }
        }
        if (e.startsWith("[")) {
            val inner = e.substringAfter('[').substringBefore(']')
            return inner.contains(':') && inner.all { it.isLetterOrDigit() || it == ':' || it == '.' }
        }
        if (e.count { it == ':' } > 1) {
            return e.all { it.isLetterOrDigit() || it == ':' || it == '.' }
        }
        val host = e.substringBefore(':')
        val port = if (e.contains(':')) e.substringAfter(':').toIntOrNull() else 53
        if (port == null || port !in 1..65535) return false
        if (!IPV4.matches(host)) return false
        return host.split('.').all { it.toInt() in 0..255 }
    }
}
