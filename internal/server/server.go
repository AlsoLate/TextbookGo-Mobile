package server

import (
	"crypto/rand"
	"encoding/hex"
	"io/fs"
	"net/http"
	"sync"
)

// Server 提供 JSON API + SSE + 前端静态资源。
type Server struct {
	app     App
	static  fs.FS
	mu      sync.Mutex
	streams map[string]<-chan Event
}

func New(app App, static fs.FS) *Server {
	return &Server{app: app, static: static, streams: make(map[string]<-chan Event)}
}

func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("/api/home", s.handleHome)
	mux.HandleFunc("/api/login", s.handleLogin)
	mux.HandleFunc("/api/catalog", s.handleCatalog)
	mux.HandleFunc("/api/shelf", s.handleShelf)
	mux.HandleFunc("/api/shelf/delete", s.handleShelfDelete)
	mux.HandleFunc("/api/download", s.handleDownload)
	mux.HandleFunc("/api/download/stream", s.handleStream)
	mux.HandleFunc("/api/open-dir", s.handleOpenDir)
	mux.HandleFunc("/api/token/forget", s.handleTokenForget)
	mux.HandleFunc("/api/cover", s.handleCover)
	mux.Handle("/", http.FileServer(http.FS(s.static)))
	return mux
}

// putStream 登记一个下载流，返回客户端订阅用的 streamID。
func (s *Server) putStream(ch <-chan Event) string {
	id := newStreamID()
	s.mu.Lock()
	s.streams[id] = ch
	s.mu.Unlock()
	return id
}

// takeStream 取出并移除一个下载流；不存在返回 nil。
func (s *Server) takeStream(id string) <-chan Event {
	if id == "" {
		return nil
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	ch, ok := s.streams[id]
	if ok {
		delete(s.streams, id)
	}
	return ch
}

func newStreamID() string {
	var b [16]byte
	_, _ = rand.Read(b[:])
	return hex.EncodeToString(b[:])
}