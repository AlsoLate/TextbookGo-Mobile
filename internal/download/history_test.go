package download

import (
	"os"
	"path/filepath"
	"testing"
)

func writeHistory(t *testing.T, dir string, h *History) {
	t.Helper()
	if err := SaveHistory(dir, h); err != nil {
		t.Fatalf("save: %v", err)
	}
}

func touchPDF(t *testing.T, dir, name string) {
	t.Helper()
	if err := os.WriteFile(filepath.Join(dir, name), []byte("%PDF-1.4 fake"), 0o644); err != nil {
		t.Fatalf("touch %s: %v", name, err)
	}
}

// TestReconcile 校验：history 有 3 本，磁盘只剩 2 个 PDF → Reconcile 后 history 剩 2 本。
func TestReconcile(t *testing.T) {
	dir := t.TempDir()
	writeHistory(t, dir, &History{Books: []HistoryBook{
		{ID: "a", Title: "A", Filename: "a.pdf"},
		{ID: "b", Title: "B", Filename: "b.pdf"},
		{ID: "c", Title: "C", Filename: "c.pdf"}, // 文件不存在
	}})
	touchPDF(t, dir, "a.pdf")
	touchPDF(t, dir, "b.pdf")

	removed, err := Reconcile(dir)
	if err != nil {
		t.Fatalf("reconcile: %v", err)
	}
	if len(removed) != 1 || removed[0] != "c" {
		t.Fatalf("removed=%v want [c]", removed)
	}
	h, _ := LoadHistory(dir)
	if len(h.Books) != 2 {
		t.Fatalf("after reconcile books=%d want 2", len(h.Books))
	}
}

// TestRemoveBookWithFile 校验：删除记录同时删除 PDF 文件。
func TestRemoveBookWithFile(t *testing.T) {
	dir := t.TempDir()
	writeHistory(t, dir, &History{Books: []HistoryBook{
		{ID: "a", Title: "A", Filename: "a.pdf"},
	}})
	touchPDF(t, dir, "a.pdf")

	if err := RemoveBookWithFile(dir, "a"); err != nil {
		t.Fatalf("remove: %v", err)
	}
	h, _ := LoadHistory(dir)
	if len(h.Books) != 0 {
		t.Fatalf("books=%d want 0", len(h.Books))
	}
	if _, err := os.Stat(filepath.Join(dir, "a.pdf")); !os.IsNotExist(err) {
		t.Fatalf("pdf file should be deleted, got %v", err)
	}
}

// TestRemoveBookWithFileMissingPDF 校验：文件已不在时，仅清记录，不报错。
func TestRemoveBookWithFileMissingPDF(t *testing.T) {
	dir := t.TempDir()
	writeHistory(t, dir, &History{Books: []HistoryBook{
		{ID: "a", Title: "A", Filename: "a.pdf"},
	}})
	// 不创建 a.pdf
	if err := RemoveBookWithFile(dir, "a"); err != nil {
		t.Fatalf("remove: %v", err)
	}
	h, _ := LoadHistory(dir)
	if len(h.Books) != 0 {
		t.Fatalf("books=%d want 0", len(h.Books))
	}
}