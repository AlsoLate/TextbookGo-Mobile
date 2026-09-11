package auth

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"time"
)

// TokenFile token 文件名
const TokenFile = "token.json"

// tokenData token 存储结构
type tokenData struct {
	Token     string `json:"token"`
	SavedAt   string `json:"saved_at"`
	UpdatedAt string `json:"updated_at"`
}

// getConfigDir 获取配置目录
func getConfigDir() (string, error) {
	home, err := os.UserHomeDir()
	if err != nil {
		return "", err
	}

	switch runtime.GOOS {
	case "windows":
		return filepath.Join(home, ".config", "BoooookDown"), nil
	default:
		return filepath.Join(home, ".config", "BoooookDown"), nil
	}
}

// GetToken 获取 token，优先从环境变量，其次从配置文件
func GetToken() (string, error) {
	// 1. 环境变量
	if token := os.Getenv("SMARTEDU_TOKEN"); token != "" {
		return token, nil
	}

	// 2. 配置文件
	return LoadToken()
}

// ForgetToken 删除已保存的 token 文件（“不记住登录状态”）。
// 内存中的当前会话不受影响；下次启动服务需重新扫码登录。
func ForgetToken() error {
	dir, err := getConfigDir()
	if err != nil {
		return fmt.Errorf("获取配置目录失败: %w", err)
	}
	if err := os.Remove(filepath.Join(dir, TokenFile)); err != nil && !os.IsNotExist(err) {
		return fmt.Errorf("删除 token 文件失败: %w", err)
	}
	return nil
}

// SaveToken 保存 token 到配置文件
func SaveToken(token string) error {
	dir, err := getConfigDir()
	if err != nil {
		return fmt.Errorf("获取配置目录失败: %w", err)
	}

	if err := os.MkdirAll(dir, 0755); err != nil {
		return fmt.Errorf("创建配置目录失败: %w", err)
	}

	data := tokenData{
		Token:     token,
		SavedAt:   time.Now().Format(time.RFC3339),
		UpdatedAt: time.Now().Format(time.RFC3339),
	}

	jsonData, err := json.MarshalIndent(data, "", "  ")
	if err != nil {
		return fmt.Errorf("序列化 token 失败: %w", err)
	}

	path := filepath.Join(dir, TokenFile)
	if err := os.WriteFile(path, jsonData, 0600); err != nil {
		return fmt.Errorf("写入 token 文件失败: %w", err)
	}

	return nil
}

// LoadToken 从配置文件读取 token
func LoadToken() (string, error) {
	dir, err := getConfigDir()
	if err != nil {
		return "", err
	}

	path := filepath.Join(dir, TokenFile)
	data, err := os.ReadFile(path)
	if err != nil {
		if os.IsNotExist(err) {
			return "", nil
		}
		return "", fmt.Errorf("读取 token 文件失败: %w", err)
	}

	var td tokenData
	if err := json.Unmarshal(data, &td); err != nil {
		return "", fmt.Errorf("解析 token 文件失败: %w", err)
	}

	return td.Token, nil
}

// DeleteToken 删除保存的 token
func DeleteToken() error {
	dir, err := getConfigDir()
	if err != nil {
		return err
	}
	path := filepath.Join(dir, TokenFile)
	if err := os.Remove(path); err != nil && !os.IsNotExist(err) {
		return err
	}
	return nil
}
