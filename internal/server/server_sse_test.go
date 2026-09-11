package server

import (
	"encoding/json"
	"net/http"
	"strings"
	"sync"
	"testing"
)

func TestDownloadStream(t *testing.T) {
	fake := &fakeApp{}
	s := newTestServer(fake)

	start := do(t, s, http.MethodPost, "/api/download", `{"ids":["b1"]}`)
	if start.Code != 200 {
		t.Fatalf("download start status=%d", start.Code)
	}
	if fake.dlCalls != 1 {
		t.Fatalf("dlCalls=%d want 1", fake.dlCalls)
	}
	var resp struct {
		OK        bool   `json:"ok"`
		StreamID string `json:"stream_id"`
	}
	if err := json.NewDecoder(start.Body).Decode(&resp); err != nil {
		t.Fatalf("decode start: %v (body=%s)", err, start.Body.String())
	}
	if !resp.OK || resp.StreamID == "" {
		t.Fatalf("start response missing stream_id: %+v", resp)
	}

	rec := do(t, s, http.MethodGet, "/api/download/stream?id="+resp.StreamID, "")
	if rec.Code != 200 {
		t.Fatalf("stream status=%d", rec.Code)
	}
	body := rec.Body.String()
	if !strings.Contains(body, `"type":"progress"`) {
		t.Fatalf("stream missing progress: %s", body)
	}
	if !strings.Contains(body, "finished") {
		t.Fatalf("stream missing finished: %s", body)
	}
}

// TestDownloadStreamConcurrent 验证两个并发下载流互不覆盖（修复单槽 bug）。
func TestDownloadStreamConcurrent(t *testing.T) {
	fake := &fakeApp{}
	s := newTestServer(fake)

	start1 := do(t, s, http.MethodPost, "/api/download", `{"ids":["b1"]}`)
	start2 := do(t, s, http.MethodPost, "/api/download", `{"ids":["b1"]}`)
	if start1.Code != 200 || start2.Code != 200 {
		t.Fatalf("start status=%d/%d", start1.Code, start2.Code)
	}
	var r1, r2 struct{ StreamID string `json:"stream_id"` }
	json.NewDecoder(start1.Body).Decode(&r1)
	json.NewDecoder(start2.Body).Decode(&r2)
	if r1.StreamID == "" || r2.StreamID == "" {
		t.Fatalf("missing stream_id: %+v / %+v", r1, r2)
	}
	if r1.StreamID == r2.StreamID {
		t.Fatalf("two downloads share same stream_id: %s", r1.StreamID)
	}

	var wg sync.WaitGroup
	wg.Add(2)
	var body1, body2 string
	go func() {
		defer wg.Done()
		rec := do(t, s, http.MethodGet, "/api/download/stream?id="+r1.StreamID, "")
		body1 = rec.Body.String()
	}()
	go func() {
		defer wg.Done()
		rec := do(t, s, http.MethodGet, "/api/download/stream?id="+r2.StreamID, "")
		body2 = rec.Body.String()
	}()
	wg.Wait()

	if !strings.Contains(body1, "finished") || !strings.Contains(body2, "finished") {
		t.Fatalf("both streams must finish independently:\n s1=%s\n s2=%s", body1, body2)
	}
}

// TestDownloadStreamMissingID 验证缺 id 参数返回 400。
func TestDownloadStreamMissingID(t *testing.T) {
	s := newTestServer(&fakeApp{})
	rec := do(t, s, http.MethodGet, "/api/download/stream", "")
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("missing id status=%d want 400", rec.Code)
	}
}

// TestDownloadStreamUnknownID 验证未知 id 返回 404。
func TestDownloadStreamUnknownID(t *testing.T) {
	s := newTestServer(&fakeApp{})
	rec := do(t, s, http.MethodGet, "/api/download/stream?id=does-not-exist", "")
	if rec.Code != http.StatusNotFound {
		t.Fatalf("unknown id status=%d want 404", rec.Code)
	}
}