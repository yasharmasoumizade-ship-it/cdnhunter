package com.cdnhunter.app.core

/**
 * Pulls the lines that explain a failure out of the core's log.
 *
 * The core is run at debug level, and its log is a small ring buffer: during a failing connect it
 * fills with hundreds of `[DNS] hijack …` and `cache hit` lines within seconds, pushing the one line
 * that names the cause (`dial … error: …`, a handshake failure) out of the "last N lines" a plain
 * tail would show. This keeps only lines that look like failures, drops the known noise, and
 * collapses repeats that differ only in ports/ids.
 */
object CoreLogFilter {

    private val FAILURE = Regex(
        """(?i)\b(error|fail(ed|ure)?|refused|reset|timed? ?out|timeout|deadline|invalid|invaild|eof|unreachable|denied|mismatch|handshake|rejected|panic)\b"""
    )
    private val NOISE = Regex("""(?i)(\[DNS\] hijack|cache hit|\[Rule\] use default|using HTTP/2 for this upstream|creating a new http client|re-creating the http client)""")
    private val VARIABLE = Regex("""\d+""")

    /** Up to [max] distinct failure-looking lines, oldest first, each trimmed to [maxLineLength]. */
    fun failures(log: String, max: Int = 8, maxLineLength: Int = 220): List<String> {
        val seen = LinkedHashMap<String, String>()
        for (raw in log.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || NOISE.containsMatchIn(line) || !FAILURE.containsMatchIn(line)) continue
            val key = VARIABLE.replace(line, "#")
            seen.remove(key)              // keep the most recent occurrence's position
            seen[key] = line.take(maxLineLength)
        }
        return seen.values.toList().takeLast(max)
    }

    fun summary(log: String, max: Int = 5): String = failures(log, max).joinToString(" | ")
}
