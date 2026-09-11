package webapp

import (
	"context"
	"errors"
	"os"
	"path/filepath"
	"sync"

	"github.com/Wing900/TextbookGo/internal/app"
	"github.com/Wing900/TextbookGo/internal/auth"
	"github.com/Wing900/TextbookGo/internal/cover"
	"github.com/Wing900/TextbookGo/internal/download"
	"github.com/Wing900/TextbookGo/internal/models"
	"github.com/Wing900/TextbookGo/internal/server"
)

// WebApp 实现 server.App，桥接真实业务 Service。
type WebApp struct {
	service *app.Service
	mu      sync.RWMutex
	catalog *app.CatalogData
	covers  sync.Map // id -> []byte (封面 PNG 缓存)
}

func New(service *app.Service) *WebApp {
	return &WebApp{service: service}
}

func (w *WebApp) Home() (server.HomeInfo, error) {
	h, err := w.service.HomeData()
	if err != nil {
		return server.HomeInfo{}, err
	}
	return server.HomeInfo{OutputDir: h.OutputDir, LoggedIn: h.LoggedIn, HistoryCount: h.HistoryCount}, nil
}

func (w *WebApp) Login() error {
	c, err := w.service.EnsureCatalog()
	if err != nil {
		return err
	}
	w.mu.Lock()
	w.catalog = c
	w.mu.Unlock()
	return nil
}

func (w *WebApp) Catalog() (*server.Catalog, error) {
	c, err := w.ensure()
	if err != nil {
		return nil, err
	}
	out := &server.Catalog{}
	for _, g := range c.Grades {
		out.Grades = append(out.Grades, buildGrade(w.service, c.Entries, g))
	}
	return out, nil
}

func (w *WebApp) Shelf() ([]server.ShelfBook, error) {
	// 与文件系统双向同步：移除磁盘上已丢失的记录
	_, _ = download.Reconcile(w.service.OutputDir)
	history, err := download.LoadHistory(w.service.OutputDir)
	if err != nil {
		return nil, err
	}
	out := make([]server.ShelfBook, 0, len(history.Books))
	for _, b := range history.Books {
		out = append(out, server.ShelfBook{
			ID: b.ID, Title: b.Title, Filename: b.Filename,
			DownloadedAt: b.DownloadedAt, Size: b.Size,
		})
	}
	return out, nil
}

func (w *WebApp) Download(ctx context.Context, ids []string) <-chan server.Event {
	out := make(chan server.Event, 32)
	c, err := w.ensure()
	if err != nil {
		go func() {
			out <- server.Event{Type: server.EventError, Message: "未登录"}
			close(out)
		}()
		return out
	}
	books := resolveBooks(c.Entries, ids)
	if len(books) == 0 {
		go func() {
			out <- server.Event{Type: server.EventError, Message: "未找到教材"}
			close(out)
		}()
		return out
	}
	src := w.service.StartDownload(ctx, c.Token, books)
	go func() {
		defer close(out)
		for u := range src {
			emitEvents(out, u)
		}
	}()
	return out
}

func (w *WebApp) OpenDir() error {
	return w.service.OpenDir()
}

// ForgetToken “记住登录状态”开关关闭：删除已存凭据，下次启动需重新扫码。
func (w *WebApp) ForgetToken() error {
	return auth.ForgetToken()
}

// DeleteBook 删除书架上一本教材：移除历史记录 + 删除 PDF 文件。
func (w *WebApp) DeleteBook(id string) error {
	return download.RemoveBookWithFile(w.service.OutputDir, id)
}

// Cover 渲染已下载教材 PDF 第 1 页作为封面（带内存缓存）。
func (w *WebApp) Cover(ctx context.Context, id string) ([]byte, string, error) {
	if v, ok := w.covers.Load(id); ok {
		return v.([]byte), "image/png", nil
	}
	history, err := download.LoadHistory(w.service.OutputDir)
	if err != nil {
		return nil, "", err
	}
	var fn string
	for _, b := range history.Books {
		if b.ID == id {
			fn = b.Filename
			break
		}
	}
	if fn == "" {
		return nil, "", errors.New("教材未下载")
	}
	pdfBytes, err := os.ReadFile(filepath.Join(w.service.OutputDir, fn))
	if err != nil {
		return nil, "", err
	}
	pngBytes, err := cover.Render(pdfBytes)
	if err != nil {
		return nil, "", err
	}
	w.covers.Store(id, pngBytes)
	return pngBytes, "image/png", nil
}

func (w *WebApp) ensure() (*app.CatalogData, error) {
	w.mu.RLock()
	c := w.catalog
	w.mu.RUnlock()
	if c != nil {
		return c, nil
	}
	if err := w.Login(); err != nil {
		return nil, err
	}
	w.mu.RLock()
	defer w.mu.RUnlock()
	return w.catalog, nil
}

func buildGrade(service *app.Service, entries []models.CatalogEntry, grade string) server.Grade {
	sg := server.Grade{Name: grade}
	for _, sub := range service.Subjects(entries, grade) {
		sg.Subjects = append(sg.Subjects, buildSubject(service, entries, grade, sub))
	}
	return sg
}

func buildSubject(service *app.Service, entries []models.CatalogEntry, grade, sub string) server.Subject {
	return server.Subject{Name: sub, Books: booksDTO(service.Books(entries, grade, sub))}
}

func booksDTO(books []app.SelectableBook) []server.Book {
	out := make([]server.Book, 0, len(books))
	for _, b := range books {
		out = append(out, server.Book{
			ID: b.ID, Title: b.Title, Version: b.Version, Volume: b.Volume, Downloaded: b.Downloaded,
			CoverURL: b.CoverURL,
		})
	}
	return out
}

func resolveBooks(entries []models.CatalogEntry, ids []string) []models.BookItem {
	byID := make(map[string]models.CatalogEntry, len(entries))
	for _, e := range entries {
		byID[e.ID] = e
	}
	books := make([]models.BookItem, 0, len(ids))
	for _, id := range ids {
		e, ok := byID[id]
		if !ok {
			continue
		}
		books = append(books, models.BookItem{ID: e.ID, Title: e.Title})
	}
	return books
}

func emitEvents(out chan<- server.Event, u app.DownloadUpdate) {
	for _, ev := range toEvents(u) {
		out <- ev
	}
}

func toEvents(u app.DownloadUpdate) []server.Event {
	var evs []server.Event
	switch u.Type {
	case app.UpdateDownload:
		evs = append(evs, progressOf(u.Event))
		evs = append(evs, doneOf(u.Event)...)
	case app.UpdateFinished:
		evs = append(evs, server.Event{Type: server.EventFinished})
	case app.UpdateBookmarkSuccess:
		evs = append(evs, server.Event{Type: server.EventBookmark, BookTitle: u.BookTitle})
	case app.UpdateBookmarkError:
		evs = append(evs, server.Event{Type: server.EventError, BookTitle: u.BookTitle, Message: u.Error.Error()})
	}
	return evs
}

func progressOf(e download.DownloadEvent) server.Event {
	return server.Event{
		Type: server.EventProgress, BookID: e.BookID, BookTitle: e.BookTitle,
		Filename: e.Filename, Read: e.BytesRead, Total: e.TotalBytes,
	}
}

func doneOf(e download.DownloadEvent) []server.Event {
	if e.Type != download.EventDone {
		return nil
	}
	return []server.Event{{Type: server.EventDone, BookID: e.BookID, BookTitle: e.BookTitle}}
}
