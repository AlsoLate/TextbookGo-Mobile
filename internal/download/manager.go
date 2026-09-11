package download

import (
	"context"
	"fmt"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"github.com/Wing900/TextbookGo/internal/api"
	"github.com/Wing900/TextbookGo/internal/models"
)

// Manager 下载管理器
type Manager struct {
	concurrency int
	client      *api.Client
}

// NewManager 创建下载管理器
func NewManager(client *api.Client, concurrency int) *Manager {
	if concurrency <= 0 {
		concurrency = 3
	}
	return &Manager{
		concurrency: concurrency,
		client:      client,
	}
}

// DownloadBooks 并发下载多本教材
// 返回事件 channel，调用方可监听进度
func (m *Manager) DownloadBooks(ctx context.Context, books []models.BookItem, outputDir string) <-chan DownloadEvent {
	bufferSize := len(books)
	if bufferSize < 16 {
		bufferSize = 16
	}
	if bufferSize > 64 {
		bufferSize = 64
	}

	events := make(chan DownloadEvent, bufferSize)

	go func() {
		defer close(events)

		sem := make(chan struct{}, m.concurrency)
		var wg sync.WaitGroup

		for _, book := range books {
			select {
			case <-ctx.Done():
				return
			default:
			}

			wg.Add(1)
			go func(b models.BookItem) {
				defer wg.Done()

				sem <- struct{}{}
				defer func() { <-sem }()

				m.downloadSingle(ctx, b, outputDir, events)
			}(book)
		}

		wg.Wait()
	}()

	return events
}

// downloadSingle 下载单本教材
func (m *Manager) downloadSingle(ctx context.Context, book models.BookItem, outputDir string, events chan<- DownloadEvent) {
	// 发送开始事件
	events <- DownloadEvent{
		Type:      EventStart,
		BookTitle: book.Title,
		BookID:    book.ID,
		Filename:  book.Filename,
	}

	// 检查是否已下载
	if IsBookAlreadyDownloaded(outputDir, book.ID) {
		filename := book.Filename
		if filename == "" {
			filename = sanitizeFilename(book.Title) + ".pdf"
		}
		events <- DownloadEvent{
			Type:      EventDone,
			BookTitle: book.Title,
			BookID:    book.ID,
			Filename:  filename,
		}
		return
	}

	// 获取资源详情
	detail, err := api.FetchResourceDetails(m.client, book.ID)
	if err != nil {
		events <- DownloadEvent{
			Type:      EventError,
			BookTitle: book.Title,
			BookID:    book.ID,
			Error:     fmt.Errorf("获取资源详情失败: %w", err),
		}
		return
	}

	// 解析下载链接
	hasToken := m.client.CurrentToken() != ""
	downloadURL, backupURL, _ := api.ParseResourceURL(detail.TiItems, hasToken)

	if downloadURL == "" {
		events <- DownloadEvent{
			Type:      EventError,
			BookTitle: book.Title,
			BookID:    book.ID,
			Error:     fmt.Errorf("未找到下载链接"),
		}
		return
	}

	// 确定文件名
	filename := book.Filename
	if filename == "" {
		filename = sanitizeFilename(book.Title) + ".pdf"
	}

	outputPath := filepath.Join(outputDir, filename)

	// 开始下载
	startTime := time.Now()

	downloaded, err := DownloadWithFallback(m.client, downloadURL, backupURL, outputPath,
		func(bytesRead, totalBytes int64) {
			events <- DownloadEvent{
				Type:       EventProgress,
				BookTitle:  book.Title,
				BookID:     book.ID,
				Filename:   filename,
				BytesRead:  bytesRead,
				TotalBytes: totalBytes,
			}
			_ = startTime // 保留用于未来速度计算
		},
	)

	if err != nil {
		events <- DownloadEvent{
			Type:      EventError,
			BookTitle: book.Title,
			BookID:    book.ID,
			Error:     err,
		}
		return
	}

	// 记录下载历史
	AddBook(outputDir, HistoryBook{
		ID:       book.ID,
		Title:    book.Title,
		Filename: filename,
		Size:     downloaded,
	})

	events <- DownloadEvent{
		Type:       EventDone,
		BookTitle:  book.Title,
		BookID:     book.ID,
		Filename:   filename,
		BytesRead:  downloaded,
		TotalBytes: downloaded,
	}
}

// sanitizeFilename 清理文件名中的非法字符
func sanitizeFilename(name string) string {
	illegal := []string{"\\", "/", ":", "*", "?", "\"", "<", ">", "|"}
	result := name
	for _, c := range illegal {
		result = strings.ReplaceAll(result, c, "_")
	}
	return result
}
