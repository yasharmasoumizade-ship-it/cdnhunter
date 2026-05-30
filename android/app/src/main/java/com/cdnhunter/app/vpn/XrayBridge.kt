package com.cdnhunter.app.vpn

/**
 * Bridge to native xray-core (libv2ray.aar).
 * Calls into the Go library via JNI.
 *
 * When libv2ray.aar is available, this calls real xray functions.
 * Until then, it's a stub that logs errors.
 */
object XrayBridge {

    private var running = false

    fun start(assetsDir: String, configPath: String) {
        try {
            // Real call: libv2ray.InitCoreEnv(assetsDir, "")
            // Real call: libv2ray.StartLoop(configContent, tunFd)
            libv2ray.Libv2ray.initCoreEnv(assetsDir, "")
            val config = java.io.File(configPath).readText()
            libv2ray.Libv2ray.startLoop(config, 0)
            running = true
        } catch (e: Exception) {
            throw RuntimeException("Xray start failed: ${e.message}", e)
        }
    }

    fun stop() {
        if (running) {
            try {
                libv2ray.Libv2ray.stopLoop()
            } catch (_: Exception) {}
            running = false
        }
    }

    fun queryStats(): Pair<Long, Long> {
        if (!running) return 0L to 0L
        return try {
            val up = libv2ray.Libv2ray.queryStats("proxy", "uplink")
            val down = libv2ray.Libv2ray.queryStats("proxy", "downlink")
            up to down
        } catch (_: Exception) { 0L to 0L }
    }

    fun measureDelay(url: String = "https://www.google.com/generate_204"): Long {
        if (!running) return -1
        return try {
            libv2ray.Libv2ray.measureDelay(url)
        } catch (_: Exception) { -1 }
    }

    fun version(): String {
        return try { libv2ray.Libv2ray.checkVersionX() } catch (_: Exception) { "unknown" }
    }
}
