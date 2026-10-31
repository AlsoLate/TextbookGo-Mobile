package com.textbookgo.mobile.data.reader

import android.content.Context
import androidx.core.content.edit

/**
 * 阅读进度与阅读偏好。
 *
 * 页码按教材 id 记录，文件被删除时同步清理；夜读开关是全局偏好，
 * 下次打开任何一本教材都沿用上次的选择。
 */
class ReaderProgressStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("textbookgo-reader", Context.MODE_PRIVATE)

    fun page(bookId: String): Int = prefs.getInt(KEY_PAGE + bookId, 0)

    fun totalPages(bookId: String): Int = prefs.getInt(KEY_TOTAL + bookId, 0)

    fun savePage(bookId: String, page: Int, totalPages: Int) {
        prefs.edit {
            putInt(KEY_PAGE + bookId, page.coerceAtLeast(0))
            putInt(KEY_TOTAL + bookId, totalPages.coerceAtLeast(0))
        }
    }

    /** 删除教材时一并清掉进度，重新下载后从第一页开始。 */
    fun clear(bookId: String) {
        prefs.edit {
            remove(KEY_PAGE + bookId)
            remove(KEY_TOTAL + bookId)
        }
    }

    /** 夜读：把纸面反相成深色底浅色字，适合关灯后看。 */
    fun nightMode(): Boolean = prefs.getBoolean(KEY_NIGHT, false)

    fun setNightMode(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_NIGHT, enabled) }
    }

    private companion object {
        const val KEY_PAGE = "page:"
        const val KEY_TOTAL = "total:"
        const val KEY_NIGHT = "night_mode"
    }
}
