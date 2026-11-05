package core

import (
	"errors"
	"sync"
	"sync/atomic"
	"syscall"
	"time"
)

var (
	errSocketProtectionUnavailable = errors.New("VPN socket protection unavailable")
	errSocketProtectionRejected    = errors.New("VPN socket protection rejected")
	bootstrapClock                 = time.Now()
)

func bootstrapMillis() int64 { return time.Since(bootstrapClock).Milliseconds() }

type socketProtectionSnapshot struct {
	Phase            string `json:"phase"`
	StartedAtMillis  int64  `json:"startedAtMillis"`
	ReadyAtMillis    int64  `json:"readyAtMillis"`
	ClosedAtMillis   int64  `json:"closedAtMillis"`
	Accepted         uint64 `json:"accepted"`
	Rejected         uint64 `json:"rejected"`
	Unavailable      uint64 `json:"unavailable"`
	CallsDuringStart uint64 `json:"callsDuringStart"`
}

// 作者 long：Android建立VPN后路由已生效，保护能力必须先于TUN栈发布；listener尚未返回不能成为跳过保护的理由。
type socketProtectionGate struct {
	mu       sync.RWMutex
	protect  func(int) bool
	phase    string
	started  int64
	ready    int64
	closed   int64
	accepted atomic.Uint64
	rejected atomic.Uint64
	missing  atomic.Uint64
	starting atomic.Uint64
}

func newSocketProtectionGate(protect func(int) bool) *socketProtectionGate {
	return &socketProtectionGate{protect: protect, phase: "starting", started: bootstrapMillis()}
}

func (g *socketProtectionGate) protectSocket(fd int) error {
	g.mu.RLock()
	defer g.mu.RUnlock()
	if g.protect == nil {
		g.missing.Add(1)
		return errSocketProtectionUnavailable
	}
	if g.phase == "starting" {
		g.starting.Add(1)
	}
	if !g.protect(fd) {
		g.rejected.Add(1)
		// 作者 long：平台拒绝保护时不能把socket当成功出站，否则仍有效的VPN路由会把它送回自身。
		return errSocketProtectionRejected
	}
	g.accepted.Add(1)
	return nil
}

func (g *socketProtectionGate) markReady() {
	g.mu.Lock()
	defer g.mu.Unlock()
	if g.protect != nil {
		g.phase, g.ready = "ready", bootstrapMillis()
	}
}

func (g *socketProtectionGate) close() {
	g.mu.Lock()
	defer g.mu.Unlock()
	// 作者 long：等待已进入的平台保护调用返回，再释放回调；已拿到旧handler的迟到拨号应失败，不能无保护继续。
	g.protect, g.phase, g.closed = nil, "closed", bootstrapMillis()
}

func (g *socketProtectionGate) snapshot() socketProtectionSnapshot {
	g.mu.RLock()
	defer g.mu.RUnlock()
	return socketProtectionSnapshot{g.phase, g.started, g.ready, g.closed,
		g.accepted.Load(), g.rejected.Load(), g.missing.Load(), g.starting.Load()}
}

func protectRawSocket(conn syscall.RawConn, gate *socketProtectionGate) error {
	var protectionError error
	if err := conn.Control(func(fd uintptr) { protectionError = gate.protectSocket(int(fd)) }); err != nil {
		return err
	}
	return protectionError
}
