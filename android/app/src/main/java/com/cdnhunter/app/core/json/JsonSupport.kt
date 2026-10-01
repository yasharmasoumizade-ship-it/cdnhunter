package com.cdnhunter.app.core.json

import com.cdnhunter.app.core.ConnectionError
import com.cdnhunter.app.core.ErrorCode
import org.json.JSONArray
import org.json.JSONObject

/** What one outbound/proxy entry of a JSON config turned into. */
internal sealed class Adapted {
    /** A proxy in the core's flat shape, ready for validation. */
    class Proxy(val name: String, val map: LinkedHashMap<String, Any>) : Adapted()

    /** Not a proxy at all (direct, block, selector, DNS, ...). Expected in real configs; not an error. */
    class NotAProxy(val tag: String, val reason: String) : Adapted()

    /** A proxy this app cannot run, or one that is missing something it needs. */
    class Rejected(val tag: String, val error: ConnectionError.ConfigError) : Adapted()
}

/** Lenient, null-safe accessors. JSON configs in the wild write numbers as strings and booleans as "true". */
internal fun JSONObject.str(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val v = opt(key) ?: return null
    if (v is JSONObject || v is JSONArray) return null
    return v.toString().trim().takeIf { it.isNotEmpty() }
}

internal fun JSONObject.int(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    return when (val v = opt(key)) {
        is Number -> v.toInt()
        is String -> v.trim().toIntOrNull()
        else -> null
    }
}

internal fun JSONObject.bool(key: String): Boolean? {
    if (!has(key) || isNull(key)) return null
    return when (val v = opt(key)) {
        is Boolean -> v
        is String -> when (v.trim().lowercase()) {
            "true", "1" -> true
            "false", "0" -> false
            else -> null
        }
        is Number -> v.toInt() != 0
        else -> null
    }
}

internal fun JSONObject.obj(key: String): JSONObject? = if (has(key)) optJSONObject(key) else null

internal fun JSONObject.arr(key: String): JSONArray? = if (has(key)) optJSONArray(key) else null

/** A string list from either a JSON array or a single comma-separated string. */
internal fun JSONObject.strList(key: String): List<String> {
    if (!has(key) || isNull(key)) return emptyList()
    return when (val v = opt(key)) {
        is JSONArray -> (0 until v.length()).mapNotNull { v.opt(it)?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }
        is String -> v.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        else -> emptyList()
    }
}

internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

internal fun rejected(tag: String, code: ErrorCode, technical: String, field: String? = null) =
    Adapted.Rejected(tag, ConnectionError.ConfigError(code, technical, field))

/** Protocols that are routing/plumbing rather than proxies, across Xray, sing-box and Clash. */
internal val NON_PROXY_TYPES = setOf(
    "freedom", "direct", "blackhole", "block", "dns", "dns-out", "loopback",
    "selector", "urltest", "fallback", "load-balance", "relay", "reject", "compatible", "pass",
)
