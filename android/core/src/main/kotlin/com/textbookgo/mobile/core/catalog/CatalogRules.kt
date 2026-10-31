package com.textbookgo.mobile.core.catalog

import com.textbookgo.mobile.core.model.Book
import com.textbookgo.mobile.core.model.CatalogEntry
import java.util.Locale

/**
 * 教材目录的纯规则层，对应旧版 internal/api/catalog.go 与 internal/app/catalog.go。
 *
 * 与旧版一致的部分：
 * - 学科匹配使用标签名包含，兼容“英语（三年级起点）”等变体；
 * - 年级按固定顺序输出；
 * - 未下载教材排在已下载之前，再按标题升序。
 *
 * 在旧版基础上的修正（真实目录数据驱动）：
 * - 平台把“中国历史 / 世界历史”等学科标签误放进年级维度，年级列表需剔除学科名；
 * - 高中使用“高中年级 / 高一年级 / 高二年级”，统一归一为“高中 / 高一 / 高二”；
 * - 年级匹配改为“归一化后前缀匹配”，避免“高一年级”被“一年级”命中；
 * - 学科按常用程度排序（数学、语文、英语、物理、化学、生物学优先）；
 * - 新增“版本”维度（zxxbb）用于区分人教版/统编版等。
 */
object CatalogRules {

    const val GRADE_DIMENSION = "zxxnj"
    const val SUBJECT_DIMENSION = "zxxxk"
    const val VERSION_DIMENSION = "zxxbb"
    const val VOLUME_DIMENSION = "zxxcc"
    const val STAGE_DIMENSION = "zxxxd"

    /** 版本栏固定置顶的版本（“全部”之后第一个）。 */
    const val PINNED_VERSION = "人教版"

    val STANDARD_GRADE_ORDER: List<String> = listOf(
        "一年级", "二年级", "三年级", "四年级", "五年级", "六年级",
        "七年级", "八年级", "九年级",
        "高一", "高二", "高三", "高中",
    )

    /** 平台少数条目的年级写法不统一，展示与筛选前先归一。 */
    private val GRADE_ALIASES: Map<String, String> = mapOf(
        "高一年级" to "高一",
        "高二年级" to "高二",
        "高三年级" to "高三",
        "高中年级" to "高中",
    )

    /** 常用学科优先，其余按名称稳定排序。 */
    val SUBJECT_ORDER: List<String> = listOf(
        "数学", "语文", "英语", "物理", "化学", "生物学",
        "道德与法治", "历史", "地理", "科学", "思想政治",
        "信息技术", "信息科技", "通用技术", "体育与健康",
        "音乐", "美术", "艺术", "艺术·音乐", "艺术·美术",
        "日语", "俄语", "德语", "法语", "西班牙语",
        "中国历史", "世界历史", "语文·书法练习指导", "英语（三年级起点）",
        "地理图册", "美工", "沟通与交往", "生活语文", "生活数学", "生活适应",
        "律动", "艺术·舞蹈/影视/戏剧",
    )

    /**
     * 从目录条目中提取年级：标准顺序在前，未知年级稳定追加。
     *
     * 学科标签被误放进年级维度时（真实数据中的“中国历史”“世界历史”），
     * 这里以“该名称是否也作为学科出现”为准剔除，避免年级栏出现学科。
     */
    fun grades(entries: List<CatalogEntry>): List<String> {
        val subjectNames = entries.asSequence()
            .flatMap { it.tags.asSequence() }
            .filter { it.dimensionId == SUBJECT_DIMENSION }
            .map { it.name }
            .toSet()

        val seen = entries.asSequence()
            .flatMap { it.tags.asSequence() }
            .filter { it.dimensionId == GRADE_DIMENSION }
            .map { normalizeGrade(it.name) }
            .filter { it.isNotBlank() && it !in subjectNames }
            .filterNot { isGradeRange(it) }
            .toSet()

        val standard = STANDARD_GRADE_ORDER.filter { it in seen }
        val standardSet = standard.toSet()
        val unknown = seen.filterNot { it in standardSet }.sorted()
        return standard + unknown
    }

    /** 指定年级下的学科名：常用学科优先，其余按名称排序。 */
    fun subjects(entries: List<CatalogEntry>, grade: String): List<String> =
        entries.asSequence()
            .filter { it.hasGrade(grade) }
            .flatMap { it.tags.asSequence() }
            .filter { it.dimensionId == SUBJECT_DIMENSION }
            .map { it.name }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedWith(subjectComparator)
            .toList()

    /**
     * 指定年级 + 学科下出现过的版本：人教版固定排在最前（界面上“全部”之后），
     * 其余按教材数量降序、名称升序。
     */
    fun versions(entries: List<CatalogEntry>, grade: String, subject: String): List<String> {
        if (grade.isBlank() || subject.isBlank()) return emptyList()
        val counts = linkedMapOf<String, Int>()
        entries.asSequence()
            .filter { it.hasGrade(grade) && it.hasSubject(subject) }
            .forEach { entry ->
                entry.tags.asSequence()
                    .filter { it.dimensionId == VERSION_DIMENSION }
                    .map { it.name }
                    .filter { it.isNotBlank() }
                    .forEach { version -> counts[version] = (counts[version] ?: 0) + 1 }
            }
        return counts.entries
            .sortedWith(
                compareBy<Map.Entry<String, Int>> { if (it.key == PINNED_VERSION) 0 else 1 }
                    .thenByDescending { it.value }
                    .thenBy { it.key },
            )
            .map { it.key }
    }

    /** 指定年级、学科与版本下的教材，含版本/册次、已下载标记与稳定排序。 */
    fun books(
        entries: List<CatalogEntry>,
        grade: String,
        subject: String,
        downloadedIds: Set<String>,
        version: String = "",
    ): List<Book> = entries.asSequence()
        .filter {
            it.hasGrade(grade) &&
                it.hasSubject(subject) &&
                (version.isBlank() || it.hasVersion(version))
        }
        .map { entry ->
            Book(
                id = entry.id,
                title = displayTitle(entry.title),
                version = entry.firstTagName(VERSION_DIMENSION),
                volume = entry.firstTagName(VOLUME_DIMENSION),
                coverUrl = entry.coverUrl,
                downloaded = entry.id in downloadedIds,
                edition2022 = is2022Edition(entry.title),
            )
        }
        // 2022 年修订版排在当前分类最前面，其次才是已下载与书名。
        .sortedWith(
            compareByDescending<Book> { it.edition2022 }
                .thenBy { it.downloaded }
                .thenBy { it.title },
        )
        .toList()

    /** 是否带“根据2022年版课程标准修订”说明。 */
    fun is2022Edition(title: String): Boolean = title.contains(EDITION_2022_NOTE)

    /**
     * 卡片上展示的书名：去掉“义务教育教科书”前缀与 2022 年修订说明，
     * 说明本身改由卡片上的“2022年”标签表达。
     */
    fun displayTitle(raw: String): String {
        if (raw.isBlank()) return raw
        var title = raw
            .replace(Regex("""\s*[（(]\s*$EDITION_2022_NOTE\s*[）)]"""), "")
            .replace(EDITION_2022_NOTE, "")
            .replace(Regex("""\s*义务教育教科书\s*[·•・、,，.．\-—]?\s*"""), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
        title = title.trim('·', '•', '・', '、', '-', '—', '，', ',', ' ')
        return title.ifBlank { raw }
    }

    /** 当前分类内的书名搜索：trim、大小写不敏感子串；空查询视为全部匹配。 */
    fun titleMatches(title: String, query: String): Boolean {
        val needle = query.trim().lowercase(Locale.ROOT)
        if (needle.isEmpty()) return true
        return title.lowercase(Locale.ROOT).contains(needle)
    }

    /** 为 SQLite LIKE 转义用户输入，避免 % 和 _ 变成通配符。 */
    fun escapeLike(query: String): String = buildString(query.length) {
        query.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '%' -> append("\\%")
                '_' -> append("\\_")
                else -> append(char)
            }
        }
    }

    fun normalizeGrade(name: String): String = GRADE_ALIASES[name] ?: name

    /** “一至二年级 / 七至九年级”等跨年级范围标签不是可选年级，展示时剔除。 */
    private val GRADE_RANGE = Regex("^[一二三四五六七八九十0-9]+\\s*至\\s*[一二三四五六七八九十0-9]+年级$")

    /** 2022 年版课程标准修订说明，展示时从书名中移除。 */
    private const val EDITION_2022_NOTE = "根据2022年版课程标准修订"

    private fun isGradeRange(name: String): Boolean = GRADE_RANGE.matches(name)

    private val subjectComparator = Comparator<String> { left, right ->
        val leftRank = SUBJECT_ORDER.indexOf(left)
        val rightRank = SUBJECT_ORDER.indexOf(right)
        when {
            leftRank >= 0 && rightRank >= 0 -> leftRank.compareTo(rightRank)
            leftRank >= 0 -> -1
            rightRank >= 0 -> 1
            else -> left.compareTo(right)
        }
    }

    private fun CatalogEntry.hasGrade(grade: String): Boolean {
        val target = normalizeGrade(grade)
        if (target.isBlank()) return false
        return tags.any {
            it.dimensionId == GRADE_DIMENSION && normalizeGrade(it.name).startsWith(target)
        }
    }

    private fun CatalogEntry.hasSubject(subject: String): Boolean =
        subject.isBlank() || tags.any {
            it.dimensionId == SUBJECT_DIMENSION && it.name.contains(subject)
        }

    private fun CatalogEntry.hasVersion(version: String): Boolean =
        tags.any { it.dimensionId == VERSION_DIMENSION && it.name == version }

    private fun CatalogEntry.firstTagName(dimensionId: String): String =
        tags.firstOrNull { it.dimensionId == dimensionId }?.name.orEmpty()
}
