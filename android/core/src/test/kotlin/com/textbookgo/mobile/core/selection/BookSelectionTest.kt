package com.textbookgo.mobile.core.selection

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BookSelectionTest {

    @Test
    fun `toggle adds an id`() {
        val selection = BookSelection().toggle("book-1")

        assertThat(selection.contains("book-1")).isTrue()
        assertThat(selection.count).isEqualTo(1)
    }

    @Test
    fun `toggling the same id removes it`() {
        val selection = BookSelection().toggle("book-1").toggle("book-1")

        assertThat(selection.contains("book-1")).isFalse()
        assertThat(selection.ids).isEmpty()
    }

    @Test
    fun `selection keeps ids across grades and subjects`() {
        val selection = BookSelection()
            .toggle("grade7-math-1")
            .toggle("grade8-chinese-2")
            .toggle("grade7-english-3")

        assertThat(selection.count).isEqualTo(3)
        assertThat(selection.submitIds())
            .containsExactly("grade7-math-1", "grade8-chinese-2", "grade7-english-3")
    }

    @Test
    fun `count equals unique submitted ids`() {
        val selection = BookSelection().toggle("a").toggle("b").toggle("a")

        assertThat(selection.count).isEqualTo(selection.submitIds().size)
        assertThat(selection.submitIds()).containsExactly("b")
    }

    @Test
    fun `clear removes everything`() {
        val selection = BookSelection().toggle("a").clear()

        assertThat(selection.count).isEqualTo(0)
        assertThat(selection.ids).isEmpty()
    }

    @Test
    fun `submit snapshot does not change when the selection changes later`() {
        val original = BookSelection().toggle("a")
        val submitted = original.submitIds()
        val changed = original.toggle("b")

        assertThat(submitted).containsExactly("a")
        assertThat(changed.submitIds()).containsExactly("a", "b")
    }
}
