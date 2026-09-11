package models

import "strings"

// TagBase 标签基础信息
type TagBase struct {
	ID          string `json:"id"`
	TagName     string `json:"tag_name"`
	ParentTagID string `json:"parent_tag_id"`
	TagType     string `json:"tag_type"`
	Sort        int    `json:"sort"`
}

// TagItem 标签项，包含子标签
type TagItem struct {
	TagBase
	Children []TagItem `json:"children,omitempty"`
}

// DataVersion 教材列表版本信息
// 注意: API 返回的 urls 是逗号分隔的字符串，不是数组
type DataVersion struct {
	Module       string `json:"module"`
	ModuleVersion int64 `json:"module_version"`
	URLsRaw      string `json:"urls"`
}

// GetURLs 将逗号分隔的 URL 字符串解析为切片
func (d *DataVersion) GetURLs() []string {
	if d.URLsRaw == "" {
		return nil
	}
	urls := make([]string, 0)
	for _, u := range splitAndTrim(d.URLsRaw, ",") {
		if u != "" {
			urls = append(urls, u)
		}
	}
	return urls
}

func splitAndTrim(s, sep string) []string {
	parts := make([]string, 0)
	for _, p := range strings.SplitN(s, sep, -1) {
		parts = append(parts, strings.TrimSpace(p))
	}
	return parts
}

// ResourceItem 资源详情
type ResourceItem struct {
	ID               string             `json:"id"`
	Title            string             `json:"title"`
	TiItems          []TiItem           `json:"ti_items"`
	TagsList         []string           `json:"tags_list"`
	CustomProperties CustomProperties   `json:"custom_properties"`
}

// CustomProperties 平台资源自定义属性，含封面缩略图与预览图。
type CustomProperties struct {
	Thumbnails []string          `json:"thumbnails"`
	Preview    map[string]string `json:"preview"`
}

// TiItem 资源文件信息
type TiItem struct {
	TiStorage  string   `json:"ti_storage"`
	TiStorages []string `json:"ti_storages"`
	TiFileFlag string   `json:"ti_file_flag"`
	TiIsSource bool     `json:"ti_is_source_file"`
	TiFormat   string   `json:"ti_format"`
	TiMD5      string   `json:"ti_md5"`
	TiSize     int64    `json:"ti_size"`
}

// TiStorageRecord 存储记录
// ti_storages 在 API 中是字符串数组，不是对象
// 但保留此类型以兼容可能的对象格式

// CatalogEntry 教材目录条目
type CatalogEntry struct {
	ID               string           `json:"id"`
	Title            string           `json:"title"`
	TagList          []Tag            `json:"tag_list"`
	CustomProperties CustomProperties `json:"custom_properties"`
}

// CoverURL 返回平台封面图 URL：优先 thumbnails[0]，回退 preview.Slide1。
func (c *CatalogEntry) CoverURL() string {
	if len(c.CustomProperties.Thumbnails) > 0 {
		return c.CustomProperties.Thumbnails[0]
	}
	return c.CustomProperties.Preview["Slide1"]
}

// Tag 标签信息（来自 tag_list）
type Tag struct {
	TagID         string `json:"tag_id"`
	TagName       string `json:"tag_name"`
	TagDimensionID string `json:"tag_dimension_id"`
}

// GetTagNames 返回所有标签名称
func (c *CatalogEntry) GetTagNames() []string {
	names := make([]string, 0, len(c.TagList))
	for _, t := range c.TagList {
		names = append(names, t.TagName)
	}
	return names
}

// GetTagByDimension 按维度 ID 获取标签名
func (c *CatalogEntry) GetTagByDimension(dimID string) string {
	for _, t := range c.TagList {
		if t.TagDimensionID == dimID {
			return t.TagName
		}
	}
	return ""
}

// TagByType 按类型分组标签
func TagByType(tags []TagItem, tagType string) []TagItem {
	var result []TagItem
	for _, t := range tags {
		if t.TagType == tagType {
			result = append(result, t)
		}
	}
	return result
}
