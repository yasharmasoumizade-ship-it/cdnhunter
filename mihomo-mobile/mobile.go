// Package mobile is the gomobile-bound entry point that exposes the mihomo
// (Clash.Meta) proxy kernel to the Android/Kotlin side of CDN Hunter.
//
// gomobile bind only supports a narrow subset of Go types across the JNI
// boundary (basic types, strings, and interfaces/structs with exported
// methods) — no generics, no channels, no raw structs by value. So this
// file is deliberately a thin, flat façade: every exported function takes
// and returns strings/ints/bools only, and all the real mihomo types
// (config.Config, C.Proxy, etc.) stay fully inside this package.
package mobile

import (
	"bufio"
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"sync"
	"time"

	"github.com/metacubex/mihomo/config"
	"github.com/metacubex/mihomo/hub/executor"
	"github.com/metacubex/mihomo/constant"
)

var (
	mu      sync.Mutex
	running bool

	trafficMu   sync.Mutex
	lastUp      int64
	lastDown    int64
	controller  string // external-controller address, e.g. "127.0.0.1:10809"
)

// Start parses the given Clash/mihomo YAML config and applies it, bringing
// up all inbound/outbound listeners and the routing engine. homeDir is
// where mihomo keeps its working files (geoip db, cache) — pass the
// app's dedicated files directory (e.g. context.getFilesDir()/mihomo).
// Returns "" on success, or an error message on failure.
func Start(configYaml string, homeDir string) string {
	mu.Lock()
	defer mu.Unlock()

	if running {
		return ""
	}

	if err := os.MkdirAll(homeDir, 0o755); err != nil {
		return fmt.Sprintf("mkdir home dir: %v", err)
	}
	constant.SetHomeDir(homeDir)
	constant.SetConfig(homeDir + "/config.yaml")

	if err := os.WriteFile(homeDir+"/config.yaml", []byte(configYaml), 0o644); err != nil {
		return fmt.Sprintf("write config: %v", err)
	}

	cfg, err := config.Parse([]byte(configYaml))
	if err != nil {
		return fmt.Sprintf("parse config: %v", err)
	}

	executor.ApplyConfig(cfg, true)
	running = true

	trafficMu.Lock()
	// The YAML this app generates always sets external-controller to this
	// fixed local address (see VpnConfigBuilder.kt) — hardcoding it here
	// avoids depending on the exact shape of config.Config's General struct,
	// which can change across mihomo versions.
	controller = "127.0.0.1:10809"
	lastUp, lastDown = 0, 0
	trafficMu.Unlock()

	// The traffic stream on /traffic starts emitting immediately once the
	// controller is up; give the listener a brief moment to bind before the
	// Kotlin side starts polling TrafficUp/TrafficDown.
	go pollTraffic()

	return ""
}

// Stop tears down all listeners and the routing engine. Safe to call even
// if Start was never called or already stopped.
func Stop() {
	mu.Lock()
	defer mu.Unlock()
	if !running {
		return
	}
	executor.Shutdown()
	running = false
	trafficMu.Lock()
	controller = ""
	trafficMu.Unlock()
}

// IsRunning reports whether the kernel is currently active.
func IsRunning() bool {
	mu.Lock()
	defer mu.Unlock()
	return running
}

// pollTraffic reads mihomo's own streaming /traffic endpoint (newline-delimited
// JSON objects: {"up":N,"down":N} in bytes-per-second) and keeps the latest
// cumulative totals in lastUp/lastDown. Runs for the lifetime of one Start()
// call; exits on its own once the controller stops responding (i.e. Stop()
// was called or the process is tearing down).
func pollTraffic() {
	trafficMu.Lock()
	addr := controller
	trafficMu.Unlock()
	if addr == "" {
		return
	}

	client := &http.Client{Timeout: 0}
	var resp *http.Response
	var err error
	for i := 0; i < 10; i++ {
		resp, err = client.Get("http://" + addr + "/traffic")
		if err == nil {
			break
		}
		time.Sleep(300 * time.Millisecond)
	}
	if err != nil {
		return
	}
	defer resp.Body.Close()

	var cumUp, cumDown int64
	scanner := bufio.NewScanner(resp.Body)
	for scanner.Scan() {
		trafficMu.Lock()
		stillRunning := running
		trafficMu.Unlock()
		if !stillRunning {
			return
		}

		var sample struct {
			Up   int64 `json:"up"`
			Down int64 `json:"down"`
		}
		if err := json.Unmarshal(scanner.Bytes(), &sample); err != nil {
			continue
		}
		// mihomo's /traffic reports bytes-in-the-last-second, not a running
		// total, so accumulate it ourselves for the UI's cumulative counters.
		cumUp += sample.Up
		cumDown += sample.Down

		trafficMu.Lock()
		lastUp = cumUp
		lastDown = cumDown
		trafficMu.Unlock()

		time.Sleep(0) // yield; scanner.Scan() already blocks until the next line
	}
}

// TrafficUp/TrafficDown return cumulative bytes sent/received since Start,
// polled by the Kotlin side for the live speed counters on the home screen.
func TrafficUp() int64 {
	trafficMu.Lock()
	defer trafficMu.Unlock()
	return lastUp
}

func TrafficDown() int64 {
	trafficMu.Lock()
	defer trafficMu.Unlock()
	return lastDown
}

