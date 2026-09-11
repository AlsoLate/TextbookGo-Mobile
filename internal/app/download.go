package app

import (
	"context"
	"fmt"
	"path/filepath"
	"sync"

	"github.com/Wing900/TextbookGo/internal/api"
	"github.com/Wing900/TextbookGo/internal/download"
	"github.com/Wing900/TextbookGo/internal/models"
	"github.com/Wing900/TextbookGo/internal/pdf"
)

func (s *Service) StartDownload(ctx context.Context, token string, books []models.BookItem) <-chan DownloadUpdate {
	session := s.session
	if token == "" {
		var err error
		token, err = session.EnsureToken()
		if err != nil {
			updates := make(chan DownloadUpdate, 1)
			updates <- DownloadUpdate{Type: UpdateFinished, Error: fmt.Errorf("获取登录态失败: %w", err)}
			close(updates)
			return updates
		}
	}

	client := api.NewClient(token)
	client.SetUnauthorizedHandler(session.RefreshToken)
	source := download.NewManager(client, 3).DownloadBooks(ctx, books, s.OutputDir)
	updates := make(chan DownloadUpdate, 32)

	go func() {
		defer close(updates)
		var bookmarkWG sync.WaitGroup

		for event := range source {
			updates <- DownloadUpdate{Type: UpdateDownload, Event: event}
			if event.Type != download.EventDone {
				continue
			}
			bookmarkWG.Add(1)
			go func(evt download.DownloadEvent) {
				defer bookmarkWG.Done()
				s.tryAddBookmarks(client, filepath.Join(s.OutputDir, evt.Filename), evt.BookID, evt.BookTitle, updates)
			}(event)
		}

		bookmarkWG.Wait()
		updates <- DownloadUpdate{Type: UpdateFinished}
	}()
	return updates
}

func (s *Service) tryAddBookmarks(client *api.Client, pdfPath, contentID, title string, updates chan<- DownloadUpdate) {
	if !pdf.HasPDFCPU() {
		return
	}

	bookmarks, err := api.FetchBookmarks(client, contentID)
	if err != nil || len(bookmarks) == 0 {
		return
	}
	if err := pdf.AddBookmarks(pdfPath, bookmarks); err != nil {
		updates <- DownloadUpdate{Type: UpdateBookmarkError, BookTitle: title, Error: err}
		return
	}
	updates <- DownloadUpdate{Type: UpdateBookmarkSuccess, BookTitle: title}
}
