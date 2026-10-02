package com.cdnhunter.app.core.json

import com.cdnhunter.app.core.ConfigValidator
import com.cdnhunter.app.core.ConnectionError
import com.cdnhunter.app.core.ErrorCode
import com.cdnhunter.app.core.InternalConnectionConfig
import com.cdnhunter.app.core.ValidationResult
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

enum class JsonSchema { XRAY, SING_BOX, CLASH, UNKNOWN }

/** One usable server found in a JSON config. [config] has already passed [ConfigValidator]. */
data class ParsedProxy(val name: String, val config: InternalConnectionConfig)

/** An entry that was left out, and why. Shown to the user so a half-imported config is not a mystery. */
data class SkippedEntry(val tag: String, val reason: String)

sealed class JsonParseResult {
    data class Success(
        val schema: JsonSchema,
        val proxies: List<ParsedProxy>,
        val skipped: List<SkippedEntry>,
    ) : JsonParseResult()

    data class Failure(val error: ConnectionError.ConfigError) : JsonParseResult()
}

/**
 * Raw JSON -> validated [InternalConnectionConfig]s.
 *
 *   text -> size/depth/syntax checks -> schema detection -> per-schema adapter
 *        -> internal config -> [ConfigValidator] -> [ParsedProxy]
 *
 * Three schemas are recognised, because those are the JSON configs people actually hold:
 * Xray/V2Ray (`outbounds[].protocol`), sing-box (`outbounds[].type`) and Clash
 * (`proxies[]`). A root that is itself a single outbound/proxy, or an array of them, is
 * accepted too. Anything else is refused with [ErrorCode.INVALID_CONFIG] rather than guessed at.
 *
 * Never throws. Input is untrusted: it is size-capped, depth-capped before the tokenizer
 * sees it (a deeply nested document would otherwise overflow the stack), and every field
 * the adapters copy is whitelisted and type-coerced.
 */
object JsonConfigParser {

    const val MAX_BYTES = 512 * 1024
    const val MAX_DEPTH = 48
    const val MAX_ENTRIES = 500

    /** Cheap check for "this text is meant to be JSON", for callers deciding which parser to use. */
    fun looksLikeJson(text: String): Boolean {
        val t = text.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        return t.startsWith("{") || t.startsWith("[")
    }

    fun parse(raw: String): JsonParseResult {
        val text = raw.trim().removePrefix("\uFEFF").trim()
        if (text.isEmpty()) return failure(ErrorCode.INVALID_JSON, "empty input")
        if (text.length > MAX_BYTES || text.toByteArray(Charsets.UTF_8).size > MAX_BYTES) {
            return failure(ErrorCode.INVALID_JSON, "JSON is larger than ${MAX_BYTES / 1024} KB")
        }
        if (!looksLikeJson(text)) return failure(ErrorCode.INVALID_JSON, "not a JSON object or array")
        nestingTooDeep(text)?.let { return failure(ErrorCode.INVALID_JSON, it) }

        val root: Any = try {
            val tokener = JSONTokener(text)
            val value = tokener.nextValue()
            if (tokener.nextClean().code != 0) return failure(ErrorCode.INVALID_JSON, "unexpected data after the JSON document")
            value
        } catch (e: Exception) {
            // The message can quote the offending text; Redactor runs in ConnectionError.
            return failure(ErrorCode.INVALID_JSON, "syntax error: ${e.message?.take(120)}")
        }

        val schema = detect(root)
        val entries: List<Entry> = when (root) {
            is JSONObject -> entriesOf(root, schema).map { Entry(it, root.str("remarks")) }
            is JSONArray -> root.objects().flatMap { el ->
                // An array is either a list of bare outbounds/proxies, or a list of COMPLETE configs
                // (what subscription servers send: one Xray config per server, each with its own
                // inbounds/outbounds/remarks). Complete configs are unpacked to their outbounds.
                if (isWholeConfig(el)) entriesOf(el, detectObject(el)).map { Entry(it, el.str("remarks")) }
                else listOf(Entry(el, null))
            }
            else -> emptyList()
        }
        if (schema == JsonSchema.UNKNOWN) {
            return failure(ErrorCode.INVALID_CONFIG, "unrecognised JSON: expected an Xray/V2Ray, sing-box or Clash config")
        }
        if (entries.isEmpty()) return failure(ErrorCode.INVALID_CONFIG, "no outbounds or proxies found in the config")
        if (entries.size > MAX_ENTRIES) return failure(ErrorCode.INVALID_CONFIG, "more than $MAX_ENTRIES entries in the config")

        val proxies = ArrayList<ParsedProxy>()
        val skipped = ArrayList<SkippedEntry>()
        var firstError: ConnectionError.ConfigError? = null

        for ((entry, remark) in entries) {
            val adapted = try {
                when (schema) {
                    JsonSchema.XRAY -> XrayAdapter.adapt(entry)
                    JsonSchema.SING_BOX -> SingBoxAdapter.adapt(entry)
                    JsonSchema.CLASH -> ClashAdapter.adapt(entry)
                    JsonSchema.UNKNOWN -> continue
                }
            } catch (e: Exception) {
                rejected("?", ErrorCode.INVALID_CONFIG, "could not read entry (${e.javaClass.simpleName})")
            }
            when (adapted) {
                is Adapted.NotAProxy -> skipped += SkippedEntry(adapted.tag, adapted.reason)
                is Adapted.Rejected -> {
                    if (firstError == null) firstError = adapted.error
                    skipped += SkippedEntry(adapted.tag, "${adapted.error.code}: ${adapted.error.technical}")
                }
                is Adapted.Proxy -> {
                    // The remark is the name the server's owner gave this config ("🇩🇪 Germany 4");
                    // the outbound's own tag is usually just "proxy".
                    val displayName = remark ?: adapted.name
                    val internal = InternalConnectionConfig.fromProxyMap(adapted.map, displayName)
                    if (internal == null) {
                        skipped += SkippedEntry(adapted.name, "${ErrorCode.INVALID_CONFIG}: missing server or port")
                        if (firstError == null) firstError = ConnectionError.ConfigError(ErrorCode.INVALID_CONFIG, "missing server or port")
                    } else when (val v = ConfigValidator.validate(internal)) {
                        is ValidationResult.Valid -> proxies += ParsedProxy(displayName, v.config.config)
                        is ValidationResult.Invalid -> {
                            if (firstError == null) firstError = v.error
                            skipped += SkippedEntry(displayName, "${v.error.code}: ${v.error.technical}")
                        }
                    }
                }
            }
        }

        if (proxies.isEmpty()) {
            return JsonParseResult.Failure(
                firstError ?: ConnectionError.ConfigError(ErrorCode.INVALID_CONFIG, "the config contains no proxy server")
            )
        }
        return JsonParseResult.Success(schema, proxies, skipped)
    }

    /** Identifies the schema from structure alone. */
    fun detect(root: Any): JsonSchema = when (root) {
        is JSONObject -> detectObject(root)
        is JSONArray -> root.objects().firstNotNullOfOrNull { el ->
            (if (isWholeConfig(el)) detectObject(el) else detectEntry(el)).takeIf { it != JsonSchema.UNKNOWN }
        } ?: JsonSchema.UNKNOWN
        else -> JsonSchema.UNKNOWN
    }

    /** A complete client config (as opposed to a single outbound/proxy): it holds a list of them. */
    private fun isWholeConfig(o: JSONObject) =
        o.arr("outbounds") != null || o.obj("outbound") != null || o.arr("proxies") != null

    private class Entry(val obj: JSONObject, val remark: String?) {
        operator fun component1() = obj
        operator fun component2() = remark
    }

    internal fun detectObject(o: JSONObject): JsonSchema {
        val outbounds = o.arr("outbounds")?.objects() ?: o.obj("outbound")?.let { listOf(it) }
        if (outbounds != null) return outbounds.firstNotNullOfOrNull { detectEntry(it).takeIf { s -> s != JsonSchema.UNKNOWN } } ?: JsonSchema.UNKNOWN
        if (o.arr("proxies") != null) return JsonSchema.CLASH
        return detectEntry(o)
    }

    /** One entry on its own: Xray has `protocol`, sing-box has `type` + `server_port`, Clash has `type` + `port`. */
    private fun detectEntry(e: JSONObject): JsonSchema = when {
        e.has("protocol") -> JsonSchema.XRAY
        e.has("type") && e.has("server_port") -> JsonSchema.SING_BOX
        e.has("type") && e.has("port") -> JsonSchema.CLASH
        // A sing-box plumbing outbound (selector, direct) has a type and no server at all.
        e.has("type") && e.has("tag") -> JsonSchema.SING_BOX
        else -> JsonSchema.UNKNOWN
    }

    private fun entriesOf(root: JSONObject, schema: JsonSchema): List<JSONObject> = when {
        root.arr("outbounds") != null -> root.arr("outbounds")!!.objects()
        root.obj("outbound") != null -> listOf(root.obj("outbound")!!)
        root.arr("proxies") != null -> root.arr("proxies")!!.objects()
        schema != JsonSchema.UNKNOWN -> listOf(root)
        else -> emptyList()
    }

    /** Null if the document nests no deeper than [MAX_DEPTH], else a reason. Ignores brackets inside strings. */
    private fun nestingTooDeep(text: String): String? {
        var depth = 0
        var inString = false
        var escaped = false
        for (c in text) {
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{', '[' -> {
                    depth++
                    if (depth > MAX_DEPTH) return "JSON is nested deeper than $MAX_DEPTH levels"
                }
                '}', ']' -> if (depth > 0) depth--
            }
        }
        return null
    }

    private fun failure(code: ErrorCode, technical: String) =
        JsonParseResult.Failure(ConnectionError.ConfigError(code, technical))
}
