package hev.htproxy

import java.io.File

/**
 * JNI bridge to hev-socks5-tunnel native library.
 * Package/class name MUST match the native JNI registration:
 *   PKGNAME = hev/htproxy
 *   CLSNAME = TProxyService
 */
object TProxyService {

    private var running = false

    init {
        try {
            System.loadLibrary("hev-socks5-tunnel")
        } catch (_: UnsatisfiedLinkError) {}
    }

    fun start(configFile: File, tunFd: Int) {
        if (running) return
        TProxyStartService(configFile.absolutePath, tunFd)
        running = true
    }

    fun stop() {
        if (!running) return
        try { TProxyStopService() } catch (_: Exception) {}
        running = false
    }

    fun getStats(): LongArray {
        return try { TProxyGetStats() ?: longArrayOf(0, 0, 0, 0) } catch (_: Exception) { longArrayOf(0, 0, 0, 0) }
    }

    fun isRunning() = running

    // JNI native methods — names MUST match hev-jni.c
    private external fun TProxyStartService(configPath: String, fd: Int)
    private external fun TProxyStopService()
    private external fun TProxyGetStats(): LongArray?
}
