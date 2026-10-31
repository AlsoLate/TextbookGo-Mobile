package com.textbookgo.mobile

import android.content.Context
import com.textbookgo.mobile.core.repository.PreferencesRepository
import com.textbookgo.mobile.core.repository.ShelfRepository
import com.textbookgo.mobile.data.RealPlatformBindings

/** Release：真实 HTTP 数据源与应用内网页登录。 */
fun createPlatformBindings(
    context: Context,
    shelf: ShelfRepository,
    preferences: PreferencesRepository,
): PlatformBindings = RealPlatformBindings(context, shelf, preferences)
