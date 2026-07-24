package com.cdnhunter.app.vpn

import android.net.VpnService
import com.cdnhunter.mihomo.mobile.Mobile

object MihomoBridge {

    private var running = false

    // The actual error string mihomo itself returned on the last failed start()
    // call (config validation errors, bind failures, etc.) — NOT just logcat.
    var lastError: String = ""
        private set

    // Registers the Android-side socket protector with the Go core. Without
    // this, mihomo's own outbound socket to the real proxy server gets
    // captured by the TUN it created (loops back into itself), so only
    // local connections that never touch the TUN (e.g. 127.0.0.1:10808)
    // ever worked. Must be called before start(), every connection attempt
    // — cheap, just overwrites the same hook on the Go side.
    fun setProtector(service: VpnService) {
        try {
            Mobile.setProtector(object : com.cdnhunter.mihomo.mobile.Protector {
                // gomobile maps Go's `int` to Java/Kotlin `Long` (Go's int has no
                // fixed width, so the binding always widens to long) — the fd
                // itself is a normal 32-bit descriptor, so narrow it back down
                // for VpnService.protect(Int).
                override fun protect(fd: Long): Boolean = service.protect(fd.toInt())
            })
        } catch (e: Exception) {
            android.util.Log.e("MihomoBridge", "setProtector failed: ${e.message}")
        }
    }

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
