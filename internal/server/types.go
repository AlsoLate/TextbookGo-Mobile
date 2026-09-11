package server

import "context"

// App 是 server 依赖的抽象能力。真实实现由业务装配注入，测试用 fake。
type App interface {
	Home() (HomeInfo, error)
	Login() error
	Catalog() (*Catalog, error)
	Shelf() ([]ShelfBook, error)
	Download(ctx context.Context, ids []string) <-chan Event
	OpenDir() error
	ForgetToken() error
	Cover(ctx context.Context, id string) ([]byte, string, error)
	DeleteBook(id string) error
}

type HomeInfo struct {
	OutputDir    string `json:"output_dir"`
	LoggedIn     bool   `json:"logged_in"`
	HistoryCount int    `json:"history_count"`
}

type Catalog struct {
	Grades []Grade `json:"grades"`
}

type Grade struct {
	Name     string    `json:"name"`
	Subjects []Subject `json:"subjects"`
}

type Subject struct {
	Name  string `json:"name"`
	Books []Book `json:"books"`
}

type Book struct {
	ID         string `json:"id"`
	Title      string `json:"title"`
	Version    string `json:"version"`
	Volume     string `json:"volume"`
	Downloaded bool   `json:"downloaded"`
	CoverURL   string `json:"cover_url,omitempty"`
}

type ShelfBook struct {
	ID           string `json:"id"`
	Title        string `json:"title"`
	Filename     string `json:"filename"`
	DownloadedAt string `json:"downloaded_at"`
	Size         int64  `json:"size"`
}

const (
	EventStart    = "start"
	EventProgress = "progress"
	EventDone     = "done"
	EventBookmark = "bookmark"
	EventFinished = "finished"
	EventError    = "error"
)

type Event struct {
	Type      string `json:"type"`
	BookID    string `json:"book_id,omitempty"`
	BookTitle string `json:"book_title,omitempty"`
	Filename  string `json:"filename,omitempty"`
	Read      int64  `json:"read,omitempty"`
	Total     int64  `json:"total,omitempty"`
	Message   string `json:"message,omitempty"`
}
