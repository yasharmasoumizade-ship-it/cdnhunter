package com.cdnhunter.app.vpn.singbox

import java.io.File
import java.security.SecureRandom

/**
 * libbox's command server is a gRPC server. Port 0 makes it listen on a UNIX socket inside [basePath] (the app's
 * private files dir); any other port would open TCP on 127.0.0.1, reachable by every app on the device. The
 * port is therefore fixed at 0 here, and every run gets a fresh random secret.
 */
class CommandServerParams(val basePath: String, val workingPath: String, val tempPath: String, val secret: String) {
    val listenPort: Int = 0
    override fun toString() = "CommandServerParams(base=$basePath, port=0, secret=***)"
}

object CommandServerSetup {
    /** sun_path is 108 bytes; leave room for the socket file name. */
    const val MAX_BASE_PATH = 80

    fun create(root: File, random: SecureRandom = SecureRandom()): CommandServerParams {
        val base = File(root, "sb")
        val work = File(base, "w")
        val tmp = File(base, "t")
        for (d in listOf(base, work, tmp)) {
            d.mkdirs()
            d.setReadable(false, false); d.setWritable(false, false); d.setExecutable(false, false)
            d.setReadable(true, true); d.setWritable(true, true); d.setExecutable(true, true)
        }
        require(base.absolutePath.length <= MAX_BASE_PATH) { "command server base path is too long for a unix socket" }
        val bytes = ByteArray(32).also { random.nextBytes(it) }
        val secret = bytes.joinToString("") { "%02x".format(it) }
        return CommandServerParams(base.absolutePath, work.absolutePath, tmp.absolutePath, secret)
    }
}
