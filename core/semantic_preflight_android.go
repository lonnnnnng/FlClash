//go:build android

package core

import (
	"encoding/json"
	"errors"
	"io"
	"os"
	"path/filepath"
	"strings"
	"syscall"

	"github.com/metacubex/mihomo/config"
	"golang.org/x/sys/unix"
)

// 作者 long：只对Android独立隔离UID开放完整解析，不能在主核心里设置预检全局或复用前次解析缓存。
func ValidateConfigurationIsolated(content []byte, resourceJSON string) string {
	failure := `{"passed":false,"code":"not_isolated","section":"config","index":-1,"deferredResources":0}`
	if syscall.Getuid()%100000 < 90000 {
		return failure
	}
	if len(resourceJSON) > 65536 {
		return failure
	}
	var inputs []struct {
		Name  string `json:"name"`
		FD    int    `json:"fd"`
		Bytes int64  `json:"bytes"`
	}
	if json.Unmarshal([]byte(resourceJSON), &inputs) != nil || len(inputs) > 64 {
		return failure
	}
	files := map[string]*os.File{}
	var total int64
	defer func() {
		for _, file := range files {
			file.Close()
		}
	}()
	for _, input := range inputs {
		if input.Name == "" || len(input.Name) > 4096 || input.Bytes < 1 || input.Bytes > 64*1024*1024 || input.FD < 0 {
			return failure
		}
		if _, exists := files[input.Name]; exists {
			return failure
		}
		flags, err := unix.FcntlInt(uintptr(input.FD), unix.F_GETFL, 0)
		if err != nil || flags&unix.O_ACCMODE != unix.O_RDONLY {
			return failure
		}
		fd, err := syscall.Dup(input.FD)
		if err != nil {
			return failure
		}
		file := os.NewFile(uintptr(fd), "preflight-resource")
		info, err := file.Stat()
		if err != nil || !info.Mode().IsRegular() || info.Size() != input.Bytes {
			file.Close()
			return failure
		}
		files[input.Name] = file
		total += input.Bytes
		if total > 128*1024*1024 {
			return failure
		}
	}
	result := config.ParseReadOnly(content, func(name string) ([]byte, error) {
		// 作者 long：相对依赖在虚拟home中解析，只能匹配主进程显式授予的名字，绝不按候选路径任意打开文件。
		lookup := name
		if strings.HasPrefix(name, "/kclash-validation/") {
			lookup = strings.TrimPrefix(name, "/kclash-validation/")
		}
		file := files[lookup]
		if file == nil {
			file = files[filepath.Base(name)]
		}
		// Geo允许固定文件名别名，普通证书/Provider路径不能靠basename匹配另一份同名资源。
		if file != nil && files[lookup] == nil {
			switch filepath.Base(name) {
			case "GEOIP.dat", "GEOIP.metadb", "GEOSITE.dat", "ASN.mmdb":
			default:
				file = nil
			}
		}
		if file == nil {
			return nil, errors.New("resource not granted")
		}
		info, err := file.Stat()
		if err != nil {
			return nil, err
		}
		return io.ReadAll(io.NewSectionReader(file, 0, info.Size()))
	})
	bytes, err := json.Marshal(result)
	if err != nil {
		return failure
	}
	return string(bytes)
}
