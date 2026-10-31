package com.textbookgo.mobile.core.catalog

import com.google.common.truth.Truth.assertThat
import com.textbookgo.mobile.core.fixtures.Fixtures
import org.junit.Test

class CatalogRulesTest {

    @Test
    fun `grades use standard order and append unknown grades`() {
        val entries = listOf(
            Fixtures.entry("g1", "高一数学", grade = "高一"),
            Fixtures.entry("g2", "七年级数学", grade = "七年级"),
            Fixtures.entry("g3", "一年级数学", grade = "一年级"),
            Fixtures.entry("g4", "大学数学", grade = "大学"),
        )

        assertThat(CatalogRules.grades(entries))
            .containsExactly("一年级", "七年级", "高一", "大学")
            .inOrder()
    }

    @Test
    fun `unknown grades are appended in stable string order`() {
        val entries = listOf(
            Fixtures.entry("a", "A", grade = "B年级"),
            Fixtures.entry("b", "B", grade = "A年级"),
            Fixtures.entry("c", "C", grade = "七年级"),
        )

        assertThat(CatalogRules.grades(entries)).containsExactly("七年级", "A年级", "B年级").inOrder()
    }

    @Test
    fun `grades are distinct`() {
        val entries = listOf(
            Fixtures.entry("1", "a", grade = "七年级"),
            Fixtures.entry("2", "b", grade = "七年级"),
        )

        assertThat(CatalogRules.grades(entries)).containsExactly("七年级")
    }

    @Test
    fun `subjects follow the preferred order and are scoped to the grade`() {
        val entries = listOf(
            Fixtures.entry("1", "数学", grade = "七年级", subject = "数学"),
            Fixtures.entry("2", "语文", grade = "七年级", subject = "语文"),
            Fixtures.entry("3", "英语", grade = "七年级", subject = "英语"),
            Fixtures.entry("4", "物理", grade = "八年级", subject = "物理"),
            Fixtures.entry("5", "历史", grade = "七年级", subject = "历史"),
        )

        assertThat(CatalogRules.subjects(entries, "七年级"))
            .containsExactly("数学", "语文", "英语", "历史")
            .inOrder()
    }

    @Test
    fun `subjects put common subjects first and unknown ones last`() {
        val entries = listOf(
            Fixtures.entry("1", "A", grade = "七年级", subject = "律动"),
            Fixtures.entry("2", "B", grade = "七年级", subject = "生物学"),
            Fixtures.entry("3", "C", grade = "七年级", subject = "物理"),
            Fixtures.entry("4", "D", grade = "七年级", subject = "化学"),
        )

        assertThat(CatalogRules.subjects(entries, "七年级"))
            .containsExactly("物理", "化学", "生物学", "律动")
            .inOrder()
    }

    @Test
    fun `grades normalize high school aliases`() {
        val entries = listOf(
            Fixtures.entry("1", "a", grade = "高中年级"),
            Fixtures.entry("2", "b", grade = "高一年级"),
            Fixtures.entry("3", "c", grade = "高二年级"),
            Fixtures.entry("4", "d", grade = "七年级"),
        )

        assertThat(CatalogRules.grades(entries))
            .containsExactly("七年级", "高一", "高二", "高中")
            .inOrder()
    }

    @Test
    fun `grade ranges are not offered as grades`() {
        val entries = listOf(
            Fixtures.entry("1", "a", grade = "一至二年级"),
            Fixtures.entry("2", "b", grade = "三至四年级"),
            Fixtures.entry("3", "c", grade = "七至九年级"),
            Fixtures.entry("4", "d", grade = "七年级"),
        )

        assertThat(CatalogRules.grades(entries)).containsExactly("七年级")
    }

    @Test
    fun `grades exclude tags that are actually subjects`() {
        val entries = listOf(
            Fixtures.entry("1", "a", grade = "七年级", subject = "历史"),
            Fixtures.entry("2", "b", grade = "中国历史", subject = "中国历史"),
            Fixtures.entry("3", "c", grade = "世界历史", subject = "世界历史"),
        )

        assertThat(CatalogRules.grades(entries)).containsExactly("七年级")
    }

    @Test
    fun `grade matching does not leak high school into grade one`() {
        val entries = listOf(
            Fixtures.entry("1", "a", grade = "一年级", subject = "数学"),
            Fixtures.entry("2", "b", grade = "高一年级", subject = "数学"),
        )

        val books = CatalogRules.books(entries, "一年级", "数学", emptySet())

        assertThat(books.map { it.id }).containsExactly("1")
    }

    @Test
    fun `versions pin 人教版 first then order by book count`() {
        val entries = listOf(
            Fixtures.entry("1", "a", grade = "七年级", subject = "数学", version = "人教版"),
            Fixtures.entry("2", "b", grade = "七年级", subject = "数学", version = "北师大版"),
            Fixtures.entry("3", "c", grade = "七年级", subject = "数学", version = "北师大版"),
            Fixtures.entry("4", "d", grade = "八年级", subject = "数学", version = "苏教版"),
        )

        assertThat(CatalogRules.versions(entries, "七年级", "数学"))
            .containsExactly("人教版", "北师大版")
            .inOrder()
    }

    @Test
    fun `books filter by exact version`() {
        val entries = listOf(
            Fixtures.entry("1", "a", grade = "七年级", subject = "数学", version = "人教版"),
            Fixtures.entry("2", "b", grade = "七年级", subject = "数学", version = "人教版（主编：吴欣）"),
            Fixtures.entry("3", "c", grade = "七年级", subject = "数学", version = "北师大版"),
        )

        assertThat(
            CatalogRules.books(entries, "七年级", "数学", emptySet(), version = "人教版")
                .map { it.id },
        ).containsExactly("1")
    }

    @Test
    fun `grade matching keeps legacy contains semantics`() {
        val entries = listOf(
            Fixtures.entry("1", "一年级上册", grade = "一年级上", subject = "数学"),
        )

        assertThat(CatalogRules.subjects(entries, "一年级")).containsExactly("数学")
    }

    @Test
    fun `books filter by grade and subject`() {
        val entries = listOf(
            Fixtures.entry("1", "七年级数学", grade = "七年级", subject = "数学"),
            Fixtures.entry("2", "八年级数学", grade = "八年级", subject = "数学"),
            Fixtures.entry("3", "七年级语文", grade = "七年级", subject = "语文"),
        )

        val books = CatalogRules.books(entries, "七年级", "数学", emptySet())

        assertThat(books.map { it.id }).containsExactly("1")
    }

    @Test
    fun `books put downloaded last then sort by title`() {
        val entries = listOf(
            Fixtures.entry("1", "B 教材", grade = "七年级", subject = "数学"),
            Fixtures.entry("2", "A 教材", grade = "七年级", subject = "数学"),
            Fixtures.entry("3", "C 教材", grade = "七年级", subject = "数学"),
        )

        val books = CatalogRules.books(entries, "七年级", "数学", downloadedIds = setOf("3"))

        assertThat(books.map { it.title }).containsExactly("A 教材", "B 教材", "C 教材").inOrder()
        assertThat(books.last().downloaded).isTrue()
    }

    @Test
    fun `books expose version volume and cover`() {
        val entries = listOf(
            Fixtures.entry(
                id = "1",
                title = "数学",
                grade = "七年级",
                subject = "数学",
                version = "人教版",
                volume = "上册",
                coverUrl = "https://c1.ykt.cbern.com.cn/c.png",
            ),
        )

        val book = CatalogRules.books(entries, "七年级", "数学", emptySet()).single()

        assertThat(book.version).isEqualTo("人教版")
        assertThat(book.volume).isEqualTo("上册")
        assertThat(book.coverUrl).isEqualTo("https://c1.ykt.cbern.com.cn/c.png")
    }

    @Test
    fun `title search trims and ignores case`() {
        assertThat(CatalogRules.titleMatches("Mathematics", "  MATH  ")).isTrue()
        assertThat(CatalogRules.titleMatches("数学 七年级", "数学")).isTrue()
        assertThat(CatalogRules.titleMatches("数学 七年级", "语文")).isFalse()
    }

    @Test
    fun `display title drops textbook prefix and 2022 revision note`() {
        assertThat(
            CatalogRules.displayTitle("义务教育教科书·英语九年级下册（根据2022年版课程标准修订）"),
        ).isEqualTo("英语九年级下册")
        assertThat(CatalogRules.displayTitle("义务教育教科书 数学 七年级 上册"))
            .isEqualTo("数学 七年级 上册")
        assertThat(CatalogRules.is2022Edition("义务教育教科书·数学（根据2022年版课程标准修订）"))
            .isTrue()
        // 培智/盲校教材的“义务教育实验教科书”不是要去掉的前缀。
        assertThat(CatalogRules.displayTitle("培智学校义务教育实验教科书 生活数学 一年级 上册"))
            .isEqualTo("培智学校义务教育实验教科书 生活数学 一年级 上册")
    }

    @Test
    fun `2022 edition books sort first and carry the flag`() {
        val entries = listOf(
            Fixtures.entry("1", "A 教材", grade = "七年级", subject = "数学"),
            Fixtures.entry(
                "2",
                "B 教材（根据2022年版课程标准修订）",
                grade = "七年级",
                subject = "数学",
            ),
        )

        val books = CatalogRules.books(entries, "七年级", "数学", emptySet())

        assertThat(books.map { it.title }).containsExactly("B 教材", "A 教材").inOrder()
        assertThat(books.first().edition2022).isTrue()
        assertThat(books.last().edition2022).isFalse()
    }

    @Test
    fun `blank query matches everything`() {
        assertThat(CatalogRules.titleMatches("任意教材", "   ")).isTrue()
    }

    @Test
    fun `like escaping neutralizes wildcards`() {
        assertThat(CatalogRules.escapeLike("100%_教材\\")).isEqualTo("100\\%\\_教材\\\\")
        assertThat(CatalogRules.escapeLike("普通教材")).isEqualTo("普通教材")
    }

    @Test
    fun `fixture catalog entries are parsed through the public DTO`() {
        val entries = CatalogJson.parseEntries(Fixtures.read("catalog_entries.json"))

        assertThat(entries).hasSize(2)
        assertThat(entries[0].tagList.map { it.tagName })
            .containsExactly("七年级", "数学", "人教版", "上册")
        assertThat(entries[0].customProperties.thumbnails).hasSize(1)
    }
}
