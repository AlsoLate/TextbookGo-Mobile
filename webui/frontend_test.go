package webui

import (
	"regexp"
	"strings"
	"testing"
)

func readFS(t *testing.T, name string) string {
	t.Helper()
	data, err := FS.ReadFile("frontend/" + name)
	if err != nil {
		t.Fatalf("read %s: %v", name, err)
	}
	return string(data)
}

var cssFiles = []string{"css/tokens.css", "css/base.css", "css/covers.css", "css/views.css"}
var jsFiles = []string{"js/theme.js", "js/ui.js", "js/api.js", "js/sound.js", "js/render.js", "js/flow.js", "js/app.js"}

func readAll(t *testing.T, names []string) string {
	var b strings.Builder
	for _, n := range names {
		b.WriteString(readFS(t, n))
	}
	return b.String()
}

// TestNoEmoji 断言前端界面不使用 emoji（几何图标替代）。
func TestNoEmoji(t *testing.T) {
	html := readFS(t, "index.html")
	css := readAll(t, cssFiles)
	js := readAll(t, jsFiles)
	emoji := regexp.MustCompile(`[\x{1F000}-\x{1FAFF}\x{2600}-\x{27BF}\x{2B00}-\x{2BFF}\x{FE0F}]`)
	for _, f := range []struct{ name, data string }{
		{"index.html", html}, {"css", css}, {"js", js},
	} {
		if emoji.MatchString(f.data) {
			t.Fatalf("%s 含 emoji（约定用几何图形替代）", f.name)
		}
	}
}

// TestAppJSAPIContract 断言前端零 Go、纯 fetch、只走 /api/*、不请求外部。
func TestAppJSAPIContract(t *testing.T) {
	js := readAll(t, jsFiles)
	if strings.Contains(js, "http://") || strings.Contains(js, "https://") {
		t.Fatal("前端 JS 不得请求外部地址")
	}
	if !strings.Contains(js, "/api/") {
		t.Fatal("前端 JS 必须通过 /api/* 调用后端")
	}
}

// TestThemeSystem 断言主题系统存在：双态 day/night + data-theme 应用。
func TestThemeSystem(t *testing.T) {
	js := readFS(t, "js/theme.js")
	if !strings.Contains(js, "window.theme") {
		t.Fatal("theme.js 需暴露 window.theme")
	}
	for _, theme := range []string{"day", "night"} {
		if !strings.Contains(js, "'"+theme+"'") && !strings.Contains(js, "\""+theme+"\"") {
			t.Fatalf("theme.js 缺少主题 %s", theme)
		}
	}
	css := readFS(t, "css/tokens.css")
	if !strings.Contains(css, `[data-theme="night"]`) {
		t.Fatal("tokens.css 必须定义 night 主题变体")
	}
}

// TestFlowHandlesAllEvents 断言 flow.js 处理后端定义的全部 6 种 SSE 事件 type。
func TestFlowHandlesAllEvents(t *testing.T) {
	flow := readFS(t, "js/flow.js")
	for _, typ := range []string{"start", "progress", "done", "bookmark", "finished", "error"} {
		if !strings.Contains(flow, "'"+typ+"'") && !strings.Contains(flow, "\""+typ+"\"") {
			t.Fatalf("flow.js 缺少 SSE 事件处理: %s", typ)
		}
	}
	if !strings.Contains(flow, "onerror") {
		t.Fatal("flow.js 必须注册 SSE onerror")
	}
}

// TestDragDeleteContract 断言书架拖拽删除契约：shelfCard draggable + trash-zone drop + deleteBook API。
func TestDragDeleteContract(t *testing.T) {
	api := readFS(t, "js/api.js")
	render := readFS(t, "js/render.js")
	flow := readFS(t, "js/flow.js")
	html := readFS(t, "index.html")
	if !strings.Contains(api, "deleteBook") || !strings.Contains(api, "/api/shelf/delete") {
		t.Fatal("api.js 必须暴露 deleteBook 且调用 /api/shelf/delete")
	}
	if !strings.Contains(render, "draggable = true") || !strings.Contains(render, "text/bookId") {
		t.Fatal("render.js shelfCard 必须 draggable 且 dragstart 携带 text/bookId")
	}
	if !strings.Contains(flow, "trashZone") || !strings.Contains(flow, "text/bookId") || !strings.Contains(flow, "deleteBook") {
		t.Fatal("flow.js 必须绑定 trashZone 接收 drop 并调 deleteBook")
	}
	if !strings.Contains(html, "trashZone") || !strings.Contains(html, "ic-trash") {
		t.Fatal("index.html 必须含 trashZone drop zone 与 ic-trash 图标")
	}
}

// TestCoversNoRainbow 断言封面完全由主题 token 驱动，无独立高饱和色。
func TestCoversNoRainbow(t *testing.T) {
	css := readFS(t, "css/covers.css")
	hex := regexp.MustCompile(`#[0-9a-fA-F]{6}`)
	if hex.MatchString(css) {
		t.Fatal("covers.css 不得含独立 hex 色（应全部用 var(--token)）")
	}
	if !strings.Contains(css, "var(--") {
		t.Fatal("covers.css 必须使用主题 token")
	}
}
