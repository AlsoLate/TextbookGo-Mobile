package api

import (
	"encoding/json"
	"fmt"
	"strings"

	"github.com/Wing900/TextbookGo/internal/models"
)

// FetchTagHierarchy 获取标签分类树
func FetchTagHierarchy(client *Client) ([]models.TagItem, error) {
	url := TagsURL()
	data, err := client.GetJSON(url)
	if err != nil {
		return nil, fmt.Errorf("获取标签失败: %w", err)
	}

	var tags []models.TagItem
	if err := json.Unmarshal(data, &tags); err != nil {
		return nil, fmt.Errorf("解析标签失败: %w", err)
	}

	return tags, nil
}

// BuildTagTree 构建父子关系的标签树
// 输入是扁平的标签列表，输出是嵌套的树结构
func BuildTagTree(tags []models.TagItem) []models.TagItem {
	// 构建 ID 到标签的映射
	tagMap := make(map[string]*models.TagItem)
	for i := range tags {
		tagMap[tags[i].ID] = &tags[i]
	}

	// 收集所有标签到扁平列表
	allTags := collectAllTags(tags)

	// 重建映射
	flatMap := make(map[string]*models.TagItem)
	for i := range allTags {
		flatMap[allTags[i].ID] = &allTags[i]
	}

	// 构建树
	var roots []models.TagItem
	for _, tag := range allTags {
		if tag.ParentTagID == "" || tag.ParentTagID == "0" {
			roots = append(roots, tag)
		} else if parent, ok := flatMap[tag.ParentTagID]; ok {
			parent.Children = append(parent.Children, tag)
		}
	}

	return roots
}

// collectAllTags 递归收集所有标签到扁平列表
func collectAllTags(tags []models.TagItem) []models.TagItem {
	var result []models.TagItem
	for _, tag := range tags {
		result = append(result, models.TagItem{TagBase: tag.TagBase})
		if len(tag.Children) > 0 {
			result = append(result, collectAllTags(tag.Children)...)
		}
	}
	return result
}

// GetGradesFromTags 从标签树中提取年级列表
func GetGradesFromTags(tags []models.TagItem) []string {
	var grades []string
	seen := make(map[string]bool)
	for _, tag := range tags {
		if tag.TagType == "年级" || containsGrade(tag.TagName) {
			if !seen[tag.TagName] {
				grades = append(grades, tag.TagName)
				seen[tag.TagName] = true
			}
		}
		for _, child := range tag.Children {
			if child.TagType == "年级" || containsGrade(child.TagName) {
				if !seen[child.TagName] {
					grades = append(grades, child.TagName)
					seen[child.TagName] = true
				}
			}
		}
	}
	return grades
}

// containsGrade 检查名称是否包含年级信息
func containsGrade(name string) bool {
	gradeKeywords := []string{
		"一年级", "二年级", "三年级", "四年级", "五年级", "六年级",
		"七年级", "八年级", "九年级",
		"高一", "高二", "高三",
		"一年级上", "一年级下", "二年级上", "二年级下",
		"三年级上", "三年级下", "四年级上", "四年级下",
		"五年级上", "五年级下", "六年级上", "六年级下",
	}
	for _, kw := range gradeKeywords {
		if strings.Contains(name, kw) {
			return true
		}
	}
	return false
}
