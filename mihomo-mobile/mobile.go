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
	"github.com/metacubex/mihomo/listener"
	LC "github.com/metacubex/mihomo/listener/config"
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

	// executor.Shutdown() -> listener.Cleanup() only closes the TUN
	// listener (closeTunListener()). It never touches the mixed-port
	// listener, and it never resets listener.LastTunConf. That's correct
	// for mihomo's normal usage (a CLI process that exits and takes every
	// socket with it), but wrong here: we're embedded as a library in a
	// long-lived Android process that starts/stops repeatedly. Left as-is
	// this caused two symptoms:
	//
	//  1. The mixed-port listener (127.0.0.1:10808) is a package-level
	//     singleton that's never closed, so it keeps accepting connections
	//     forever after "Stop" — the port never actually frees up.
	//  2. Worse, on the *next* Start(), ReCreateTun compares the new tun
	//     config against listener.LastTunConf — which Shutdown() never
	//     cleared. Android almost always hands back the same fd number for
	//     the new TUN (it's just reusing the freed slot), so every field
	//     including FileDescriptor matches the stale LastTunConf, the equal
	//     check short-circuits, and no real TUN listener gets created at
	//     all. Only the never-closed mixed-port listener from point 1 keeps
	//     working, which is exactly the "only 127.0.0.1:10808 gets
	//     proxied" symptom.
	//
	// Fix: force both closed/reset ourselves. ReCreateMixed(0, nil) closes
	// the mixed listener (port 0 short-circuits before the nil tunnel is
	// ever used). ReCreateTun(LC.Tun{}, nil) is Enable:false, guaranteed to
	// differ from the real (Enable:true) config, so its deferred
	// `LastTunConf = tunConf` unconditionally overwrites the stale value —
	// guaranteeing the next Start() sees a real change and actually builds
	// a fresh TUN listener regardless of fd-number reuse.
	listener.ReCreateMixed(0, nil)
	listener.ReCreateTun(LC.Tun{}, nil)

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
