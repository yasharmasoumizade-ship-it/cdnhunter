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
	"syscall"

	"github.com/metacubex/mihomo/config"
	"github.com/metacubex/mihomo/hub/executor"
	"github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/listener"
	LC "github.com/metacubex/mihomo/listener/config"
	"github.com/metacubex/mihomo/component/dialer"
	"github.com/metacubex/mihomo/log"
	"github.com/metacubex/mihomo/tunnel/statistic"
)

var (
	mu      sync.Mutex
	running bool

	protectorMu sync.Mutex
	protector   Protector
	hookSet     bool

	protectLogMu  sync.Mutex
	protectLogBuf []string

	coreLogMu  sync.Mutex
	coreLogBuf []string
)

// logProtect appends one line to a small ring buffer describing a dial
// attempt seen by the SocketControl hook — whether a protector was
// registered at all, and whether the protect() call itself reported
// success. Exposed via ProtectLog() so the Android side can include it
// when the user shares debug info, since a dial that silently never
// leaves the device produces no error anywhere else in the stack.
func logProtect(line string) {
	protectLogMu.Lock()
	defer protectLogMu.Unlock()
	protectLogBuf = append(protectLogBuf, line)
	if len(protectLogBuf) > 50 {
		protectLogBuf = protectLogBuf[len(protectLogBuf)-50:]
	}
}

// ProtectLog returns the recent dial/protect attempt history, newest last.
func ProtectLog() string {
	protectLogMu.Lock()
	defer protectLogMu.Unlock()
	if len(protectLogBuf) == 0 {
		return "(no dial attempts observed yet)"
	}
	out := ""
	for _, l := range protectLogBuf {
		out += l + "\n"
	}
	return out
}

// logCore appends one formatted line to mihomo's own internal log ring
// buffer (rule matching, DNS hijack, TUN read/write, dial attempts at the
// core level).
func logCore(line string) {
coreLogMu.Lock()
defer coreLogMu.Unlock()
coreLogBuf = append(coreLogBuf, line)
if len(coreLogBuf) > 300 {
coreLogBuf = coreLogBuf[len(coreLogBuf)-300:]
}
}

// CoreLog returns mihomo's own internal debug/info/warning/error log lines
// captured since the last Start(), newest last.
func CoreLog() string {
coreLogMu.Lock()
defer coreLogMu.Unlock()
if len(coreLogBuf) == 0 {
return "(no core log lines captured yet)"
}
out := ""
for _, l := range coreLogBuf {
out += l + "\n"
}
return out
}

// startCoreLogCapture subscribes to mihomo's internal log broadcast and
// feeds every line into logCore(). Safe to call repeatedly.
func startCoreLogCapture() {
sub := log.Subscribe()
go func() {
for elm := range sub {
logCore(fmt.Sprintf("[%s] %s", elm.LogLevel.String(), elm.Payload))
}
}()
}

// Protector is implemented on the Kotlin/Java side (gomobile reverse
// binding) as a thin wrapper around android.net.VpnService.protect(fd).
// Without this, every socket mihomo opens to actually reach the user's
// remote proxy server gets captured by the very TUN interface mihomo
// itself is feeding, and the connection never leaves the device — the
// classic "only 127.0.0.1:<mixed-port> works, real device traffic
// doesn't route" symptom. "tun.auto-detect-interface" alone does NOT fix
// this on unrooted Android: binding directly to a network interface via
// SO_BINDTODEVICE requires CAP_NET_ADMIN, which a normal app process
// doesn't have. VpnService.protect() is the only privilege-free way to
// exempt a specific socket from the VPN's own routing on Android, and it
// must be called explicitly per-socket — there's no automatic mechanism.
type Protector interface {
	Protect(fd int) bool
}

// SetProtector registers the Android-side protector. Must be called once
// before Start() — CdnVpnService does this every connection attempt,
// which is harmless (just overwrites the same hook).
func SetProtector(p Protector) {
	protectorMu.Lock()
	defer protectorMu.Unlock()
	protector = p
	if !hookSet {
		// dialer.DefaultSocketHook is mihomo's own extension point for
		// exactly this purpose (the same one CMFA uses) — it's invoked as
		// the Control function on every socket mihomo's dialer/listener
		// creates, network and outbound listeners included.
		dialer.DefaultSocketHook = func(network, address string, c syscall.RawConn) error {
			protectorMu.Lock()
			p := protector
			protectorMu.Unlock()
			if p == nil {
				logProtect(fmt.Sprintf("network=%s address=%s: NO PROTECTOR REGISTERED (skipped)", network, address))
				return nil
			}
			var protectErr error
			var gotFd uintptr
			err := c.Control(func(fd uintptr) {
				gotFd = fd
				if !p.Protect(int(fd)) {
					protectErr = fmt.Errorf("VpnService.protect failed for fd %d (network=%s address=%s)", fd, network, address)
				}
			})
			if err != nil {
				logProtect(fmt.Sprintf("network=%s address=%s: c.Control ERROR: %v", network, address, err))
				return err
			}
			if protectErr != nil {
				logProtect(fmt.Sprintf("network=%s address=%s fd=%d: PROTECT FAILED: %v", network, address, gotFd, protectErr))
			} else {
				logProtect(fmt.Sprintf("network=%s address=%s fd=%d: protected OK", network, address, gotFd))
			}
			return protectErr
		}
		hookSet = true
	}
}

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

	coreLogMu.Lock()
	coreLogBuf = nil
	coreLogMu.Unlock()
	// Must subscribe BEFORE ApplyConfig, not after: ApplyConfig is what
	// actually builds the TUN listener and logs whether it succeeded (e.g.
	// "[TUN] use tun name %s for fd %d" or the "get tun name failed"
	// fallback warning). Subscribing afterward means log.Subscribe() opens
	// its channel too late to catch anything ApplyConfig already emitted —
	// exactly why coreLog showed no TUN-related lines at all even on runs
	// where TUN setup may have silently failed.
	startCoreLogCapture()

	executor.ApplyConfig(cfg, true)
	running = true

	protectLogMu.Lock()
	protectLogBuf = nil
	protectLogMu.Unlock()

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
}

// IsRunning reports whether the kernel is currently active.
func IsRunning() bool {
	mu.Lock()
	defer mu.Unlock()
	return running
}

// TrafficUp/TrafficDown return cumulative bytes sent/received since the
// kernel started (mihomo's own running totals — resets to 0 every time a
// fresh Start() rebuilds statistic.DefaultManager's counters), polled by
// the Kotlin side once a second for the home screen's live counters and
// session total. No goroutine or HTTP round-trip needed: this reads the
// same in-process atomic counters mihomo's own /traffic endpoint reports,
// directly.
func TrafficUp() int64 {
	up, _ := statistic.DefaultManager.Total()
	return up
}

func TrafficDown() int64 {
	_, down := statistic.DefaultManager.Total()
	return down
}
