package com.cdnhunter.app.vpn

import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

/**
 * Bridge to native xray-core (libv2ray.aar from AndroidLibXrayLite).
 */
object XrayBridge {

    private var controller: CoreController? = null
    private var running = false
    private var envInitialized = false

    private val callbackHandler = object : CoreCallbackHandler {
        override fun startup(): Long = 0
        override fun shutdown(): Long = 0
        override fun onEmitStatus(status: Long, msg: String?): Long {
            android.util.Log.d("XrayBridge", "status=$status msg=$msg")
            return 0
        }
    }

    /**
     * Safe to call on every connect. initCoreEnv() must only run ONCE per process —
     * calling it again (e.g. on reconnect/AutoIP restart) corrupts xray-core's native
     * state and crashes the whole process with a JNI-level crash that no Kotlin
     * try/catch can stop. We guard it with envInitialized so repeated connects only
     * create a fresh CoreController, never re-touch the native env.
     */
    @Synchronized
    fun init(assetsDir: String) {
        if (!envInitialized) {
            Libv2ray.initCoreEnv(assetsDir, "")
            envInitialized = true
        }
        // Always tear down any previous controller before creating a new one —
        // never leave two CoreControllers alive at once.
        if (controller != null) {
            try { controller?.stopLoop() } catch (_: Exception) {}
            controller = null
        }
        controller = Libv2ray.newCoreController(callbackHandler)
    }

    @Synchronized
    fun start(configContent: String, tunFd: Int = 0) {
        val ctrl = controller ?: throw IllegalStateException("Call init() first")
        try {
            android.util.Log.i("XrayBridge", "xray version: ${version()}")
            ctrl.startLoop(configContent, tunFd)
            running = true
            android.util.Log.i("XrayBridge", "Xray started OK")
        } catch (e: Exception) {
            android.util.Log.e("XrayBridge", "startLoop failed: ${e.message}")
            running = false
            throw e
        }
    }

    @Synchronized
    fun stop() {
        if (running) {
            try { controller?.stopLoop() } catch (e: Exception) {
                android.util.Log.e("XrayBridge", "stopLoop failed: ${e.message}")
            }
            running = false
        }
    }

    fun queryUpload(): Long {
        return controller?.queryStats("proxy", "uplink") ?: 0
    }

    fun queryDownload(): Long {
        return controller?.queryStats("proxy", "downlink") ?: 0
    }

    fun measureDelay(url: String = "https://www.google.com/generate_204"): Long {
        return controller?.measureDelay(url) ?: -1
    }

    fun version(): String {
        return try { Libv2ray.checkVersionX() } catch (_: Exception) { "unknown" }
    }

    fun isRunning() = running
}
