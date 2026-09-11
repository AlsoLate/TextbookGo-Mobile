package download

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"time"
)

// HistoryFile 下载历史文件名
const HistoryFile = "download_history.json"

// History 下载历史
type History struct {
	Books []HistoryBook `json:"books"`
}

// HistoryBook 已下载教材记录
type HistoryBook struct {
	ID           string `json:"id"`
	Title        string `json:"title"`
	Filename     string `json:"filename"`
	DownloadedAt string `json:"downloaded_at"`
	Size         int64  `json:"size"`
}

// LoadHistory 从输出目录加载下载历史
func LoadHistory(dir string) (*History, error) {
	path := filepath.Join(dir, HistoryFile)
	data, err := os.ReadFile(path)
	if err != nil {
		if os.IsNotExist(err) {
			return &History{}, nil
		}
		return nil, fmt.Errorf("读取下载历史失败: %w", err)
	}

	var history History
	if err := json.Unmarshal(data, &history); err != nil {
		return nil, fmt.Errorf("解析下载历史失败: %w", err)
	}

	return &history, nil
}

// SaveHistory 保存下载历史到输出目录
func SaveHistory(dir string, history *History) error {
	if err := os.MkdirAll(dir, 0755); err != nil {
		return fmt.Errorf("创建目录失败: %w", err)
	}

	data, err := json.MarshalIndent(history, "", "  ")
	if err != nil {
		return fmt.Errorf("序列化历史失败: %w", err)
	}

	path := filepath.Join(dir, HistoryFile)
	if err := os.WriteFile(path, data, 0644); err != nil {
		return fmt.Errorf("写入历史文件失败: %w", err)
	}

	return nil
}

// AddBook 添加一本已下载教材到历史
func AddBook(dir string, book HistoryBook) error {
	history, err := LoadHistory(dir)
	if err != nil {
		return err
	}

	// 检查是否已存在
	for _, b := range history.Books {
		if b.ID == book.ID {
			return nil // 已存在，不重复添加
		}
	}

	if book.DownloadedAt == "" {
		book.DownloadedAt = time.Now().Format(time.RFC3339)
	}

	history.Books = append(history.Books, book)
	return SaveHistory(dir, history)
}

// HasBook 检查是否已下载过某本教材
func HasBook(dir string, bookID string) bool {
	history, err := LoadHistory(dir)
	if err != nil {
		return false
	}

	for _, b := range history.Books {
		if b.ID == bookID {
			return true
		}
	}

	return false
}

// IsBookAlreadyDownloaded 检查教材是否已下载（历史记录中存在且文件存在）
func IsBookAlreadyDownloaded(dir string, bookID string) bool {
	history, err := LoadHistory(dir)
	if err != nil {
		return false
	}

	for _, b := range history.Books {
		if b.ID == bookID {
			path := filepath.Join(dir, b.Filename)
			if _, err := os.Stat(path); err == nil {
				return true
			}
		}
	}

	return false
}

// RemoveBook 从历史中移除某本教材
func RemoveBook(dir string, bookID string) error {
	history, err := LoadHistory(dir)
	if err != nil {
		return err
	}

	var newBooks []HistoryBook
	for _, b := range history.Books {
		if b.ID != bookID {
			newBooks = append(newBooks, b)
		}
	}

	history.Books = newBooks
	return SaveHistory(dir, history)
}

// RemoveBookWithFile 移除历史记录，并删除磁盘上的 PDF 文件。
// 文件已不存在时静默忽略，仅清记录。
func RemoveBookWithFile(dir string, bookID string) error {
	history, err := LoadHistory(dir)
	if err != nil {
		return err
	}
	var filename string
	var newBooks []HistoryBook
	for _, b := range history.Books {
		if b.ID == bookID {
			filename = b.Filename
			continue
		}
		newBooks = append(newBooks, b)
	}
	history.Books = newBooks
	if filename != "" {
		if err := os.Remove(filepath.Join(dir, filename)); err != nil && !os.IsNotExist(err) {
			return fmt.Errorf("删除文件失败: %w", err)
		}
	}
	return SaveHistory(dir, history)
}

// Reconcile 校验 history 中每本书的 PDF 文件是否仍存在，移除丢失记录并回写。
// 返回被移除的 bookID 列表。用于和文件系统双向同步（用户手动删 PDF 后书架自动清理）。
func Reconcile(dir string) ([]string, error) {
	history, err := LoadHistory(dir)
	if err != nil {
		return nil, err
	}
	var removed []string
	var kept []HistoryBook
	for _, b := range history.Books {
		if _, err := os.Stat(filepath.Join(dir, b.Filename)); err == nil {
			kept = append(kept, b)
		} else {
			removed = append(removed, b.ID)
		}
	}
	if len(removed) == 0 {
		return nil, nil
	}
	history.Books = kept
	if err := SaveHistory(dir, history); err != nil {
		return removed, err
	}
	return removed, nil
}
