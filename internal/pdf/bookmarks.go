package pdf

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"

	"github.com/Wing900/TextbookGo/internal/api"
)

// AddBookmarks 使用 pdfcpu 为 PDF 添加书签
// pdfcpu 需要预先安装: go install github.com/pdfcpu/pdfcpu/cmd/pdfcpu@latest
func AddBookmarks(pdfPath string, bookmarks []api.Bookmark) error {
	if len(bookmarks) == 0 {
		return nil
	}

	// 检查文件是否存在
	if _, err := os.Stat(pdfPath); os.IsNotExist(err) {
		return fmt.Errorf("PDF 文件不存在: %s", pdfPath)
	}

	// 生成书签配置文件
	bookmarkFile := pdfPath + ".bookmarks.txt"
	if err := generateBookmarkFile(bookmarkFile, bookmarks); err != nil {
		return fmt.Errorf("生成书签文件失败: %w", err)
	}
	defer os.Remove(bookmarkFile)

	// 使用 pdfcpu 添加书签
	// pdfcpu bookmarks add [-u] [-p password] outFile jsonFile|csvFile
	cmd := exec.Command("pdfcpu", "bookmarks", "add", pdfPath, bookmarkFile)
	output, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("pdfcpu 执行失败: %s\n%s", err, string(output))
	}

	return nil
}

// HasPDFCPU 检查 pdfcpu 是否已安装
func HasPDFCPU() bool {
	_, err := exec.LookPath("pdfcpu")
	return err == nil
}

// InstallPDFCPU 安装 pdfcpu
func InstallPDFCPU() error {
	fmt.Println("正在安装 pdfcpu...")
	cmd := exec.Command("go", "install", "github.com/pdfcpu/pdfcpu/cmd/pdfcpu@latest")
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	return cmd.Run()
}

// generateBookmarkFile 生成 pdfcpu 格式的书签文件
// 格式: level "title" page
func generateBookmarkFile(path string, bookmarks []api.Bookmark) error {
	var lines []string
	flattenBookmarksToLines(bookmarks, &lines)

	content := strings.Join(lines, "\n") + "\n"
	return os.WriteFile(path, []byte(content), 0644)
}

// flattenBookmarksToLines 递归将书签展平为 pdfcpu 格式的行
func flattenBookmarksToLines(bookmarks []api.Bookmark, lines *[]string) {
	for _, bm := range bookmarks {
		if bm.Page <= 0 {
			continue
		}
		// 格式: level "title" page
		line := fmt.Sprintf(`%d "%s" %d`, bm.Level, escapeQuotes(bm.Title), bm.Page)
		*lines = append(*lines, line)

		if len(bm.Children) > 0 {
			flattenBookmarksToLines(bm.Children, lines)
		}
	}
}

// escapeQuotes 转义引号
func escapeQuotes(s string) string {
	result := ""
	for _, c := range s {
		if c == '"' {
			result += `\"`
		} else {
			result += string(c)
		}
	}
	return result
}

// GetGoBinPath 获取 GOPATH/bin 路径（pdfcpu 安装位置）
func GetGoBinPath() string {
	gopath := os.Getenv("GOPATH")
	if gopath == "" {
		home, _ := os.UserHomeDir()
		gopath = filepath.Join(home, "go")
	}

	if runtime.GOOS == "windows" {
		return filepath.Join(gopath, "bin")
	}
	return filepath.Join(gopath, "bin")
}
