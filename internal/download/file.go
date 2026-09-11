package download

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
	"time"

	"github.com/Wing900/TextbookGo/internal/api"
)

// DownloadFile 下载文件到指定路径
// 使用 32KB 缓冲区流式写入，通过回调报告进度
func DownloadFile(client *api.Client, url, outputPath string, progressFn func(bytesRead, totalBytes int64)) (int64, error) {
	// 确保输出目录存在
	dir := filepath.Dir(outputPath)
	if err := os.MkdirAll(dir, 0755); err != nil {
		return 0, fmt.Errorf("创建目录失败: %w", err)
	}

	// 先写入临时文件，完成后重命名
	tmpPath := outputPath + ".tmp"

	// 检查是否有部分下载
	var offset int64
	if stat, err := os.Stat(tmpPath); err == nil {
		offset = stat.Size()
	}

	stream, contentLength, err := client.DownloadStream(url)
	if err != nil {
		return 0, err
	}
	defer stream.Close()

	// 如果有断点续传的需求，这里可以设置 Range header
	// 目前简单处理：从头下载
	totalBytes := contentLength
	if totalBytes <= 0 {
		totalBytes = 0 // 未知大小
	}

	// 创建或打开临时文件
	var file *os.File
	if offset > 0 {
		file, err = os.OpenFile(tmpPath, os.O_WRONLY|os.O_APPEND, 0644)
	} else {
		file, err = os.Create(tmpPath)
	}
	if err != nil {
		return 0, fmt.Errorf("创建文件失败: %w", err)
	}
	defer file.Close()

	// 32KB 缓冲区
	buf := make([]byte, 32*1024)
	var bytesRead int64
	lastReport := time.Now()

	for {
		n, readErr := stream.Read(buf)
		if n > 0 {
			if _, writeErr := file.Write(buf[:n]); writeErr != nil {
				return bytesRead, fmt.Errorf("写入文件失败: %w", writeErr)
			}
			bytesRead += int64(n)

			// 每 100ms 报告一次进度
			if time.Since(lastReport) > 100*time.Millisecond && progressFn != nil {
				progressFn(bytesRead, totalBytes)
				lastReport = time.Now()
			}
		}

		if readErr == io.EOF {
			break
		}
		if readErr != nil {
			return bytesRead, fmt.Errorf("读取数据失败: %w", readErr)
		}
	}

	// 关闭文件后重命名
	file.Close()

	// 确保目标文件不存在
	if err := os.Remove(outputPath); err != nil && !os.IsNotExist(err) {
		// 忽略删除错误
	}

	if err := os.Rename(tmpPath, outputPath); err != nil {
		return bytesRead, fmt.Errorf("重命名文件失败: %w", err)
	}

	// 最终进度报告
	if progressFn != nil {
		progressFn(bytesRead, totalBytes)
	}

	return bytesRead, nil
}

// DownloadWithFallback 带备用链接的下载
func DownloadWithFallback(client *api.Client, url, backupURL, outputPath string, progressFn func(int64, int64)) (int64, error) {
	// 尝试主链接
	size, err := DownloadFile(client, url, outputPath, progressFn)
	if err == nil {
		return size, nil
	}

	// 主链接失败，尝试备用链接
	if backupURL != "" {
		// 清理临时文件
		os.Remove(outputPath + ".tmp")
		size, err = DownloadFile(client, backupURL, outputPath, progressFn)
		if err == nil {
			return size, nil
		}
	}

	return 0, fmt.Errorf("所有下载链接均失败: %w", err)
}
