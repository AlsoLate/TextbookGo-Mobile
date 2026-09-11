// Command boooookdown-web 启动「下书」网页版：
// 内嵌前端 + JSON API + SSE 下载进度。
package main

import (
	"flag"
	"fmt"
	"io/fs"
	"net"
	"net/http"
	"os"
	"os/exec"
	"runtime"

	"github.com/Wing900/TextbookGo/internal/app"
	"github.com/Wing900/TextbookGo/internal/server"
	"github.com/Wing900/TextbookGo/internal/webapp"
	"github.com/Wing900/TextbookGo/webui"
)

// version 由发布脚本注入（-ldflags "-X main.version=..."）。
var version = "dev"

func main() {
	addr := flag.String("addr", "", "监听地址（默认 127.0.0.1:8790，被占用时自动顺延至 8800）")
	out := flag.String("out", "", "下载目录（默认可执行文件旁的 books）")
	flag.Parse()

	dir, err := resolveDir(*out)
	if err != nil {
		failHold("解析输出目录失败: %v", err)
	}
	svc := app.NewService(dir)
	static, err := fs.Sub(webui.FS, "frontend")
	if err != nil {
		failHold("加载前端资源失败: %v", err)
	}
	srv := server.New(webapp.New(svc), static)

	ln, err := listen(*addr)
	if err != nil {
		failHold("端口监听失败: %v", err)
	}
	actual := ln.Addr().String()
	fmt.Printf("TextbookGo · 下书 v%s 已启动\n  访问: http://%s\n  下载目录: %s\n", version, actual, dir)
	openBrowser("http://" + actual) // 双击即用：自动弹出浏览器
	if err := http.Serve(ln, srv.Handler()); err != nil {
		failHold("服务异常退出: %v", err)
	}
}

// listen：显式指定地址就严格用它；未指定则从 8790 起自动找空位（至 8800）。
// 双击场景默认无参，端口被占时顺延而不是闪退。
func listen(addr string) (net.Listener, error) {
	if addr != "" {
		return net.Listen("tcp", addr)
	}
	for port := 8790; port <= 8800; port++ {
		ln, err := net.Listen("tcp", fmt.Sprintf("127.0.0.1:%d", port))
		if err == nil {
			return ln, nil
		}
	}
	return nil, fmt.Errorf("127.0.0.1 的 8790-8800 端口全部被占用")
}

// failHold 双击运行的致命错误不闪退：打印原因后等回车，让用户看得见死因。
func failHold(format string, args ...interface{}) {
	fmt.Printf("\n启动失败: "+format+"\n\n按回车键退出...", args...)
	var s string
	fmt.Scanln(&s)
	os.Exit(1)
}

// openBrowser 跨平台尽力打开默认浏览器；失败静默（服务器仍可用，手动访问打印的地址）。
func openBrowser(url string) {
	var cmd *exec.Cmd
	switch runtime.GOOS {
	case "windows":
		cmd = exec.Command("rundll32", "url,OpenURL", url)
	case "darwin":
		cmd = exec.Command("open", url)
	default:
		cmd = exec.Command("xdg-open", url)
	}
	_ = cmd.Start()
}

func resolveDir(out string) (string, error) {
	var args []string
	if out != "" {
		args = []string{out}
	}
	return app.ResolveOutputDir(args)
}
