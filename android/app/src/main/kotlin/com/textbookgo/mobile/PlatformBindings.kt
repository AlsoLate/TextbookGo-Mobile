package com.textbookgo.mobile

import android.content.Context
import com.textbookgo.mobile.core.repository.AuthRepository
import com.textbookgo.mobile.core.repository.CatalogRepository
import com.textbookgo.mobile.core.repository.PreferencesRepository
import com.textbookgo.mobile.core.repository.ShelfRepository
import com.textbookgo.mobile.data.download.ContentSource
import com.textbookgo.mobile.data.download.Transfer

/** 由 debug/release/verify 源集分别提供，保证示例数据不会进入正式包。 */
interface PlatformBindings {
    val auth: AuthRepository
    val catalog: CatalogRepository
    val content: ContentSource
    val transfer: Transfer
}

object Bindings {
    fun create(
        context: Context,
        shelf: ShelfRepository,
        preferences: PreferencesRepository,
    ): PlatformBindings = createPlatformBindings(context, shelf, preferences)
}
