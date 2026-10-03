package com.cdnhunter.app.vpn.singbox

import android.os.Build
import com.cdnhunter.mihomo.libbox.CommandServerHandler
import com.cdnhunter.mihomo.libbox.Libbox
import com.cdnhunter.mihomo.libbox.OverrideOptions
import com.cdnhunter.mihomo.libbox.PlatformInterface
import com.cdnhunter.mihomo.libbox.SetupOptions
import com.cdnhunter.mihomo.libbox.SystemProxyStatus

/**
 * The real libbox calls, in libbox's required order. Intentionally thin: everything with a decision in it lives in
 * [LibboxEngine] / [AndroidSingBoxPlatform] / [CommandServerSetup] and is unit-tested; this file only translates.
 */
class RealLibboxBackend(private val platform: PlatformInterface) : LibboxBackend {

    override fun setup(params: CommandServerParams) {
        val o = SetupOptions()
        o.setBasePath(params.basePath)
        o.setWorkingPath(params.workingPath)
        o.setTempPath(params.tempPath)
        o.setFixAndroidStack(Build.VERSION.SDK_INT == Build.VERSION_CODES.M)
        o.setCommandServerListenPort(params.listenPort) // 0 = private unix socket, never TCP
        o.setCommandServerSecret(params.secret)
        o.setLogMaxLines(300L)
        o.setDebug(false)
        Libbox.setup(o)
    }

    override fun newServer(events: ServerEvents): BackendServer {
        val handler = object : CommandServerHandler {
            override fun serviceStop() { events.onServiceStop() }
            override fun serviceReload() { events.onServiceReload() }
            override fun writeDebugMessage(message: String?) { if (message != null) events.onDebugMessage(message) }
            override fun getSystemProxyStatus(): SystemProxyStatus = throw UnsupportedOperationException("system proxy is not supported by this app")
            override fun setSystemProxyEnabled(enabled: Boolean) { throw UnsupportedOperationException("system proxy is not supported by this app") }
            override fun connectSSHAgent(): Int = throw UnsupportedOperationException("SSH agent is not supported by this app")
            override fun triggerNativeCrash() { throw UnsupportedOperationException("not supported") }
        }
        val server = Libbox.newCommandServer(handler, platform)
        return object : BackendServer {
            override fun start() = server.start()
            override fun startOrReloadService(configJson: String) = server.startOrReloadService(configJson, OverrideOptions())
            override fun closeService() = server.closeService()
            override fun close() = server.close()
        }
    }
}
