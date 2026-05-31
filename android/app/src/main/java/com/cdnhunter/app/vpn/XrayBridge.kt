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

    private val callbackHandler = object : CoreCallbackHandler {
        override fun startup(): Long = 0
        override fun shutdown(): Long = 0
        override fun onEmitStatus(status: Long, msg: String?): Long {
            android.util.Log.d("XrayBridge", "status=$status msg=$msg")
            return 0
        }
    }

    fun init(assetsDir: String) {
        Libv2ray.initCoreEnv(assetsDir, "")
        controller = Libv2ray.newCoreController(callbackHandler)
    }

    fun start(configContent: String, tunFd: Int = 0) {
        val ctrl = controller ?: throw IllegalStateException("Call init() first")
        try {
            ctrl.startLoop(configContent, tunFd)
            running = true
            android.util.Log.i("XrayBridge", "Xray started OK")
        } catch (e: Exception) {
            android.util.Log.e("XrayBridge", "startLoop failed: ${e.message}")
            throw e
        }
    }

    fun stop() {
        if (running) {
            controller?.stopLoop()
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
