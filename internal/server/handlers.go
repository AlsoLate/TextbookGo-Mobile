package server

import (
	"encoding/json"
	"net/http"
)

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

func writeErr(w http.ResponseWriter, status int, msg string) {
	writeJSON(w, status, map[string]any{"error": msg})
}

func (s *Server) handleHome(w http.ResponseWriter, _ *http.Request) {
	home, err := s.app.Home()
	if err != nil {
		writeErr(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, home)
}

func (s *Server) handleLogin(w http.ResponseWriter, _ *http.Request) {
	if err := s.app.Login(); err != nil {
		writeErr(w, http.StatusBadGateway, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"ok": true})
}

func (s *Server) handleCatalog(w http.ResponseWriter, _ *http.Request) {
	catalog, err := s.app.Catalog()
	if err != nil {
		writeErr(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, catalog)
}

func (s *Server) handleShelf(w http.ResponseWriter, _ *http.Request) {
	books, err := s.app.Shelf()
	if err != nil {
		writeErr(w, http.StatusInternalServerError, err.Error())
		return
	}
	if books == nil {
		books = []ShelfBook{}
	}
	writeJSON(w, http.StatusOK, books)
}

// handleOpenDir 响应“打开下载目录”：资源管理器打开下载目录。
func (s *Server) handleOpenDir(w http.ResponseWriter, _ *http.Request) {
	if err := s.app.OpenDir(); err != nil {
		writeErr(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"ok": true})
}

// handleTokenForget “记住登录状态”开关关闭时调用：删除已存 token。
// 当前会话不受影响（内存 token 继续服役），下次启动需重新扫码。
func (s *Server) handleTokenForget(w http.ResponseWriter, _ *http.Request) {
	if err := s.app.ForgetToken(); err != nil {
		writeErr(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"ok": true})
}

func (s *Server) handleShelfDelete(w http.ResponseWriter, r *http.Request) {
	id := r.URL.Query().Get("id")
	if id == "" {
		writeErr(w, http.StatusBadRequest, "missing id")
		return
	}
	if err := s.app.DeleteBook(id); err != nil {
		writeErr(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"ok": true})
}

func (s *Server) handleCover(w http.ResponseWriter, r *http.Request) {
	id := r.URL.Query().Get("id")
	if id == "" {
		writeErr(w, http.StatusBadRequest, "missing id")
		return
	}
	data, mime, err := s.app.Cover(r.Context(), id)
	if err != nil {
		writeErr(w, http.StatusNotFound, "封面不可用")
		return
	}
	w.Header().Set("Content-Type", mime)
	w.Header().Set("Cache-Control", "public, max-age=86400")
	_, _ = w.Write(data)
}
