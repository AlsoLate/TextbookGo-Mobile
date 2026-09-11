package api

import (
	"encoding/json"
	"fmt"
	"strings"

	"github.com/Wing900/TextbookGo/internal/models"
)

// FetchResourceDetails 获取资源详情
func FetchResourceDetails(client *Client, contentID string) (*models.ResourceItem, error) {
	url := DetailsURL(contentID)
	data, err := client.GetJSON(url)
	if err != nil {
		return nil, fmt.Errorf("获取资源详情失败 [%s]: %w", contentID, err)
	}

	var item models.ResourceItem
	if err := json.Unmarshal(data, &item); err != nil {
		return nil, fmt.Errorf("解析资源详情失败: %w", err)
	}

	return &item, nil
}

// ParseResourceURL 从 ti_items 中解析下载链接
// 优先选择 ti_is_source_file=true 的源文件
func ParseResourceURL(tiItems []models.TiItem, hasToken bool) (downloadURL, backupURL string, size int64) {
	// 优先查找 is_source_file 的文件
	for _, item := range tiItems {
		if item.TiIsSource && item.TiFileFlag != "ebook_mapping" {
			return resolveItemURLs(item, hasToken)
		}
	}

	// 回退：查找 PDF 格式
	for _, item := range tiItems {
		if item.TiFormat == "pdf" || item.TiFormat == "PDF" {
			return resolveItemURLs(item, hasToken)
		}
	}

	// 最后：第一个非 ebook_mapping 的文件
	for _, item := range tiItems {
		if item.TiFileFlag != "ebook_mapping" && item.TiStorage != "" {
			return resolveItemURLs(item, hasToken)
		}
	}

	return "", "", 0
}

// resolveItemURLs 解析单个 TiItem 的下载 URL
func resolveItemURLs(item models.TiItem, hasToken bool) (url, backup string, size int64) {
	url = ResolveCDNURL(item.TiStorage, hasToken)
	size = item.TiSize

	// 从 ti_storages（字符串数组）中找备用链接
	if len(item.TiStorages) > 0 {
		for _, s := range item.TiStorages {
			if s != "" {
				backup = s // ti_storages 中已经是完整 URL
				break
			}
		}
	}

	// 如果没有备用链接，用主链接替换域名生成
	if backup == "" && url != "" {
		backup = replaceCDNServer(url)
	}

	return url, backup, size
}

// replaceCDNServer 替换 URL 中的 CDN 服务器域名
func replaceCDNServer(rawURL string) string {
	for _, server := range ServerList {
		domain := server + ".ykt.cbern.com.cn"
		if strings.Contains(rawURL, domain) {
			alt := RandomServer() + ".ykt.cbern.com.cn"
			if alt == domain {
				for _, s := range ServerList {
					if s+".ykt.cbern.com.cn" != domain {
						alt = s + ".ykt.cbern.com.cn"
						break
					}
				}
			}
			return strings.Replace(rawURL, domain, alt, 1)
		}
	}
	return rawURL
}
