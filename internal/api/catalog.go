package api

import (
	"encoding/json"
	"fmt"
	"sort"
	"strings"
	"sync"

	"github.com/Wing900/TextbookGo/internal/models"
)

// FetchCatalog 获取完整的教材目录
// 1. 获取数据版本信息（URL 列表）
// 2. 并发获取所有数据文件
// 3. 合并结果
func FetchCatalog(client *Client) ([]models.CatalogEntry, error) {
	// Step 1: 获取版本信息
	versionURL := DataVersionURL()
	versionData, err := client.GetJSON(versionURL)
	if err != nil {
		return nil, fmt.Errorf("获取数据版本失败: %w", err)
	}

	var version models.DataVersion
	if err := json.Unmarshal(versionData, &version); err != nil {
		return nil, fmt.Errorf("解析数据版本失败: %w", err)
	}

	urls := version.GetURLs()
	if len(urls) == 0 {
		return nil, fmt.Errorf("数据版本中无 URL 列表")
	}

	// Step 2: 并发获取所有数据文件
	var (
		mu        sync.Mutex
		wg        sync.WaitGroup
		allEntrys []models.CatalogEntry
		errs      []string
	)

	sem := make(chan struct{}, 5) // 最多 5 个并发

	for _, dataURL := range urls {
		wg.Add(1)
		go func(url string) {
			defer wg.Done()
			sem <- struct{}{}
			defer func() { <-sem }()

			// 将 s-file-N 替换为随机服务器
			actualURL := url
			for _, server := range ServerList {
				if strings.Contains(url, server+".ykt.cbern.com.cn") {
					actualURL = strings.Replace(url, server+".ykt.cbern.com.cn",
						RandomServer()+".ykt.cbern.com.cn", 1)
					break
				}
			}

			data, err := client.GetJSON(actualURL)
			if err != nil {
				mu.Lock()
				errs = append(errs, fmt.Sprintf("获取 %s 失败: %v", url, err))
				mu.Unlock()
				return
			}

			var entries []models.CatalogEntry
			if err := json.Unmarshal(data, &entries); err != nil {
				mu.Lock()
				errs = append(errs, fmt.Sprintf("解析 %s 失败: %v", url, err))
				mu.Unlock()
				return
			}

			mu.Lock()
			allEntrys = append(allEntrys, entries...)
			mu.Unlock()
		}(dataURL)
	}

	wg.Wait()

	if len(allEntrys) == 0 && len(errs) > 0 {
		return nil, fmt.Errorf("获取教材目录失败: %s", strings.Join(errs, "; "))
	}

	return allEntrys, nil
}

// 标签维度 ID 常量
const (
	DimXueDuan = "zxxxd" // 学段: 小学/初中/高中
	DimNianJi  = "zxxnj" // 年级: 一年级/二年级/...
	DimXueKe   = "zxxxk" // 学科: 数学/语文/...
	DimCeCi    = "zxxcc" // 册次: 上册/下册
	DimBanBen  = "zxxbb" // 版本: 统编版/人教A版/...
)

// FilterByGrade 按年级筛选教材
func FilterByGrade(entries []models.CatalogEntry, grade string) []models.CatalogEntry {
	var result []models.CatalogEntry
	for _, entry := range entries {
		for _, tag := range entry.TagList {
			if tag.TagDimensionID == DimNianJi && strings.Contains(tag.TagName, grade) {
				result = append(result, entry)
				break
			}
		}
	}
	return result
}

// FilterBySubject 按学科筛选教材
func FilterBySubject(entries []models.CatalogEntry, subject string) []models.CatalogEntry {
	var result []models.CatalogEntry
	for _, entry := range entries {
		for _, tag := range entry.TagList {
			if tag.TagDimensionID == DimXueKe && strings.Contains(tag.TagName, subject) {
				result = append(result, entry)
				break
			}
		}
	}
	return result
}

// GetGrades 从教材目录中提取所有年级
func GetGrades(entries []models.CatalogEntry) []string {
	gradeOrder := []string{
		"一年级", "二年级", "三年级", "四年级", "五年级", "六年级",
		"七年级", "八年级", "九年级",
		"高一", "高二", "高三",
	}

	seen := make(map[string]bool)
	for _, entry := range entries {
		for _, tag := range entry.TagList {
			if tag.TagDimensionID == DimNianJi && !seen[tag.TagName] {
				seen[tag.TagName] = true
			}
		}
	}

	var grades []string
	for _, g := range gradeOrder {
		if seen[g] {
			grades = append(grades, g)
		}
	}

	// 添加未在预定义列表中的年级
	for g := range seen {
		found := false
		for _, og := range gradeOrder {
			if g == og {
				found = true
				break
			}
		}
		if !found {
			grades = append(grades, g)
		}
	}

	return grades
}

// GetSubjects 从教材目录中提取指定年级的所有学科
func GetSubjects(entries []models.CatalogEntry, grade string) []string {
	seen := make(map[string]bool)
	var subjects []string

	for _, entry := range entries {
		// 检查是否属于该年级
		hasGrade := false
		for _, tag := range entry.TagList {
			if tag.TagDimensionID == DimNianJi && strings.Contains(tag.TagName, grade) {
				hasGrade = true
				break
			}
		}
		if !hasGrade {
			continue
		}

		// 提取学科
		for _, tag := range entry.TagList {
			if tag.TagDimensionID == DimXueKe && !seen[tag.TagName] {
				subjects = append(subjects, tag.TagName)
				seen[tag.TagName] = true
			}
		}
	}

	return subjects
}

// FirstCatalogEntry 返回一个稳定的示例条目，用于后续鉴权预检。
func FirstCatalogEntry(entries []models.CatalogEntry) (models.CatalogEntry, bool) {
	if len(entries) == 0 {
		return models.CatalogEntry{}, false
	}
	copied := append([]models.CatalogEntry(nil), entries...)
	sort.SliceStable(copied, func(i, j int) bool {
		if copied[i].Title != copied[j].Title {
			return copied[i].Title < copied[j].Title
		}
		return copied[i].ID < copied[j].ID
	})
	return copied[0], true
}
