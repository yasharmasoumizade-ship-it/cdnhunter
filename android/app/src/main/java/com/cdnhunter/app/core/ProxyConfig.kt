package com.cdnhunter.app.core

enum class ProxyProtocol(val wire: String) {
    VLESS("vless"),
    VMESS("vmess"),
    TROJAN("trojan"),
    SHADOWSOCKS("ss");

    companion object {
        fun fromWire(s: String?): ProxyProtocol? = values().firstOrNull { it.wire.equals(s?.trim(), ignoreCase = true) }
    }
}

/**
 * One proxy server in the app's own, protocol-neutral form: what every input format
 * (share URI, Xray JSON, sing-box JSON, Clash JSON, a stored config) is converted into
 * before anything else happens to it.
 *
 * [proxy] is the entry as the core expects it under `proxies:` — a flat map with the
 * server, credentials, TLS/REALITY and transport options already mapped. Keeping that
 * shape (rather than a second, parallel field-by-field model) means there is exactly
 * one place each option is translated, and the core builder consumes it unchanged.
 *
 * [toString] never prints the map: it holds the credentials.
 */
class InternalConnectionConfig(
    val protocol: ProxyProtocol,
    val name: String,
    val server: String,
    val port: Int,
    val proxy: LinkedHashMap<String, Any>,
) {
    /** UUID for vless/vmess, password for trojan/shadowsocks. Never log this. */
    val credential: String
        get() = (proxy["uuid"] ?: proxy["password"])?.toString().orEmpty()

    override fun toString(): String = "InternalConnectionConfig(${protocol.wire}, $server:$port)"

    companion object {
        /** Builds the typed view from a core-shaped proxy map; null if the map has no usable protocol/server/port at all. */
        fun fromProxyMap(map: LinkedHashMap<String, Any>, name: String? = null): InternalConnectionConfig? {
            val protocol = ProxyProtocol.fromWire(map["type"]?.toString()) ?: return null
            val server = map["server"]?.toString() ?: return null
            val port = when (val p = map["port"]) {
                is Int -> p
                is Number -> p.toInt()
                is String -> p.trim().toIntOrNull() ?: return null
                else -> return null
            }
            return InternalConnectionConfig(protocol, name ?: "${protocol.wire} · $server", server, port, map)
        }
    }
}

/**
 * A config that has passed [ConfigValidator]. The constructor is private: the only way
 * to hold one is to have gone through validation, so code downstream (the core builder,
 * the service) can take the type as proof instead of re-checking.
 */
class ValidatedConfig private constructor(val config: InternalConnectionConfig) {
    override fun toString(): String = "ValidatedConfig($config)"

    companion object {
        internal fun of(config: InternalConnectionConfig) = ValidatedConfig(config)
    }
}

sealed class ValidationResult {
    data class Valid(val config: ValidatedConfig) : ValidationResult()
    data class Invalid(val error: ConnectionError.ConfigError) : ValidationResult()
}
