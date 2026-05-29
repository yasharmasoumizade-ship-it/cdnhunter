package com.cdnhunter.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cdnhunter.app.data.*
import com.cdnhunter.app.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ScanViewModel : ViewModel() {

    private val scanEngine = ScanEngine()
    private val frontingEngine = FrontingEngine()
    private val throughputEngine = ThroughputEngine()
    private val geoService = GeoService()

    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private val _config = MutableStateFlow(ScanConfig())
    val config: StateFlow<ScanConfig> = _config.asStateFlow()

    private var scanJob: Job? = null

    fun updateConfig(newConfig: ScanConfig) {
        _config.value = newConfig
    }

    fun startScan() {
        if (_state.value.running) return

        scanJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                runFullScan()
            } catch (e: CancellationException) {
                log("Scan cancelled")
            } catch (e: Exception) {
                log("ERR: ${e.message}")
            } finally {
                _state.update { it.copy(running = false) }
            }
        }
    }

    fun stopScan() {
        scanEngine.requestStop()
        scanJob?.cancel()
        log("STOP requested")
        _state.update { it.copy(running = false, phase = ScanPhase.DONE, phaseDetail = "Stopped") }
    }

    fun copyIps(): String {
        val results = _state.value.results
        // Prefer fronting-ok IPs, fallback to all healthy
        val frontingIps = results.filter { it.ok && it.frontingOk }.map { it.ip }
        val ips = frontingIps.ifEmpty { results.filter { it.ok }.map { it.ip } }
        return ips.joinToString("\n")
    }

    private suspend fun runFullScan() {
        val cfg = _config.value

        // Reset state
        _state.update {
            ScanState(
                running = true,
                phase = ScanPhase.SCANNING,
                phaseDetail = "Loading IPs",
                source = cfg.cdnProvider.label.take(10),
                logs = listOf("Scan started — ${cfg.cdnProvider.label}, conc=${cfg.concurrency}")
            )
        }

        // ── Phase 1: Scan ──────────────────────────────────────────────────
        setPhase(ScanPhase.SCANNING, "Scanning IPs")
        log("[Phase 1/4] Scanning...")

        scanEngine.onProgress = { scanned, healthy, total ->
            val pct = if (total > 0) (scanned * 100) / total else 0
            _state.update { it.copy(scanned = scanned, healthy = healthy, failed = scanned - healthy, pct = pct) }
        }
        scanEngine.onLiveResult = { result ->
            if (result.ok) {
                _state.update { cur ->
                    val live = (cur.liveIps + result).takeLast(15)
                    cur.copy(liveIps = live)
                }
            }
        }
        scanEngine.onLog = { msg -> log(msg) }

        val healthy = scanEngine.scan(cfg)

        if (healthy.isEmpty()) {
            log("No healthy IPs found")
            _state.update { it.copy(phase = ScanPhase.DONE, phaseDetail = "No results", pct = 100) }
            return
        }

        log("${healthy.size} healthy IPs found")
        _state.update { it.copy(healthy = healthy.size, pct = 100) }

        // ── Phase 2: IP Check (geo + Iran filter) ──────────────────────────
        setPhase(ScanPhase.IP_CHECK, "Checking regions")
        log("[Phase 2/4] Checking IP regions...")

        val filtered = geoService.filterIranIps(healthy, cfg.timeout)
        val iranRemoved = healthy.size - filtered.size
        if (iranRemoved > 0) log("$iranRemoved Iranian IP(s) removed")
        log("${filtered.size} IPs passed region check")

        // ── Phase 3: Fronting ──────────────────────────────────────────────
        setPhase(ScanPhase.FRONTING, "Testing fronting")
        log("[Phase 3/4] Checking fronting for ${filtered.size} IPs...")

        val frontingResults = frontingEngine.checkFronting(
            healthy = filtered,
            timeout = cfg.timeout,
            sniOverride = cfg.sni,
            concurrency = minOf(cfg.concurrency, 50)
        )
        val frontingOk = frontingResults.filter { it.frontingOk }
        log("${frontingOk.size} fronting OK")

        // ── Phase 4: Throughput ────────────────────────────────────────────
        if (frontingOk.isNotEmpty()) {
            setPhase(ScanPhase.THROUGHPUT, "Measuring throughput")
            log("[Phase 4/4] Throughput for ${frontingOk.size} IPs...")

            val withThroughput = throughputEngine.testThroughput(
                ips = frontingOk,
                timeout = cfg.timeout + 1f,
                concurrency = 15
            )
            // Merge throughput data back
            val tpMap = withThroughput.associateBy { it.ip }
            val finalResults = frontingResults.map { r ->
                tpMap[r.ip] ?: r
            }
            _state.update { it.copy(results = finalResults) }
            log("Throughput done")
        } else {
            log("[Phase 4/4] Throughput skipped (no fronting IPs)")
            _state.update { it.copy(results = frontingResults) }
        }

        // ── Done ───────────────────────────────────────────────────────────
        setPhase(ScanPhase.DONE, "Complete")
        val finalState = _state.value
        val fCount = finalState.results.count { it.frontingOk }
        log("Done — ${finalState.results.count { it.ok }} healthy / $fCount fronting OK")
    }

    private fun setPhase(phase: ScanPhase, detail: String) {
        _state.update { it.copy(phase = phase, phaseDetail = detail) }
    }

    private fun log(msg: String) {
        _state.update { cur ->
            val logs = (cur.logs + msg).takeLast(200)
            cur.copy(logs = logs)
        }
    }

    override fun onCleared() {
        super.onCleared()
        scanEngine.requestStop()
        scanJob?.cancel()
    }
}
