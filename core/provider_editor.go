package core

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"io"
	"os"
	"path/filepath"
	"sync"
	"unicode/utf8"

	cp "github.com/metacubex/mihomo/constant/provider"
	rp "github.com/metacubex/mihomo/rules/provider"
)

const providerDocumentLimit = 8 * 1024 * 1024

// 作者 long：文档只返回可显示正文和版本指纹，磁盘路径留在核心中，不能由页面指定写入目标。
type ProviderDocument struct {
	Name    string `json:"name"`
	Kind    string `json:"kind"`
	Content string `json:"content"`
	Digest  string `json:"digest"`
}

type ScopedProviderSideLoad struct {
	ScopedProviderUpdate
	Data   string `json:"data"`
	Digest string `json:"digest"`
}

type ScopedProvidersUpdate struct {
	ScopedProviderUpdate
	Names []string `json:"names"`
}

type ProviderUpdateResult struct {
	Name string `json:"name"`
	Code string `json:"code"`
}

func scopedProvider(params ScopedProviderUpdate) (cp.Provider, error) {
	epoch, version := routeStamp()
	if epoch != params.CoreEpoch || version != params.RouteVersion {
		return nil, providerError("provider_stale", params.Name, errors.New("provider configuration changed"))
	}
	p, ok := lookupExternalProvider(params.Name)
	if !ok {
		return nil, providerError("provider_not_found", params.Name, errors.New("provider does not exist"))
	}
	return p, nil
}

func readProviderBytes(path string) ([]byte, string, error) {
	file, err := os.Open(path)
	if errors.Is(err, os.ErrNotExist) {
		return nil, "missing", nil
	}
	if err != nil {
		return nil, "", err
	}
	defer file.Close()
	data, err := io.ReadAll(io.LimitReader(file, providerDocumentLimit+1))
	if err != nil {
		return nil, "", err
	}
	if len(data) > providerDocumentLimit {
		return nil, "", errors.New("provider file exceeds limit")
	}
	sum := sha256.Sum256(data)
	return data, hex.EncodeToString(sum[:]), nil
}

type providerTextWriter struct {
	bytes.Buffer
	exceeded bool
}

func (w *providerTextWriter) Write(data []byte) (int, error) {
	if w.Len()+len(data) > providerDocumentLimit {
		w.exceeded = true
		return 0, errors.New("provider text exceeds limit")
	}
	return w.Buffer.Write(data)
}

func handleReadExternalProviderScoped(params ScopedProviderUpdate) (*ProviderDocument, error) {
	configMu.Lock()
	defer configMu.Unlock()
	p, err := scopedProvider(params)
	if err != nil {
		return nil, err
	}
	meta, err := toExternalProvider(p)
	if err != nil || meta.Path == "" {
		return nil, providerError("provider_file_error", params.Name, errors.New("provider file unavailable"))
	}
	data, digest, err := readProviderBytes(meta.Path)
	if err != nil {
		return nil, providerError("provider_file_error", params.Name, err)
	}
	doc := &ProviderDocument{Name: params.Name, Kind: "binary", Digest: digest}
	if bytes.HasPrefix(data, []byte{0x28, 0xb5, 0x2f, 0xfd}) && meta.Type == "Rule" {
		for _, behavior := range []cp.RuleBehavior{cp.Domain, cp.IPCIDR} {
			out := &providerTextWriter{}
			if err = rp.ConvertToMrs(data, behavior, cp.MrsRule, out); err == nil {
				if out.exceeded {
					return nil, providerError("provider_file_error", params.Name, errors.New("provider text exceeds limit"))
				}
				doc.Kind, doc.Content = "mrs", out.String()
				return doc, nil
			}
		}
		return nil, providerError("provider_file_error", params.Name, errors.New("cannot preview rule set"))
	}
	if utf8.Valid(data) && !bytes.ContainsRune(data, 0) {
		doc.Kind, doc.Content = "text", string(data)
	}
	return doc, nil
}

func handleSideLoadExternalProviderScoped(params ScopedProviderSideLoad) error {
	if len(params.Data) > providerDocumentLimit || !utf8.ValidString(params.Data) || bytes.ContainsRune([]byte(params.Data), 0) {
		return providerError("provider_content_error", params.Name, errors.New("provider requires bounded UTF-8 text"))
	}
	configMu.Lock()
	defer configMu.Unlock()
	p, err := scopedProvider(params.ScopedProviderUpdate)
	if err != nil {
		return err
	}
	meta, err := toExternalProvider(p)
	if err != nil || meta.Path == "" {
		return providerError("provider_file_error", params.Name, errors.New("provider file unavailable"))
	}
	key := providerUpdateScope + params.Name
	if !claimUpdate(key) {
		return providerError("provider_updating", params.Name, errors.New("provider is updating"))
	}
	defer releaseUpdate(key)
	before, digest, err := readProviderBytes(meta.Path)
	if err != nil {
		return providerError("provider_file_error", params.Name, err)
	}
	if digest != params.Digest {
		return providerError("provider_stale", params.Name, errors.New("provider file changed"))
	}
	// 作者 long：Mihomo先校验再写缓存，但文件写失败可能留下半截正文；预先备份保证失败仍能恢复原字节和时间。
	backup := ""
	keepBackup := false
	if digest != "missing" {
		info, statErr := os.Stat(meta.Path)
		if statErr != nil {
			return providerError("provider_file_error", params.Name, statErr)
		}
		file, createErr := os.CreateTemp(filepath.Dir(meta.Path), ".provider-rollback-")
		if createErr != nil {
			return providerError("provider_file_error", params.Name, createErr)
		}
		backup = file.Name()
		defer func() {
			if !keepBackup {
				os.Remove(backup)
			}
		}()
		_, writeErr := file.Write(before)
		closeErr := file.Close()
		if writeErr != nil || closeErr != nil {
			return providerError("provider_file_error", params.Name, errors.Join(writeErr, closeErr))
		}
		if err = os.Chmod(backup, info.Mode().Perm()); err != nil {
			return providerError("provider_file_error", params.Name, err)
		}
		if err = os.Chtimes(backup, info.ModTime(), info.ModTime()); err != nil {
			return providerError("provider_file_error", params.Name, err)
		}
	}
	if err = sideUpdateExternalProvider(p, []byte(params.Data)); err != nil {
		if backup != "" {
			restoreErr := os.Rename(backup, meta.Path)
			// 作者 long：恢复失败时保留备份供后续恢复，不能在defer中删除唯一完整副本。
			keepBackup = restoreErr != nil
			err = errors.Join(err, restoreErr)
		} else {
			// 作者 long：首次侧载失败只回收本调用刚创建的目标，原先不存在的缓存不能伪装成有效资源。
			if removeErr := os.Remove(meta.Path); removeErr != nil && !errors.Is(removeErr, os.ErrNotExist) {
				err = errors.Join(err, removeErr)
			}
		}
		return providerError("provider_content_error", params.Name, err)
	}
	refreshRoute()
	return nil
}

func handleUpdateExternalProvidersScoped(params ScopedProvidersUpdate) ([]ProviderUpdateResult, error) {
	if len(params.Names) == 0 || len(params.Names) > 512 {
		return nil, providerError("provider_content_error", "", errors.New("invalid provider batch size"))
	}
	configMu.Lock()
	epoch, version := routeStamp()
	if epoch != params.CoreEpoch || version != params.RouteVersion {
		configMu.Unlock()
		return nil, providerError("provider_stale", "", errors.New("provider configuration changed"))
	}
	providers := make([]cp.Provider, len(params.Names))
	seen := map[string]bool{}
	for i, name := range params.Names {
		p, ok := lookupExternalProvider(name)
		if !ok || p.VehicleType() != cp.HTTP || seen[name] {
			configMu.Unlock()
			return nil, providerError("provider_not_found", name, errors.New("provider unavailable for update"))
		}
		seen[name] = true
		providers[i] = p
	}
	configMu.Unlock()
	// 作者 long：整批先冻结同一配置的Provider对象，再在配置锁外并行下载；节点切换和停止不等待网络。
	results := make([]ProviderUpdateResult, len(providers))
	var tasks sync.WaitGroup
	for i, p := range providers {
		tasks.Go(func() {
			err := updateResolvedExternalProvider(params.Names[i], p, func(item cp.Provider) *MethodError {
				if updateErr := item.Update(); updateErr != nil {
					return providerUpdateError(params.Names[i], updateErr)
				}
				return nil
			})
			code := ""
			if err != nil {
				code = asMethodError(err).Code
			}
			results[i] = ProviderUpdateResult{Name: params.Names[i], Code: code}
		})
	}
	tasks.Wait()
	return results, nil
}
