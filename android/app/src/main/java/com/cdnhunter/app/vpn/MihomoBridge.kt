package com.cdnhunter.app.vpn

import com.cdnhunter.mihomo.Mobile

object MihomoBridge {

    private var running = false

    @Synchronized
    fun start(configYaml: String, tunFd: Int = 0) {
        try {
            android.util.Log.i("MihomoBridge", "mihomo version: ${version()}")
            Mobile.start(configYaml)
            running = true
            android.util.Log.i("MihomoBridge", "mihomo started OK")
        } catch (e: Exception) {
            android.util.Log.e("MihomoBridge", "start failed: ${e.message}")
            running = false
            throw e
        }
    }

    @Synchronized
    fun stop() {
        if (running) {
            try { Mobile.stop() } catch (e: Exception) {
                android.util.Log.e("MihomoBridge", "stop failed: ${e.message}")
            }
            running = false
        }
    }

    fun queryUpload(): Long = try { Mobile.queryUpload() } catch (_: Exception) { 0L }

    fun queryDownload(): Long = try { Mobile.queryDownload() } catch (_: Exception) { 0L }

    fun measureDelay(url: String = "https://www.google.com/generate_204"): Long = -1L

    fun version(): String = try { Mobile.version() } catch (_: Exception) { "unknown" }

    fun isRunning() = running
}
