//go:build android

// Every callback returns an error so that gobind clears a Java exception and
// hands it over as that error; a method without one leaves the exception
// pending, and the next JNI call on that thread aborts the process.
package mobile

import "core"

type ResultHandler interface {
	OnResult(data []byte) error
}

type TunHandler interface {
	Protect(fd int32) (bool, error)
	ResolveUid(protocol int32, source, target string) (int32, error)
	ResolvePackage(uid int32) (string, error)
}

type tunCallbacks struct {
	handler TunHandler
}

func (c tunCallbacks) Protect(fd int) bool {
	protected, err := c.handler.Protect(int32(fd))
	return err == nil && protected
}

func (c tunCallbacks) ResolveUid(protocol int, source, target string) int {
	uid, err := c.handler.ResolveUid(int32(protocol), source, target)
	if err != nil {
		return -1
	}
	return int(uid)
}

func (c tunCallbacks) ResolvePackage(uid int) string {
	packageName, err := c.handler.ResolvePackage(int32(uid))
	if err != nil {
		return ""
	}
	return packageName
}

func InvokeMethod(data string, handler ResultHandler) {
	core.InvokeMethod(data, func(result []byte) {
		_ = handler.OnResult(result)
	})
}

func StartTun(fd int32, handler TunHandler, stack, address, dns, options string) bool {
	return core.StartTun(tunCallbacks{handler}, int(fd), stack, address, dns, options)
}

func QuickSetup(initParams, setupParams string, handler ResultHandler) {
	core.QuickSetup(initParams, setupParams, func(message string) {
		_ = handler.OnResult([]byte(message))
	})
}

func SetEventListener(handler ResultHandler) {
	if handler == nil {
		core.SetEventListener(nil)
		return
	}
	core.SetEventListener(func(data []byte) {
		_ = handler.OnResult(data)
	})
}

func StopTun() {
	core.StopTun()
}

func StartListener() {
	core.StartListener()
}

func StopListener() {
	core.StopListener()
}

func ForceGC() {
	core.ForceGC()
}

func UpdateDns(value string) {
	core.UpdateDns(value)
}

func ResetNetwork() {
	core.ResetNetwork()
}

func NetworkBootstrapSnapshot() string {
	return core.NetworkBootstrapSnapshot()
}

func BeginDialDiagnostic(sourcePort int32) { core.BeginDialDiagnostic(int(sourcePort)) }

func DialDiagnosticSnapshot(sourcePort int32, finish bool) string {
	return core.DialDiagnosticSnapshot(int(sourcePort), finish)
}

func GetTraffic(onlyStatisticsProxy bool) string {
	return core.GetTraffic(onlyStatisticsProxy)
}

func GetTotalTraffic(onlyStatisticsProxy bool) string {
	return core.GetTotalTraffic(onlyStatisticsProxy)
}

// 作者 long：候选字节与只读资源FD只交给独立UID一次性校验，返回内容不含原始核心异常。
func ValidateConfigurationIsolated(content []byte, resources string) string {
	return core.ValidateConfigurationIsolated(content, resources)
}
