package com.cdnhunter.app.vpn

import java.io.File

/**
 * Bridge to hev-socks5-tunnel native library.
 * Uses a YAML config file to configure the tunnel.
 */
object Tun2SocksBridge {

    private var running = false

    init {
        try {
            System.loadLibrary("hev-socks5-tunnel")
        } catch (_: UnsatisfiedLinkError) {
            // Will be available after native build
        }
    }

    fun start(tunFd: Int, socksAddr: String, socksPort: Int, configFile: File) {
        if (running) return
        val config = """
tunnel:
  mtu: 8500

socks5:
  port: $socksPort
  address: '$socksAddr'
  udp: 'tcp'

misc:
  task-stack-size: 81920
  connect-timeout: 5000
  read-write-timeout: 60000
  log-level: warn
""".trimIndent()
        configFile.writeText(config)
        nativeStartTunnel(configFile.absolutePath, tunFd)
        running = true
    }

    fun stop() {
        if (running) {
            try { nativeStopTunnel() } catch (_: Exception) {}
            running = false
        }
    }

    fun isRunning() = running

    // JNI native methods
    private external fun nativeStartTunnel(configPath: String, fd: Int)
    private external fun nativeStopTunnel()
}
