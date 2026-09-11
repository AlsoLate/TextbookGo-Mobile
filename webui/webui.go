// Package webui 通过 go:embed 打包前端静态资源，供 server 注入。
package webui

import "embed"

//go:embed frontend
var FS embed.FS
