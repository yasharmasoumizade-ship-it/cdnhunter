package com.cdnhunter.app.core

/**
 * Strips credentials out of any text that is about to be logged, shown in the debug
 * dump, or attached to an error.
 *
 * The connection config is the secret: it carries the UUID or password, the REALITY
 * public key and short id, and the server address. The in-app debug dump is copied and
 * pasted into support chats, so redaction has to happen at the moment text enters
 * [ConnLog] / [ConnectionError] — not at the screen that displays it.
 *
 * Deliberately over-eager: a long opaque token that is not a secret gets masked too,
 * which costs nothing, while a secret that slips through costs a server.
 */
object Redactor {

    private const val MASK = "<redacted>"

    private val PROXY_URI = Regex("""(?i)\b(vless|vmess|trojan|ss|cdnjson|socks5?)://[^\s"'<>]+""")
    private val UUID = Regex("""\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b""")
    private val KEY_VALUE = Regex(
        """(?i)\b(password|passwd|pwd|secret|token|uuid|private[-_ ]?key|public[-_ ]?key|pbk|sid|short[-_ ]?id|auth(?:orization)?|cookie)\b(\s*["']?\s*[:=]\s*["']?)([^\s"',;}\]]+)"""
    )
    private val LONG_TOKEN = Regex("""\b[A-Za-z0-9+/_-]{32,}={0,2}""")

    fun redact(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        var s: String = text
        s = PROXY_URI.replace(s) { m -> m.groupValues[1].lowercase() + "://" + MASK }
        s = UUID.replace(s, MASK)
        s = KEY_VALUE.replace(s) { m -> m.groupValues[1] + m.groupValues[2] + MASK }
        s = LONG_TOKEN.replace(s, MASK)
        return s
    }
}
