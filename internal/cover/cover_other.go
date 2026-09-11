//go:build !windows

// Package cover：非 Windows 平台的封面渲染降级实现。
// pdfium 仅内嵌了 win 版二进制（见 cover_windows.go），此处 Render 直接返回错误；
// 前端 cover 组件对图片 onerror 已有兜底——隐藏 img 后莫兰迪拼贴自然透出，
// 书架在 mac/Linux 上以拼贴封面呈现，下载等功能完全不受影响。
package cover

import "errors"

var errUnsupported = errors.New("本平台暂不支持 PDF 封面渲染，使用拼贴封面")

// Render 非 Windows 平台不可用，返回错误由调用方降级处理。
func Render([]byte) ([]byte, error) { return nil, errUnsupported }