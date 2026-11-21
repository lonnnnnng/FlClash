package core

import (
	"errors"
	"net"
	"sync"
	"sync/atomic"
	"syscall"
	"testing"
	"time"
)

type protectionTestConn struct{ controlError error }

func (c protectionTestConn) Control(fn func(uintptr)) error {
	if c.controlError != nil {
		return c.controlError
	}
	fn(42)
	return nil
}
func (protectionTestConn) Read(func(uintptr) bool) error  { return nil }
func (protectionTestConn) Write(func(uintptr) bool) error { return nil }

var _ syscall.RawConn = protectionTestConn{}

func openProtectionTestSocket(t *testing.T) (*net.UDPConn, syscall.RawConn, int) {
	t.Helper()
	conn, err := net.ListenUDP("udp4", &net.UDPAddr{IP: net.IPv4(127, 0, 0, 1)})
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = conn.Close() })
	raw, err := conn.SyscallConn()
	if err != nil {
		t.Fatal(err)
	}
	fd := -1
	if err := raw.Control(func(value uintptr) { fd = int(value) }); err != nil {
		t.Fatal(err)
	}
	return conn, raw, fd
}

func TestSocketProtectionRunsBeforeListenerIsReady(t *testing.T) {
	var calls atomic.Int64
	gate := newSocketProtectionGate(func(fd int) bool { calls.Add(1); return fd == 42 })
	if err := protectRawSocket(protectionTestConn{}, gate); err != nil {
		t.Fatal(err)
	}
	gate.markReady()
	if err := protectRawSocket(protectionTestConn{}, gate); err != nil {
		t.Fatal(err)
	}
	snapshot := gate.snapshot()
	if calls.Load() != 2 || snapshot.Accepted != 2 || snapshot.CallsDuringStart != 1 || snapshot.Phase != "ready" {
		t.Fatalf("protection not active throughout startup: %+v calls=%d", snapshot, calls.Load())
	}
}

func TestSocketProtectionRejectionFailsTheSocketHook(t *testing.T) {
	gate := newSocketProtectionGate(func(int) bool { return false })
	if err := protectRawSocket(protectionTestConn{}, gate); !errors.Is(err, errSocketProtectionRejected) {
		t.Fatalf("error=%v", err)
	}
	controlError := errors.New("socket already closed")
	if err := protectRawSocket(protectionTestConn{controlError}, gate); !errors.Is(err, controlError) {
		t.Fatalf("error=%v", err)
	}
	if got := gate.snapshot(); got.Rejected != 1 || got.Accepted != 0 {
		t.Fatalf("counts=%+v", got)
	}
}

func TestSocketProtectionCloseWaitsAndRejectsLateSockets(t *testing.T) {
	entered, release, finished := make(chan struct{}), make(chan struct{}), make(chan struct{})
	gate := newSocketProtectionGate(func(int) bool { close(entered); <-release; return true })
	dialResult := make(chan error, 1)
	go func() { dialResult <- gate.protectSocket(42) }()
	<-entered
	closing := make(chan struct{})
	go func() { close(closing); gate.close(); close(finished) }()
	<-closing
	select {
	case <-finished:
		t.Fatal("closed while platform protection was in flight")
	case <-time.After(20 * time.Millisecond):
	}
	close(release)
	if err := <-dialResult; err != nil {
		t.Fatal(err)
	}
	<-finished
	if err := gate.protectSocket(43); !errors.Is(err, errSocketProtectionUnavailable) {
		t.Fatalf("late error=%v", err)
	}
	if got := gate.snapshot(); got.Phase != "closed" || got.Accepted != 1 || got.Unavailable != 1 {
		t.Fatalf("state=%+v", got)
	}
}

func TestSocketProtectionConcurrentDialsKeepRejections(t *testing.T) {
	gate := newSocketProtectionGate(func(fd int) bool { return fd%2 == 0 })
	var wg sync.WaitGroup
	for i := range 1000 {
		wg.Add(1)
		go func() { defer wg.Done(); _ = gate.protectSocket(i) }()
	}
	wg.Wait()
	got := gate.snapshot()
	if got.Accepted != 500 || got.Rejected != 500 || got.CallsDuringStart != 1000 {
		t.Fatalf("counts=%+v", got)
	}
}

func TestSocketOpenedBeforeVpnIsProtectedWhenCallbackActivates(t *testing.T) {
	pending := newPendingSocketProtector()
	var active atomic.Pointer[socketProtectionGate]
	hook := newSocketProtectionHook(func() error { return nil }, active.Load, pending)
	_, raw, originalFd := openProtectionTestSocket(t)
	if err := hook(raw); err != nil {
		t.Fatal(err)
	}
	if len(pending.pending) != 1 {
		t.Fatalf("held sockets=%d, want 1 before VPN activation", len(pending.pending))
	}

	protectedFd := -1
	gate := newSocketProtectionGate(func(fd int) bool { protectedFd = fd; return true })
	active.Store(gate)
	pending.activate(func(conn syscall.RawConn) error { return protectRawSocket(conn, gate) })
	gate.markReady()

	if protectedFd != originalFd {
		t.Fatalf("protected fd=%d, want the same pre-VPN socket fd=%d", protectedFd, originalFd)
	}
	if got := gate.snapshot(); got.Accepted != 1 || got.CallsDuringStart != 1 || got.Phase != "ready" {
		t.Fatalf("pre-VPN socket protection was not recorded during activation: %+v", got)
	}
	if len(pending.pending) != 0 {
		t.Fatalf("held sockets=%d after successful protection, want 0", len(pending.pending))
	}
}

func TestPendingSocketRetriesAfterVpnProtectionIsRejected(t *testing.T) {
	pending := newPendingSocketProtector()
	_, raw, _ := openProtectionTestSocket(t)
	if err := pending.protectOrHold(raw); err != nil {
		t.Fatal(err)
	}
	first := newSocketProtectionGate(func(int) bool { return false })
	pending.activate(func(conn syscall.RawConn) error { return protectRawSocket(conn, first) })
	if len(pending.pending) != 1 {
		t.Fatalf("held sockets=%d after a rejected activation, want 1", len(pending.pending))
	}

	protected := false
	second := newSocketProtectionGate(func(int) bool { protected = true; return true })
	pending.activate(func(conn syscall.RawConn) error { return protectRawSocket(conn, second) })
	if !protected || len(pending.pending) != 0 || second.snapshot().Accepted != 1 {
		t.Fatalf("next VPN did not protect pending socket: protected=%v held=%d state=%+v", protected, len(pending.pending), second.snapshot())
	}
}

func TestPendingSocketProtectionDropsClosedSocket(t *testing.T) {
	pending := newPendingSocketProtector()
	conn, raw, _ := openProtectionTestSocket(t)
	if err := pending.protectOrHold(raw); err != nil {
		t.Fatal(err)
	}
	if err := conn.Close(); err != nil {
		t.Fatal(err)
	}
	gate := newSocketProtectionGate(func(int) bool { return true })
	pending.activate(func(value syscall.RawConn) error { return protectRawSocket(value, gate) })
	if len(pending.pending) != 0 || gate.snapshot().Accepted != 0 {
		t.Fatalf("closed socket was retained or protected: held=%d state=%+v", len(pending.pending), gate.snapshot())
	}
}

func TestConcurrentSocketCreationAndVpnActivationProtectEverySocket(t *testing.T) {
	const sockets = 64
	pending := newPendingSocketProtector()
	var active atomic.Pointer[socketProtectionGate]
	var protected atomic.Int64
	hook := newSocketProtectionHook(func() error { return nil }, active.Load, pending)
	gate := newSocketProtectionGate(func(int) bool { protected.Add(1); return true })
	var workers sync.WaitGroup
	for range sockets {
		workers.Add(1)
		go func() {
			defer workers.Done()
			if err := hook(protectionTestConn{}); err != nil {
				t.Errorf("protect socket around activation: %v", err)
			}
		}()
	}
	active.Store(gate)
	pending.activate(func(conn syscall.RawConn) error { return protectRawSocket(conn, gate) })
	workers.Wait()
	if protected.Load() != sockets || gate.snapshot().Accepted != sockets {
		t.Fatalf("protected=%d state=%+v; want %d sockets", protected.Load(), gate.snapshot(), sockets)
	}
}
