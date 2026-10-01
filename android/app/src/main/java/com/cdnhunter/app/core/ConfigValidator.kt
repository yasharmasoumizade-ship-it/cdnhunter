package com.cdnhunter.app.core

/**
 * Checks an [InternalConnectionConfig] before it is allowed anywhere near the core.
 *
 * The rules are the ones whose violation otherwise shows up as a silent failure deep
 * inside the core (a connection that "starts" and never passes traffic) or as a
 * malformed core config. Each failure names the field and carries the matching
 * [ErrorCode], so the UI can say what is wrong rather than "invalid config".
 *
 * Deliberately NOT checked: anything the core is the authority on (cipher names,
 * fingerprints, ALPN values). A wrong guess here would reject a config that works.
 */
object ConfigValidator {

    private val SUPPORTED_NETWORKS = setOf("tcp", "ws", "grpc", "h2", "xhttp")
    private val HEX = Regex("^[0-9a-fA-F]*$")
    private val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
    private val HOST_LABEL = Regex("""^[\p{L}\p{N}_]([\p{L}\p{N}_-]{0,61}[\p{L}\p{N}_])?$""")

    fun validate(config: InternalConnectionConfig): ValidationResult {
        val p = config.proxy

        controlCharViolation(p)?.let {
            return invalid(ErrorCode.INVALID_CONFIG, "control character in field '$it'", it)
        }

        validateHost(config.server)?.let { return invalid(ErrorCode.INVALID_SERVER, it, "server") }

        if (config.port !in 1..65535) {
            return invalid(ErrorCode.INVALID_PORT, "port ${config.port} is outside 1..65535", "port")
        }

        when (config.protocol) {
            ProxyProtocol.VLESS, ProxyProtocol.VMESS -> {
                val id = p["uuid"]?.toString().orEmpty()
                if (id.isBlank()) return invalid(ErrorCode.INVALID_CREDENTIALS, "missing user id", "uuid")
                if (id.length > 64) return invalid(ErrorCode.INVALID_CREDENTIALS, "user id is too long", "uuid")
            }
            ProxyProtocol.TROJAN -> {
                if (p["password"]?.toString().isNullOrBlank()) {
                    return invalid(ErrorCode.INVALID_CREDENTIALS, "missing password", "password")
                }
            }
            ProxyProtocol.SHADOWSOCKS -> {
                if (p["password"]?.toString().isNullOrBlank()) {
                    return invalid(ErrorCode.INVALID_CREDENTIALS, "missing password", "password")
                }
                if (p["cipher"]?.toString().isNullOrBlank()) {
                    return invalid(ErrorCode.INVALID_CREDENTIALS, "missing cipher/method", "cipher")
                }
            }
        }

        val network = (p["network"]?.toString() ?: "tcp").lowercase()
        if (network !in SUPPORTED_NETWORKS) {
            return invalid(ErrorCode.UNSUPPORTED_PROTOCOL, "transport '$network' is not supported", "network")
        }

        val tls = p["tls"] == true
        val realityOpts = p["reality-opts"] as? Map<*, *>
        if (realityOpts != null) {
            if (realityOpts["public-key"]?.toString().isNullOrBlank()) {
                return invalid(ErrorCode.INVALID_CONFIG, "REALITY is enabled but the public key is missing", "reality-opts.public-key")
            }
            val sid = realityOpts["short-id"]?.toString().orEmpty()
            if (sid.length > 16 || !HEX.matches(sid)) {
                return invalid(ErrorCode.INVALID_CONFIG, "REALITY short id must be up to 16 hex characters", "reality-opts.short-id")
            }
            if (p["servername"]?.toString().isNullOrBlank()) {
                return invalid(ErrorCode.INVALID_CONFIG, "REALITY is enabled but the server name (SNI) is missing", "servername")
            }
        }

        val flow = p["flow"]?.toString().orEmpty()
        if (flow.isNotBlank()) {
            if (config.protocol != ProxyProtocol.VLESS) {
                return invalid(ErrorCode.INVALID_CONFIG, "flow is only valid for vless", "flow")
            }
            if (!flow.startsWith("xtls-rprx-vision")) {
                return invalid(ErrorCode.UNSUPPORTED_PROTOCOL, "flow '$flow' is not supported", "flow")
            }
            if (!tls) {
                return invalid(ErrorCode.INVALID_CONFIG, "flow requires TLS or REALITY", "flow")
            }
        }

        return ValidationResult.Valid(ValidatedConfig.of(config))
    }

    /** Null if [host] is a usable server address, else a human-readable reason. */
    fun validateHost(host: String): String? {
        val h = host.trim()
        if (h.isEmpty()) return "server address is empty"
        if (h.length > 255) return "server address is too long"
        if (h.any { it.isWhitespace() || it.code < 0x20 || it == '/' || it == '\\' || it == '@' || it == '?' || it == '#' }) {
            return "server address contains illegal characters"
        }
        if (h.startsWith("[")) {
            if (!h.endsWith("]")) return "malformed IPv6 address"
            val inner = h.substring(1, h.length - 1)
            if (!inner.contains(':') || inner.any { !(it.isLetterOrDigit() || it == ':' || it == '.' || it == '%') }) {
                return "malformed IPv6 address"
            }
            return null
        }
        // Anything that looks like a dotted quad is an IPv4 literal and must be a valid one —
        // "999.1.1.1" would otherwise slip through as a hostname made of numeric labels.
        if (h.all { it.isDigit() || it == '.' }) {
            if (!IPV4.matches(h)) return "malformed IPv4 address"
            val octets = h.split('.').map { it.toInt() }
            if (octets.any { it > 255 }) return "malformed IPv4 address"
            if (octets.all { it == 0 } || octets.all { it == 255 }) return "reserved IPv4 address"
            return null
        }
        if (h.contains(':')) return "unexpected ':' in server address (IPv6 literals must be in [brackets])"
        val labels = h.trimEnd('.').split('.')
        if (labels.any { !HOST_LABEL.matches(it) }) return "server address is not a valid hostname"
        return null
    }

    private fun invalid(code: ErrorCode, technical: String, field: String): ValidationResult.Invalid =
        ValidationResult.Invalid(ConnectionError.ConfigError(code, technical, field))

    /** Name of the first string value (or key) containing a control character, searching nested maps and lists. */
    private fun controlCharViolation(value: Any?, path: String = ""): String? {
        return when (value) {
            is String -> if (value.any { it.code < 0x20 || it.code == 0x7f }) path.ifEmpty { "value" } else null
            is Map<*, *> -> {
                for ((k, v) in value) {
                    val key = k.toString()
                    val sub = if (path.isEmpty()) key else "$path.$key"
                    if (key.any { it.code < 0x20 || it.code == 0x7f }) return sub
                    controlCharViolation(v, sub)?.let { return it }
                }
                null
            }
            is List<*> -> {
                for ((i, v) in value.withIndex()) controlCharViolation(v, "$path[$i]")?.let { return it }
                null
            }
            else -> null
        }
    }
}
