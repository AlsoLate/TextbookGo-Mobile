package server

import (
	"context"
	"encoding/json"
	"errors"
	"io/fs"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"testing/fstest"
)

// fakeApp 实现 App 接口，用于契约测试（无网络/文件/真实服务）。
type fakeApp struct {
	loginCalls int
	openCalls  int
	dlCalls    int
	delCalls   int
}

func (f *fakeApp) Home() (HomeInfo, error) {
	return HomeInfo{OutputDir: "/tmp/books", LoggedIn: true, HistoryCount: 2}, nil
}

func (f *fakeApp) Login() error { f.loginCalls++; return nil }

func (f *fakeApp) Catalog() (*Catalog, error) {
	return &Catalog{Grades: []Grade{
		{Name: "高一", Subjects: []Subject{
			{Name: "语文", Books: []Book{{ID: "b1", Title: "语文必修一", Version: "人教版", Volume: "必修一", Downloaded: true}}},
		}},
	}}, nil
}

func (f *fakeApp) Shelf() ([]ShelfBook, error) {
	return []ShelfBook{{ID: "b1", Title: "语文必修一", Filename: "a.pdf", Size: 1024}}, nil
}

func (f *fakeApp) Download(_ context.Context, ids []string) <-chan Event {
	f.dlCalls++
	ch := make(chan Event, 3)
	go func() {
		ch <- Event{Type: EventStart, BookID: ids[0], BookTitle: "语文必修一"}
		ch <- Event{Type: EventProgress, BookID: ids[0], BookTitle: "语文必修一", Read: 512, Total: 1024}
		ch <- Event{Type: EventFinished}
		close(ch)
	}()
	return ch
}

func (f *fakeApp) OpenDir() error { f.openCalls++; return nil }
func (f *fakeApp) ForgetToken() error { return nil }

func (f *fakeApp) DeleteBook(id string) error { f.delCalls++; return nil }

func (f *fakeApp) Cover(_ context.Context, id string) ([]byte, string, error) {
	if id == "b1" {
		return []byte{0x89, 'P', 'N', 'G'}, "image/png", nil
	}
	return nil, "", errors.New("not found")
}
func fakeStatic() fs.FS {
	return fstest.MapFS{
		"index.html":  &fstest.MapFile{Data: []byte("<html><body>下书</body></html>")},
		"app.js":      &fstest.MapFile{Data: []byte(`fetch("/api/home")`)},
		"style.css":   &fstest.MapFile{Data: []byte(":root{}")},
	}
}

func newTestServer(fake *fakeApp) *Server {
	return New(fake, fakeStatic())
}

func do(t *testing.T, s *Server, method, path string, body string) *httptest.ResponseRecorder {
	t.Helper()
	var rd *strings.Reader
	if body != "" {
		rd = strings.NewReader(body)
	} else {
		rd = strings.NewReader("")
	}
	req := httptest.NewRequest(method, path, rd)
	rec := httptest.NewRecorder()
	s.Handler().ServeHTTP(rec, req)
	return rec
}

func decode[T any](t *testing.T, rec *httptest.ResponseRecorder) T {
	t.Helper()
	var v T
	if err := json.NewDecoder(rec.Body).Decode(&v); err != nil {
		t.Fatalf("decode %s: %v (body=%s)", "T", err, rec.Body.String())
	}
	return v
}

func TestHome(t *testing.T) {
	s := newTestServer(&fakeApp{})
	rec := do(t, s, http.MethodGet, "/api/home", "")
	if rec.Code != 200 {
		t.Fatalf("home status=%d", rec.Code)
	}
	var h HomeInfo
	if err := json.NewDecoder(rec.Body).Decode(&h); err != nil {
		t.Fatalf("decode: %v", err)
	}
	if !h.LoggedIn || h.OutputDir == "" || h.HistoryCount != 2 {
		t.Fatalf("unexpected home: %+v", h)
	}
}

func TestLogin(t *testing.T) {
	fake := &fakeApp{}
	s := newTestServer(fake)
	rec := do(t, s, http.MethodPost, "/api/login", "")
	if rec.Code != 200 {
		t.Fatalf("login status=%d", rec.Code)
	}
	if fake.loginCalls != 1 {
		t.Fatalf("loginCalls=%d want 1", fake.loginCalls)
	}
	var r struct{ OK bool `json:"ok"` }
	if err := json.NewDecoder(rec.Body).Decode(&r); err != nil {
		t.Fatalf("decode: %v", err)
	}
	if !r.OK {
		t.Fatalf("login ok=false")
	}
}

func TestCatalogNested(t *testing.T) {
	s := newTestServer(&fakeApp{})
	rec := do(t, s, http.MethodGet, "/api/catalog", "")
	if rec.Code != 200 {
		t.Fatalf("catalog status=%d", rec.Code)
	}
	var c Catalog
	if err := json.NewDecoder(rec.Body).Decode(&c); err != nil {
		t.Fatalf("decode: %v", err)
	}
	if len(c.Grades) != 1 || c.Grades[0].Name != "高一" {
		t.Fatalf("grades wrong: %+v", c.Grades)
	}
	book := c.Grades[0].Subjects[0].Books[0]
	if book.ID != "b1" || book.Title != "语文必修一" || !book.Downloaded {
		t.Fatalf("book wrong: %+v", book)
	}
}

func TestShelf(t *testing.T) {
	s := newTestServer(&fakeApp{})
	rec := do(t, s, http.MethodGet, "/api/shelf", "")
	if rec.Code != 200 {
		t.Fatalf("shelf status=%d", rec.Code)
	}
	books := decode[[]ShelfBook](t, rec)
	if len(books) != 1 || books[0].ID != "b1" {
		t.Fatalf("shelf wrong: %+v", books)
	}
}

func TestOpenDir(t *testing.T) {
	fake := &fakeApp{}
	s := newTestServer(fake)
	rec := do(t, s, http.MethodPost, "/api/open-dir", "")
	if rec.Code != 200 {
		t.Fatalf("open status=%d", rec.Code)
	}
	if fake.openCalls != 1 {
		t.Fatalf("openCalls=%d want 1", fake.openCalls)
	}
}

func TestShelfDelete(t *testing.T) {
	fake := &fakeApp{}
	s := newTestServer(fake)
	rec := do(t, s, http.MethodPost, "/api/shelf/delete?id=b1", "")
	if rec.Code != 200 {
		t.Fatalf("delete status=%d body=%s", rec.Code, rec.Body.String())
	}
	if fake.delCalls != 1 {
		t.Fatalf("delCalls=%d want 1", fake.delCalls)
	}
	rec2 := do(t, s, http.MethodPost, "/api/shelf/delete", "")
	if rec2.Code != 400 {
		t.Fatalf("missing id status=%d want 400", rec2.Code)
	}
}

func TestCover(t *testing.T) {
	fake := &fakeApp{}
	s := newTestServer(fake)
	rec := do(t, s, http.MethodGet, "/api/cover?id=b1", "")
	if rec.Code != 200 || rec.Header().Get("Content-Type") != "image/png" {
		t.Fatalf("cover status=%d type=%s", rec.Code, rec.Header().Get("Content-Type"))
	}
	if !strings.HasPrefix(string(rec.Body.Bytes()), "\x89PNG") {
		t.Fatalf("cover body not PNG")
	}
	rec2 := do(t, s, http.MethodGet, "/api/cover?id=nope", "")
	if rec2.Code != 404 {
		t.Fatalf("missing cover status=%d want 404", rec2.Code)
	}
}

func TestStaticIndex(t *testing.T) {
	s := newTestServer(&fakeApp{})
	rec := do(t, s, http.MethodGet, "/", "")
	if rec.Code != 200 {
		t.Fatalf("static status=%d", rec.Code)
	}
	if !strings.Contains(rec.Body.String(), "下书") {
		t.Fatalf("static body missing brand")
	}
}
