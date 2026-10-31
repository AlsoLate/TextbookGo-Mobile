package com.textbookgo.mobile

import android.content.Context
import com.textbookgo.mobile.core.repository.PreferencesRepository
import com.textbookgo.mobile.core.repository.ShelfRepository
import com.textbookgo.mobile.data.RealPlatformBindings

/**
 * Verify：真实平台数据源 + 调试签名，供真机全流程验收直接安装。
 * 与 release 使用同一套装配，只是不进入正式发布渠道。
 */
fun createPlatformBindings(
    context: Context,
    shelf: ShelfRepository,
    preferences: PreferencesRepository,
): PlatformBindings = RealPlatformBindings(context, shelf, preferences)
