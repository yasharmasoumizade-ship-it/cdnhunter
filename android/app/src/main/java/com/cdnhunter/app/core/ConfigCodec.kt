package com.cdnhunter.app.core

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Serialises a parsed proxy into a single-line `cdnjson://` entry, and back.
 *
 * Saved servers are persisted one per line (`uri` \u0001 geo fields \u0001 ...), keyed by
 * the URI string. A JSON config is multi-line and can be many kilobytes, so it cannot
 * live in that slot as-is. Instead, each proxy parsed out of a JSON config is stored as
 * its already-normalised core-shaped map, minified and base64url-encoded:
 *
 *     cdnjson://<base64url of {"type":"vless","server":...}>#<url-encoded name>
 *
 * The result is a plain string with no newline and none of the separator characters, so
 * the rest of the app — list, persistence, ping, Smart mode — treats it like any other
 * link. The remark rides in the fragment exactly as it does for a `vless://` link.
 *
 * base64 here is a small self-contained implementation rather than `java.util.Base64`
 * (API 26; this app's minSdk is 24) or `android.util.Base64` (not available in plain JVM
 * unit tests), and it is lenient on input: standard or URL-safe alphabet, padding
 * optional, whitespace ignored.
 */
object ConfigCodec {

    const val SCHEME = "cdnjson"
    private const val MAX_STORED_BYTES = 64 * 1024
    private const val URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    fun encodeStored(config: InternalConnectionConfig): String {
        val json = mapToJson(config.proxy).toString()
        val payload = base64UrlEncode(json.toByteArray(Charsets.UTF_8))
        val fragment = URLEncoder.encode(config.name, "UTF-8")
        return "$SCHEME://$payload#$fragment"
    }

    /** The proxy map stored in a `cdnjson://` entry, or null if the entry is corrupt. Never throws. */
    fun decodeStored(uri: String): LinkedHashMap<String, Any>? {
        return try {
            val body = uri.trim().removePrefix("$SCHEME://").substringBefore('#')
            if (body.isEmpty() || body.length > MAX_STORED_BYTES * 2) return null
            val bytes = base64Decode(body) ?: return null
            if (bytes.size > MAX_STORED_BYTES) return null
            jsonToMap(JSONObject(String(bytes, Charsets.UTF_8)))
        } catch (_: Exception) {
            null
        }
    }

    /** The remark stored in the fragment of a `cdnjson://` entry, if any. */
    fun storedName(uri: String): String? {
        val frag = uri.substringAfter('#', "")
        if (frag.isEmpty()) return null
        return try {
            URLDecoder.decode(frag, "UTF-8").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    // ── JSON <-> map ───────────────────────────────────────────────────────────

    fun mapToJson(map: Map<*, *>): JSONObject {
        val obj = JSONObject()
        for ((k, v) in map) obj.put(k.toString(), toJsonValue(v))
        return obj
    }

    private fun toJsonValue(v: Any?): Any = when (v) {
        null -> JSONObject.NULL
        is Map<*, *> -> mapToJson(v)
        is Iterable<*> -> JSONArray().also { arr -> v.forEach { arr.put(toJsonValue(it)) } }
        is Boolean, is Int, is Long, is Double, is String -> v
        is Number -> v.toDouble()
        else -> v.toString()
    }

    fun jsonToMap(obj: JSONObject): LinkedHashMap<String, Any> {
        val out = LinkedHashMap<String, Any>()
        val names = obj.names() ?: return out
        for (i in 0 until names.length()) {
            val k = names.optString(i)
            fromJsonValue(obj.opt(k))?.let { out[k] = it }
        }
        return out
    }

    private fun fromJsonValue(v: Any?): Any? = when (v) {
        null, JSONObject.NULL -> null
        is JSONObject -> jsonToMap(v)
        is JSONArray -> (0 until v.length()).mapNotNull { fromJsonValue(v.opt(it)) }
        is Long -> if (v in Int.MIN_VALUE..Int.MAX_VALUE) v.toInt() else v
        is Double -> if (v == Math.rint(v) && Math.abs(v) < Int.MAX_VALUE) v.toInt() else v
        else -> v
    }

    // ── base64 ─────────────────────────────────────────────────────────────────

    fun base64UrlEncode(data: ByteArray): String {
        val sb = StringBuilder((data.size + 2) / 3 * 4)
        var i = 0
        while (i < data.size) {
            val b0 = data[i].toInt() and 0xff
            val b1 = if (i + 1 < data.size) data[i + 1].toInt() and 0xff else -1
            val b2 = if (i + 2 < data.size) data[i + 2].toInt() and 0xff else -1
            sb.append(URL_ALPHABET[b0 shr 2])
            sb.append(URL_ALPHABET[((b0 and 3) shl 4) or (if (b1 >= 0) b1 shr 4 else 0)])
            if (b1 >= 0) sb.append(URL_ALPHABET[((b1 and 15) shl 2) or (if (b2 >= 0) b2 shr 6 else 0)])
            if (b2 >= 0) sb.append(URL_ALPHABET[b2 and 63])
            i += 3
        }
        return sb.toString() // no padding: '=' is not URI-safe in an authority and is not needed to decode
    }

    /** Lenient decoder: std or URL-safe alphabet, optional padding, whitespace skipped. Null on any invalid character. */
    fun base64Decode(text: String): ByteArray? {
        val out = java.io.ByteArrayOutputStream(text.length * 3 / 4)
        var buffer = 0
        var bits = 0
        for (c in text) {
            if (c == '=' || c.isWhitespace()) continue
            val v = when (c) {
                in 'A'..'Z' -> c - 'A'
                in 'a'..'z' -> c - 'a' + 26
                in '0'..'9' -> c - '0' + 52
                '+', '-' -> 62
                '/', '_' -> 63
                else -> return null
            }
            buffer = (buffer shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xff)
            }
        }
        return out.toByteArray()
    }
}
