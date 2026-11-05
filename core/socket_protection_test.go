package core

import (
	"errors"
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
