package com.cdnhunter.app.core.routing

import com.cdnhunter.app.core.ConnectionError
import com.cdnhunter.app.core.ErrorCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select

/**
 * The part of sing-box the lifecycle needs, with no libbox types in it. The libbox-backed implementation
 * (CommandServer + PlatformInterface) is a thin layer on top; everything decided here is testable without it.
 */
interface SingBoxEngine {
    /** Starts the service with this config. Throws if it cannot start. When it returns, the engine is running. */
    suspend fun start(configJson: String)

    /** Completes ONLY if the engine stops by itself (crash, unexpected close), with a short reason. Never while it runs normally. */
    suspend fun awaitTermination(): String

    /** Fully stops the engine and releases everything it holds. Idempotent. Returns once it is stopped. */
    suspend fun stop()

    fun isAlive(): Boolean
}

/** Proves the proxy PATH works (not merely that a process or an interface exists). */
fun interface ConnectivityVerifier {
    suspend fun verify(): CoreOutcome
}

/** libbox asked the app for a TUN the app-owned TUN cannot honour. Core-independent, so it never triggers a fallback. */
class TunSetupException(message: String) : Exception(message)

sealed class SingBoxConfig {
    class Ok(val json: String) : SingBoxConfig()
    class Err(val error: ConnectionError) : SingBoxConfig()
}

fun interface SingBoxConfigSource {
    suspend fun config(): SingBoxConfig
}

/**
 * sing-box as one engine behind the shared lifecycle.
 *
 * "Connected" is reported only after the [verifier] confirms the proxy path. While it verifies, the engine's own
 * death is watched, so a crash during startup is a CORE_CRASHED failure at once, not a wait for a timeout.
 */
class SingBoxCore(
    private val engine: SingBoxEngine,
    private val configSource: SingBoxConfigSource,
    private val verifier: ConnectivityVerifier,
) : VpnCore {
    override val type: CoreType = CoreType.SING_BOX

    override suspend fun connect(): CoreOutcome {
        val json = when (val c = configSource.config()) {
            is SingBoxConfig.Ok -> c.json
            is SingBoxConfig.Err -> return CoreOutcome.Failed(c.error) // a config problem: the engine is never started
        }
        try {
            engine.start(json)
        } catch (e: CancellationException) {
            throw e
        } catch (e: TunSetupException) {
            return CoreOutcome.Failed(ConnectionError.TunnelError("sing-box TUN set-up refused: ${e.message}"))
        } catch (e: Exception) {
            return CoreOutcome.Failed(ConnectionError.CoreError(ErrorCode.CORE_START_FAILED, "sing-box did not start: ${e.javaClass.simpleName}: ${e.message}", e))
        }
        return coroutineScope {
            val death = async {
                try { engine.awaitTermination() } catch (e: CancellationException) { throw e } catch (e: Exception) { "termination watcher failed: ${e.javaClass.simpleName}" }
            }
            val check = async {
                try {
                    verifier.verify()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    CoreOutcome.Failed(ConnectionError.UnknownError("verification threw ${e.javaClass.simpleName}", e))
                }
            }
            val result = select<CoreOutcome> {
                death.onAwait { reason -> CoreOutcome.Failed(ConnectionError.CoreError(ErrorCode.CORE_CRASHED, "sing-box stopped during startup: $reason")) }
                check.onAwait { it }
            }
            death.cancel()
            check.cancel()
            result
        }
    }

    override suspend fun stop() = engine.stop()

    override fun isAlive(): Boolean = engine.isAlive()
}
