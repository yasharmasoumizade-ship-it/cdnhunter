package com.cdnhunter.app.vpn.singbox

fun interface SocketProtector {
    /** VpnService.protect(fd): keeps the engine's own sockets out of the tunnel. False means it could not. */
    fun protect(fd: Int): Boolean
}

/** index -1 and an empty name mean "no usable underlying network". */
data class DefaultInterface(val name: String, val index: Int, val expensive: Boolean, val constrained: Boolean) {
    companion object { val NONE = DefaultInterface("", -1, false, false) }
}

interface DefaultInterfaceSource {
    fun start(onChange: (DefaultInterface) -> Unit)
    fun stop()
}

data class NetIf(
    val index: Int, val mtu: Int, val name: String, val addresses: List<String>, val flags: Int, val type: Int,
    val dns: List<String>, val gateway: List<String>, val metered: Boolean,
)

interface InterfaceLister { fun list(): List<NetIf> }

/** Raw Linux IFF_* bits: libbox's linkFlags() converts from these. */
object LinuxIfFlags {
    const val UP = 0x1
    const val BROADCAST = 0x2
    const val LOOPBACK = 0x8
    const val POINTOPOINT = 0x10
    const val RUNNING = 0x40
    const val MULTICAST = 0x1000

    fun of(up: Boolean, loopback: Boolean, pointToPoint: Boolean, multicast: Boolean): Int {
        var f = 0
        if (up) f = f or UP or RUNNING
        if (loopback) f = f or LOOPBACK
        if (pointToPoint) f = f or POINTOPOINT
        if (multicast) f = f or MULTICAST
        return f
    }
}

/** sing-box constant.InterfaceType: WIFI, Cellular, Ethernet, Other (v1.14.2). */
object IfType {
    const val WIFI = 0
    const val CELLULAR = 1
    const val ETHERNET = 2
    const val OTHER = 3
}
