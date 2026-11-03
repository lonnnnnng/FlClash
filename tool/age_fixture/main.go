package main

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"flag"
	"fmt"
	"os"
	"path/filepath"

	"github.com/metacubex/mihomo/component/age"
)

func main() {
	input := flag.String("input", "", "授权原始 1.yaml 路径")
	output := flag.String("output", "", "尚不存在的独占输出目录")
	expected := flag.String("sha256", "", "原文件摘要")
	name := flag.String("name", "", "系统选择器中的密文文件名")
	flag.Parse()
	data, err := os.ReadFile(*input)
	if err != nil || filepath.Base(*input) != "1.yaml" || hash(data) != *expected || *expected == "" {
		fail("授权原文件或摘要不匹配")
	}
	if filepath.Base(*name) != *name || filepath.Ext(*name) != ".age" {
		fail("密文文件名无效")
	}
	key, recipient, err := age.GenX25519KeyPair()
	if err != nil {
		fail("生成临时 identity 失败")
	}
	wrongKey, wrongRecipient, err := age.GenX25519KeyPair()
	if err != nil {
		fail("生成错误密钥夹具失败")
	}
	ciphertext, err := age.EncryptBytes(data, recipient)
	if err != nil {
		fail("加密授权原文件失败")
	}
	other, err := age.EncryptBytes(data, wrongRecipient)
	if err != nil {
		fail("加密错误接收者夹具失败")
	}
	// 作者 long：只加密获准原文件的完整字节，不生成或改写 YAML；identity 仅写入独占目录，不进入输出或提交。
	if os.Mkdir(*output, 0700) != nil {
		fail("输出目录已存在或不可创建")
	}
	manifest, _ := json.Marshal(map[string]any{
		"key": key, "wrongKey": wrongKey, "plaintextSha256": hash(data), "ciphertextSha256": hash(ciphertext),
		"otherCiphertextSha256": hash(other), "fileName": *name,
	})
	for name, value := range map[string][]byte{*name: ciphertext, "other.age": other, "manifest.json": manifest} {
		if os.WriteFile(filepath.Join(*output, name), value, 0600) != nil {
			fail("写入独占夹具失败")
		}
	}
	fmt.Println("授权原文件的真实 age 夹具已就绪")
}

func hash(data []byte) string { sum := sha256.Sum256(data); return hex.EncodeToString(sum[:]) }
func fail(message string)     { fmt.Fprintln(os.Stderr, message); os.Exit(1) }
