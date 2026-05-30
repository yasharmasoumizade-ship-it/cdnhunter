package com.cdnhunter.app.vpn

/**
 * Bridge to hev-socks5-tunnel native library.
 * Routes TUN traffic through the SOCKS5 proxy (xray).
 *
 * Loads libhev-socks5-tunnel.so via JNI.
 */
object Tun2SocksBridge {

    private var running = false

    init {
        try {
            System.loadLibrary("hev-socks5-tunnel")
        } catch (e: UnsatisfiedLinkError) {
            // Library not available yet - will fail gracefully
        }
    }

    fun start(tunFd: Int, socksAddr: String, socksPort: Int) {
        if (running) return
        try {
            // Create tun2socks config
            val config = """
                tunnel:
                  mtu: 1500
                socks5:
                  address: $socksAddr
                  port: $socksPort
                  udp: tcp
            """.trimIndent()
            nativeStart(tunFd, config)
            running = true
        } catch (e: Exception) {
            throw RuntimeException("tun2socks start failed: ${e.message}", e)
        }
    }

    fun stop() {
        if (running) {
            try { nativeStop() } catch (_: Exception) {}
            running = false
        }
    }

    // JNI methods (implemented in libhev-socks5-tunnel.so)
    private external fun nativeStart(tunFd: Int, config: String)
    private external fun nativeStop()
}
