package api

import (
	"fmt"
	"io"
	"net/http"
	"strings"
	"sync"
	"time"
)

// UnauthorizedHandler 在收到 401 时负责刷新 token。
type UnauthorizedHandler func(invalidatedToken string) (string, error)

// Client 封装 HTTP 客户端，自动处理认证和重试
type Client struct {
	HTTPClient          *http.Client
	Token               string
	mu                  sync.RWMutex
	unauthorizedHandler UnauthorizedHandler
}

// NewClient 创建新的 API 客户端
func NewClient(token string) *Client {
	return &Client{
		HTTPClient: &http.Client{
			Timeout: 60 * time.Second,
		},
		Token: token,
	}
}

// SetUnauthorizedHandler 设置 401 自动恢复回调。
func (c *Client) SetUnauthorizedHandler(handler UnauthorizedHandler) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.unauthorizedHandler = handler
}

// CurrentToken 返回当前 token。
func (c *Client) CurrentToken() string {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.Token
}

func (c *Client) setToken(token string) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.Token = token
}

func (c *Client) unauthorizedRecovery() UnauthorizedHandler {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.unauthorizedHandler
}

// GetJSON 执行 GET 请求并返回响应体
// 自动添加认证头，5xx 错误自动重试（最多 3 次）
func (c *Client) GetJSON(url string) ([]byte, error) {
	var lastErr error
	for attempt := 0; attempt < 3; attempt++ {
		if attempt > 0 {
			time.Sleep(time.Duration(attempt) * time.Second)
		}

		resp, err := c.doGet(url, true)
		if err != nil {
			lastErr = fmt.Errorf("请求失败: %w", err)
			continue
		}

		body, err := io.ReadAll(resp.Body)
		resp.Body.Close()
		if err != nil {
			lastErr = fmt.Errorf("读取响应失败: %w", err)
			continue
		}

		if resp.StatusCode >= 500 {
			lastErr = fmt.Errorf("服务器错误 %d: %s", resp.StatusCode, string(body[:capMsg(body)]))
			continue
		}

		if resp.StatusCode != 200 {
			return nil, fmt.Errorf("HTTP %d: %s", resp.StatusCode, string(body[:capMsg(body)]))
		}

		return body, nil
	}
	return nil, lastErr
}

// capMsg 错误消息截断长度（≤200），兼容 go1.20：无内置 min。
func capMsg(b []byte) int {
	if len(b) < 200 {
		return len(b)
	}
	return 200
}

// DownloadStream 执行 GET 请求并返回响应体流（用于大文件下载）
func (c *Client) DownloadStream(url string) (io.ReadCloser, int64, error) {
	resp, err := c.doGet(url, true)
	if err != nil {
		return nil, 0, fmt.Errorf("请求失败: %w", err)
	}

	if resp.StatusCode != 200 {
		resp.Body.Close()
		return nil, 0, fmt.Errorf("HTTP %d", resp.StatusCode)
	}

	return resp.Body, resp.ContentLength, nil
}

func (c *Client) doGet(url string, allowRefresh bool) (*http.Response, error) {
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return nil, fmt.Errorf("创建请求失败: %w", err)
	}

	token := c.CurrentToken()
	if token != "" {
		req.Header.Set("X-ND-AUTH", FormatAuthHeader(token))
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return nil, err
	}

	if resp.StatusCode != http.StatusUnauthorized || !allowRefresh {
		return resp, nil
	}

	handler := c.unauthorizedRecovery()
	if handler == nil {
		return resp, nil
	}

	resp.Body.Close()

	newToken, err := handler(token)
	if err != nil {
		return nil, fmt.Errorf("登录状态已失效，刷新失败: %w", err)
	}
	if newToken == "" {
		return nil, fmt.Errorf("登录状态已失效，刷新后仍未获得 token")
	}

	c.setToken(newToken)
	return c.doGet(url, false)
}

// FormatAuthHeader 格式化认证头
func FormatAuthHeader(token string) string {
	return fmt.Sprintf(`MAC id="%s",nonce="0",mac="0"`, token)
}

// ResolveCDNURL 将 CDN 域名模板解析为完整 URL
// ti_storage 格式: "cs_path:${ref-path}/edu_product/esp/assets/..."
// 需要替换 "cs_path:${ref-path}" 为实际的 CDN 基地址
func ResolveCDNURL(template string, hasToken bool) string {
	cdn := PublicCDN
	if hasToken {
		cdn = TokenCDN
	}
	// 替换 cs_path:${ref-path} 为 CDN 地址
	result := strings.ReplaceAll(template, "cs_path:${ref-path}", cdn)
	// 也处理只有 ${ref-path} 的情况
	result = strings.ReplaceAll(result, "${ref-path}", cdn)
	return result
}
