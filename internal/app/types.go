package app

import (
	"github.com/Wing900/TextbookGo/internal/auth"
	"github.com/Wing900/TextbookGo/internal/download"
	"github.com/Wing900/TextbookGo/internal/models"
)

type Service struct {
	OutputDir string
	session   *auth.SessionManager
}

type HomeData struct {
	OutputDir    string
	LoggedIn     bool
	HistoryCount int
}

type CatalogData struct {
	Token   string
	Entries []models.CatalogEntry
	Grades  []string
}

type SelectableBook struct {
	models.BookItem
	Downloaded bool
	Version    string
	Volume     string
	CoverURL   string
}

type DownloadUpdateType int

const (
	UpdateDownload DownloadUpdateType = iota
	UpdateBookmarkSuccess
	UpdateBookmarkError
	UpdateFinished
)

type DownloadUpdate struct {
	Type      DownloadUpdateType
	Event     download.DownloadEvent
	BookTitle string
	Error     error
}

func NewService(outputDir string) *Service {
	return &Service{OutputDir: outputDir, session: auth.NewSessionManager(auth.LoginViaBrowserQuiet)}
}
