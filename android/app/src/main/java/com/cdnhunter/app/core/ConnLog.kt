package com.cdnhunter.app.core

import java.util.ArrayDeque

/** Stage names are the ones the connection flow is documented in; keep them stable, they are what support searches for. */
object Stage {
    const val CONFIG_LOADED = "Config loaded"
    const val CONFIG_VALIDATED = "Config validated"
    const val CORE_STARTED = "Core started"
    const val TUNNEL_STARTING = "Tunnel starting"
    const val TUNNEL_READY = "Tunnel ready"
    const val CONNECTIVITY_CHECK = "Connectivity check"
    const val CONNECTED = "Connected"
    const val DISCONNECT_REQUESTED = "Disconnect requested"
    const val CORE_STOPPED = "Core stopped"
    const val DISCONNECTED = "Disconnected"
    const val RECONNECT = "Reconnect"
    const val ERROR = "Error"
    const val NETWORK = "Network"
}

/**
 * Structured, bounded, always-redacted connection log.
 *
 * Every line carries the connection id of the attempt that produced it, so one
 * connect/disconnect cycle can be read in isolation out of a log that spans several.
 * Text goes through [Redactor] on the way in — nothing that reaches the buffer, the
 * sink (logcat) or [dump] can contain a credential.
 */
object ConnLog {

    enum class Level { DEBUG, INFO, WARN, ERROR }

    data class Entry(
        val timeMs: Long,
        val level: Level,
        val connectionId: String,
        val stage: String,
        val message: String,
    )

    private const val MAX_ENTRIES = 400
    private val buffer = ArrayDeque<Entry>()
    private val lock = Any()

    /** Set once from Android code to mirror lines into logcat; stays null in JVM unit tests. */
    @Volatile
    var sink: ((Level, String) -> Unit)? = null

    @Volatile
    var clock: () -> Long = { System.currentTimeMillis() }

    fun log(level: Level, connectionId: String?, stage: String, message: String) {
        val entry = Entry(clock(), level, connectionId ?: "-", stage, Redactor.redact(message))
        synchronized(lock) {
            buffer.addLast(entry)
            while (buffer.size > MAX_ENTRIES) buffer.removeFirst()
        }
        try {
            sink?.invoke(level, format(entry))
        } catch (_: Exception) {
            // A logging sink must never be able to break a connection.
        }
    }

    fun d(cid: String?, stage: String, msg: String) = log(Level.DEBUG, cid, stage, msg)
    fun i(cid: String?, stage: String, msg: String) = log(Level.INFO, cid, stage, msg)
    fun w(cid: String?, stage: String, msg: String) = log(Level.WARN, cid, stage, msg)
    fun e(cid: String?, stage: String, msg: String) = log(Level.ERROR, cid, stage, msg)

    /** Logs an error with its exact stage, code, exception class and (redacted) core output. */
    fun error(cid: String?, stage: String, error: ConnectionError, coreOutput: String? = null) {
        val cause = error.cause?.let { " exception=${it.javaClass.simpleName}: ${it.message}" } ?: ""
        val core = if (!coreOutput.isNullOrBlank()) " core=[${coreOutput.trim().takeLast(600)}]" else ""
        log(Level.ERROR, cid, stage, "code=${error.code} ${error.technical}$cause$core")
    }

    fun dump(): String = synchronized(lock) { buffer.joinToString("\n") { format(it) } }

    fun clear() = synchronized(lock) { buffer.clear() }

    private fun format(e: Entry): String {
        val t = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date(e.timeMs))
        return "$t ${e.level.name.first()} [${e.connectionId}] ${e.stage}: ${e.message}"
    }
}
