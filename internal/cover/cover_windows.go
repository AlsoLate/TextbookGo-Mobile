//go:build windows

// Package cover 把教材 PDF 第 1 页渲染成封面 PNG。
// 纯 Go：pdfium.dll 通过 go:embed 内嵌，运行时释放到临时目录后动态加载，无 CGo 依赖。
// 仅 Windows：pdfium 内嵌的是 win 版二进制；其他平台见 cover_other.go。
package cover

import (
	"bytes"
	_ "embed"
	"image"
	"image/png"
	"os"
	"path/filepath"
	"runtime"
	"sync"
	"syscall"
	"unsafe"
)

//go:embed pdfium.dll
var libPDFium []byte

var (
	once      sync.Once
	initOnce  sync.Once
	initErr   error
	dll       *syscall.DLL
	procs     = map[string]*syscall.Proc{}
	renderMu  sync.Mutex // pdfium 非全线程安全，串行化渲染
)

func ensureLib() error {
	once.Do(func() {
		dir := os.TempDir()
		path := filepath.Join(dir, "pdfium.dll")
		if _, e := os.Stat(path); e != nil {
			if e2 := os.WriteFile(path, libPDFium, 0o644); e2 != nil {
				initErr = e2
				return
			}
		}
		dll, initErr = syscall.LoadDLL(path)
	})
	return initErr
}

// dataPtr 返回切片数据地址（供 pdfium 读取内存中的 PDF）。
func dataPtr(b []byte) uintptr {
	return uintptr(unsafe.Pointer(unsafe.SliceData(b)))
}

func fp(name string) (*syscall.Proc, error) {
	if err := ensureLib(); err != nil {
		return nil, err
	}
	if p, ok := procs[name]; ok {
		return p, nil
	}
	p, err := dll.FindProc(name)
	if err != nil {
		return nil, err
	}
	procs[name] = p
	return p, nil
}

// Render 渲染 PDF 第 1 页为 PNG 字节。
func Render(pdfBytes []byte) ([]byte, error) {
	if err := ensureLib(); err != nil {
		return nil, err
	}
	renderMu.Lock()
	defer renderMu.Unlock()
	initOnce.Do(func() { call("FPDF_InitLibrary") })

	doc, err := call("FPDF_LoadMemDocument", dataPtr(pdfBytes), uintptr(len(pdfBytes)), 0)
	if err != nil {
		return nil, err
	}
	defer runtime.KeepAlive(pdfBytes) // pdfium 不复制 buffer，须存活到 CloseDocument 之后
	defer call("FPDF_CloseDocument", doc)

	page, err := call("FPDF_LoadPage", doc, 0)
	if err != nil {
		return nil, err
	}
	defer call("FPDF_ClosePage", page)

	// 固定 A4 长宽比渲染（教材页基本是 A4≈0.707，失真可忽略）
	const W, H = 660, 933
	buf := make([]byte, W*H*4)
	bmp, err := call("FPDFBitmap_CreateEx", W, H, 3, dataPtr(buf), W*4)
	if err != nil {
		return nil, err
	}
	defer call("FPDFBitmap_Destroy", bmp)

	call("FPDFBitmap_FillRect", bmp, 0, 0, W, H, uintptr(0xFFFFFFFF))
	call("FPDF_RenderPageBitmap", bmp, page, 0, 0, W, H, 0, 0)

	return encodeBGRA(buf, W, H)
}

// encodeBGRA 把 pdfium 的 BGRA 像素转成 PNG（buf 为 Go 侧分配，无 unsafe）。
func encodeBGRA(buf []byte, W, H int) ([]byte, error) {
	img := image.NewRGBA(image.Rect(0, 0, W, H))
	for i := 0; i < W*H; i++ {
		img.Pix[i*4+0] = buf[i*4+2]
		img.Pix[i*4+1] = buf[i*4+1]
		img.Pix[i*4+2] = buf[i*4+0]
		img.Pix[i*4+3] = buf[i*4+3]
	}
	var out bytes.Buffer
	if err := png.Encode(&out, img); err != nil {
		return nil, err
	}
	return out.Bytes(), nil
}

// call 调用返回 uintptr 的 pdfium 函数。
//go:uintptrescapes
func call(name string, args ...uintptr) (uintptr, error) {
	p, err := fp(name)
	if err != nil {
		return 0, err
	}
	r, _, _ := p.Call(args...)
	return r, nil
}