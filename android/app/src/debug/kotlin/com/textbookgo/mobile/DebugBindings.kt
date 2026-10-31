package com.textbookgo.mobile

import android.content.Context
import com.textbookgo.mobile.core.repository.PreferencesRepository
import com.textbookgo.mobile.core.repository.ShelfRepository

/** Debug：示例授权/目录/内容源，仅用于开发与演示；不会进入 release。 */
@Suppress("UNUSED_PARAMETER")
fun createPlatformBindings(
    context: Context,
    shelf: ShelfRepository,
    preferences: PreferencesRepository,
): PlatformBindings = DebugPlatformBindings(context, shelf)
