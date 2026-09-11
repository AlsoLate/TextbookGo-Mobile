package auth

import (
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"time"

	"github.com/Wing900/TextbookGo/internal/util"
	"github.com/chromedp/chromedp"
)

const (
	smarteduLoginURL = "https://auth.smartedu.cn/uias/login"
)

// LoginViaBrowser 全自动 Token 获取
// 1. 10 秒倒计时（告知用户即将打开浏览器）
// 2. 启动 Chrome（临时 profile），导航到登录页
// 3. 用户登录后，通过 CDP 自动读取 localStorage 获取 Token
// 4. 断开 CDP，保存 Token
func LoginViaBrowser() (string, error) {
	return loginViaBrowser(os.Stdout, os.Stderr)
}

// LoginViaBrowserQuiet 在不向终端输出提示的情况下完成登录流程。
func LoginViaBrowserQuiet() (string, error) {
	return loginViaBrowser(io.Discard, io.Discard)
}

func loginViaBrowser(stdout io.Writer, stderr io.Writer) (string, error) {
	fmt.Fprintln(stdout)
	fmt.Fprintln(stdout, util.Header("=== 登录国家智慧教育平台 ==="))
	fmt.Fprintln(stdout)

	// 10 秒倒计时
	fmt.Fprintln(stdout, "  提示：10秒后会自动打开浏览器，请准备登录您的账号")
	fmt.Fprintln(stdout)
	for i := 10; i > 0; i-- {
		fmt.Fprintf(stdout, "\r  %d 秒后打开浏览器，请准备登录账号.. ", i)
		time.Sleep(1 * time.Second)
	}
	fmt.Fprintln(stdout)
	fmt.Fprintln(stdout)

	// 创建临时 Chrome profile 目录
	tmpDir, err := os.MkdirTemp("", "TextbookGo-chrome-*")
	if err != nil {
		return "", fmt.Errorf("创建临时目录失败: %w", err)
	}
	defer os.RemoveAll(tmpDir) // 清理临时目录

	// 查找 Chrome 可执行文件
	chromePath := findChrome()
	if chromePath == "" {
		return "", fmt.Errorf("未找到 Chrome，请先安装 Google Chrome")
	}

	fmt.Fprintln(stdout, "  浏览器已打开，请在浏览器中登录您的账号")
	fmt.Fprintln(stdout, "  登录成功后将自动获取 Token，无需任何手动操作")
	fmt.Fprintln(stdout)
	fmt.Fprintln(stdout, util.Dim("  等待登录中.. (5 分钟超时)"))
	fmt.Fprintln(stdout)

	// 如果 Chrome 已运行，提示关闭（避免进程冲突）
	if isChromeRunning() {
		fmt.Fprintln(stdout, util.Warn("  检测到 Chrome 已在运行，建议关闭后重试以避免冲突"))
	}

	// 设置 chromedp（非 headless 模式）
	opts := append(chromedp.DefaultExecAllocatorOptions[:],
		chromedp.ExecPath(chromePath),
		chromedp.UserDataDir(tmpDir),
		chromedp.Flag("headless", false),
		chromedp.Flag("enable-automation", false),
		chromedp.Flag("disable-blink-features", "AutomationControlled"),
		chromedp.Flag("disable-background-networking", true),
		chromedp.Flag("disable-background-timer-throttling", true),
		chromedp.Flag("disable-component-update", true),
		chromedp.Flag("no-first-run", true),
		chromedp.Flag("no-default-browser-check", true),
		chromedp.Flag("disable-default-apps", true),
		chromedp.Flag("disable-extensions", true),
		chromedp.Flag("disable-popup-blocking", true),
		chromedp.Flag("disable-sync", true),
		chromedp.Flag("metrics-recording-only", true),
		chromedp.Flag("mute-audio", true),
		chromedp.Flag("remote-allow-origins", "*"),
	)

	allocCtx, allocCancel := chromedp.NewExecAllocator(context.Background(), opts...)
	defer allocCancel()

	ctx, cancel := chromedp.NewContext(allocCtx)
	defer cancel()

	// 设置总超时
	ctx, cancel = context.WithTimeout(ctx, 5*time.Minute)
	defer cancel()

	// 导航到登录页
	if err := chromedp.Run(ctx, chromedp.Navigate(smarteduLoginURL)); err != nil {
		return "", fmt.Errorf("打开登录页失败: %w", err)
	}

	// 轮询 localStorage 直到找到 token
	token, err := pollForToken(ctx)
	if err != nil {
		return "", err
	}

	fmt.Fprintln(stdout, util.Success("  ✓ Token 获取成功!"))

	// 保存 token
	if err := SaveToken(token); err != nil {
		fmt.Fprintf(stderr, "  警告: 保存 token 失败: %v\n", err)
	}

	return token, nil
}

// pollForToken 轮询 localStorage 检查 ND_UC_AUTH
// 使用 chromedp.Evaluate 执行 JS 读取 localStorage
func pollForToken(ctx context.Context) (string, error) {
	jsExtract := `(function() {
		var keys = Object.keys(localStorage);
		for (var i = 0; i < keys.length; i++) {
			if (keys[i].indexOf("ND_UC_AUTH") === 0) {
				try {
					var data = JSON.parse(localStorage.getItem(keys[i]));
					var value = JSON.parse(data.value);
					if (value && value.access_token) {
						return value.access_token;
					}
				} catch(e) {}
			}
		}
		return "";
	})()`

	ticker := time.NewTicker(2 * time.Second)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return "", fmt.Errorf("等待超时，请重试")
		case <-ticker.C:
			var token string
			err := chromedp.Run(ctx, chromedp.Evaluate(jsExtract, &token))
			if err != nil {
				// 超时或上下文取消 → 退出
				if errors.Is(err, context.DeadlineExceeded) || errors.Is(err, context.Canceled) {
					return "", fmt.Errorf("登录超时或已取消: %w", err)
				}
				// 页面加载中 → 继续等待
				continue
			}
			if token != "" {
				return token, nil
			}
		}
	}
}

// findChrome 查找 Chrome 可执行文件路径
func findChrome() string {
	// 先检查环境变量
	if p := os.Getenv("CHROME_PATH"); p != "" {
		if _, err := os.Stat(p); err == nil {
			return p
		}
	}

	switch runtime.GOOS {
	case "windows":
		paths := []string{
			filepath.Join(os.Getenv("PROGRAMFILES"), "Google", "Chrome", "Application", "chrome.exe"),
			filepath.Join(os.Getenv("PROGRAMFILES(X86)"), "Google", "Chrome", "Application", "chrome.exe"),
			filepath.Join(os.Getenv("LOCALAPPDATA"), "Google", "Chrome", "Application", "chrome.exe"),
		}
		for _, p := range paths {
			if _, err := os.Stat(p); err == nil {
				return p
			}
		}
	case "darwin":
		p := "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
		if _, err := os.Stat(p); err == nil {
			return p
		}
	default:
		// Linux
		for _, name := range []string{"google-chrome", "google-chrome-stable", "chromium-browser", "chromium"} {
			if p, err := exec.LookPath(name); err == nil {
				return p
			}
		}
	}

	return ""
}

// isChromeRunning 检查 Chrome 是否正在运行（Windows）
func isChromeRunning() bool {
	if runtime.GOOS != "windows" {
		return false
	}

	cmd := exec.Command("tasklist", "/FI", "IMAGENAME eq chrome.exe")
	output, err := cmd.Output()
	if err != nil {
		return false
	}

	return strings.Contains(string(output), "chrome.exe")
}
