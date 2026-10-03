package com.cdnhunter.app.vpn.singbox

import com.cdnhunter.app.core.routing.TunSetupException

/** What the service's own TUN looks like: the ONLY thing libbox may assume about it. Addresses are "addr/prefix". */
data class TunExpectation(val mtu: Int, val inet4: Set<String>, val inet6: Set<String>)

/** A pure projection of libbox's TunOptions, so the validation is testable without JNI. */
data class TunSpec(
    val mtu: Int,
    val inet4: List<String>,
    val inet6: List<String>,
    val httpProxyEnabled: Boolean,
    val includePackage: List<String>,
    val excludePackage: List<String>,
)

/**
 * The service keeps ownership of its TUN (and with it the kill switch, the IPv6 policy and reuseTun).
 * libbox is only LENT the fd number: libbox's own OpenInterface() dup()s what openTun returns and uses its
 * dup, so no fd is ever handed over, duplicated here, or closed by the glue — nothing can double-close or leak.
 * The lent fd must stay open until the engine is stopped; the service closes it only after that.
 */
interface TunLender {
    fun expectation(): TunExpectation?

    /** The service's own TUN fd number, or -1 when the service has no TUN right now. */
    fun lend(): Int
}

object TunValidator {
    fun validate(spec: TunSpec, exp: TunExpectation) {
        if (spec.httpProxyEnabled) throw TunSetupException("a system HTTP proxy is not allowed")
        if (spec.includePackage.isNotEmpty() || spec.excludePackage.isNotEmpty()) {
            throw TunSetupException("per-app include/exclude lists would be silently ignored by the app-owned TUN")
        }
        if (spec.mtu != exp.mtu) throw TunSetupException("TUN MTU differs from the one the service established")
        if (spec.inet4.any { it !in exp.inet4 }) throw TunSetupException("sing-box expects an IPv4 address the service did not configure")
        if (spec.inet6.any { it !in exp.inet6 }) throw TunSetupException("sing-box expects an IPv6 address the service did not configure")
    }
}
