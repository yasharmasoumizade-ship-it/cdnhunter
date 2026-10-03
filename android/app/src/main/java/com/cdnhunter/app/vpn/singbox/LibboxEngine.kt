package com.cdnhunter.app.vpn.singbox

import com.cdnhunter.app.core.routing.SingBoxEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** What libbox tells the app about its service. Each engine instance has its own, so a late call can only touch that engine. */
interface ServerEvents {
    fun onServiceStop()
    fun onServiceReload()
    fun onDebugMessage(message: String)
}

interface BackendServer {
    fun start()
    fun startOrReloadService(configJson: String)
    fun closeService()
    fun close()
}

/** The libbox calls the lifecycle needs. The real one is RealLibboxBackend; tests use a fake. */
interface LibboxBackend {
    fun setup(params: CommandServerParams)
    fun newServer(events: ServerEvents): BackendServer
}

/**
 * sing-box via libbox: Libbox.setup -> newCommandServer -> server.start -> startOrReloadService, in exactly that order.
 *
 * One instance runs ONE attempt and is never reused. Its callbacks are tied to it, so a callback arriving after
 * stop() (or from an older attempt's engine) cannot change anything about a newer attempt. stop() is idempotent and
 * safe to call from any state, including while start() is blocked inside libbox: it never waits for start().
 */
class LibboxEngine(
    private val backend: LibboxBackend,
    private val params: () -> CommandServerParams,
    private val log: (String) -> Unit = {},
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : SingBoxEngine {

    private enum class State { IDLE, STARTING, RUNNING, DEAD, STOPPING, STOPPED }

    private val mon = Any()
    private var state = State.IDLE
    private var server: BackendServer? = null
    private val termination = CompletableDeferred<String>()

    @Volatile private var alive = false

    override suspend fun start(configJson: String) {
        synchronized(mon) {
            check(state == State.IDLE) { "this engine instance was already used" }
            state = State.STARTING
        }
        try {
            withContext(io) {
                backend.setup(params())
                val s = backend.newServer(Events())
                val kept = synchronized(mon) {
                    if (state == State.STARTING) { server = s; true } else false
                }
                if (!kept) {
                    runCatching { s.close() } // stop() won the race before the server existed
                    throw IllegalStateException("stopped while starting")
                }
                s.start()
                s.startOrReloadService(configJson)
            }
            synchronized(mon) {
                if (state != State.STARTING) throw IllegalStateException("stopped while starting")
                state = State.RUNNING
                alive = true
            }
        } catch (e: Throwable) {
            stop() // whatever was created is closed; idempotent
            throw e
        }
    }

    override suspend fun awaitTermination(): String = termination.await()

    override suspend fun stop() {
        // The server is taken out under the lock and handed back as the block's result, so
        // toClose is a val: the withContext lambda below can smart-cast it. (As a var assigned
        // inside the synchronized lambda it was a "changing closure" capture, and Kotlin refused.)
        val toClose: BackendServer? = synchronized(mon) {
            if (state == State.STOPPING || state == State.STOPPED) return
            state = State.STOPPING
            alive = false
            val taken = server
            server = null
            taken
        }
        withContext(NonCancellable + io) {
            if (toClose != null) {
                try { toClose.closeService() } catch (e: Exception) { log("closeService failed: ${e.javaClass.simpleName}") }
                try { toClose.close() } catch (e: Exception) { log("close failed: ${e.javaClass.simpleName}") }
            }
        }
        synchronized(mon) { state = State.STOPPED }
        termination.complete("stopped")
    }

    override fun isAlive(): Boolean = alive

    private inner class Events : ServerEvents {
        override fun onServiceStop() {
            val unexpected = synchronized(mon) {
                if (state == State.STARTING || state == State.RUNNING) { state = State.DEAD; alive = false; true } else false
            }
            // A stop we asked for (STOPPING/STOPPED) is not a crash and must not be reported as one.
            if (unexpected) termination.complete("libbox reported serviceStop")
        }

        override fun onServiceReload() { log("serviceReload ignored: the app never reloads a running service") }

        override fun onDebugMessage(message: String) { log(message) }
    }
}
