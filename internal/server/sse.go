package server

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
)

func (s *Server) handleDownload(w http.ResponseWriter, r *http.Request) {
	var req struct {
		IDs []string `json:"ids"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeErr(w, http.StatusBadRequest, "请求格式错误")
		return
	}
	if len(req.IDs) == 0 {
		writeErr(w, http.StatusBadRequest, "未选择教材")
		return
	}
	// 关键：下载生命周期必须独立于本次 POST 请求。
	// r.Context() 在 handler 返回（响应写完）即被取消，若沿用会把
	// 未派发的书静默砍掉（无任何事件），前端却照收 finished ——
	// 表现为“等待中却自动跳到最后一步”。
	ch := s.app.Download(context.Background(), req.IDs)
	streamID := s.putStream(ch)
	writeJSON(w, http.StatusOK, map[string]any{"ok": true, "count": len(req.IDs), "stream_id": streamID})
}

func (s *Server) handleStream(w http.ResponseWriter, r *http.Request) {
	id := r.URL.Query().Get("id")
	if id == "" {
		writeErr(w, http.StatusBadRequest, "missing stream id")
		return
	}
	ch := s.takeStream(id)
	if ch == nil {
		writeErr(w, http.StatusNotFound, "stream not found")
		return
	}
	fl, ok := w.(http.Flusher)
	if !ok {
		http.Error(w, "streaming unsupported", http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "text/event-stream")
	w.Header().Set("Cache-Control", "no-cache")
	w.Header().Set("Connection", "keep-alive")
	for ev := range ch {
		data, err := json.Marshal(ev)
		if err != nil {
			continue
		}
		if _, err := fmt.Fprintf(w, "data: %s\n\n", data); err != nil {
			return
		}
		fl.Flush()
	}
}