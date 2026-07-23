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
	"fmt"
	"os"
	"sync"

	"github.com/metacubex/mihomo/config"
	"github.com/metacubex/mihomo/hub/executor"
	"github.com/metacubex/mihomo/constant"
)

var (
	mu      sync.Mutex
	running bool
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
}

// IsRunning reports whether the kernel is currently active.
func IsRunning() bool {
	mu.Lock()
	defer mu.Unlock()
	return running
}

// TrafficUp/TrafficDown return cumulative bytes sent/received since Start,
// polled by the Kotlin side for the live speed counters on the home screen.
func TrafficUp() int64 {
	return int64(0) // wired to the real traffic manager in the follow-up commit
}

func TrafficDown() int64 {
	return int64(0)
}
