package com.cdnhunter.app.vpn

import com.cdnhunter.mihomo.mobile.Mobile

object MihomoBridge {

    private var running = false

    // The actual error string mihomo itself returned on the last failed start()
    // call (config validation errors, bind failures, etc.) — NOT just logcat.
    var lastError: String = ""
        private set

    @Synchronized
    fun start(configYaml: String, homeDir: String): Boolean {
        return try {
            val err = Mobile.start(configYaml, homeDir)
            if (err.isNullOrEmpty()) {
                running = true
                lastError = ""
                android.util.Log.i("MihomoBridge", "mihomo started OK")
                true
            } else {
                lastError = err
                android.util.Log.e("MihomoBridge", "start failed: $err")
                running = false
                false
            }
        } catch (e: Exception) {
            lastError = "exception: ${e.message}\n${android.util.Log.getStackTraceString(e)}"
            android.util.Log.e("MihomoBridge", "start exception: ${e.message}")
            running = false
            false
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

    fun queryUpload(): Long = try { Mobile.trafficUp() } catch (_: Exception) { 0L }

    fun queryDownload(): Long = try { Mobile.trafficDown() } catch (_: Exception) { 0L }

    fun measureDelay(url: String = "https://www.google.com/generate_204"): Long = -1L

    fun version(): String = "mihomo-embedded"

    fun isRunning() = running
}
