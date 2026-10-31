package com.textbookgo.mobile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 启动级冒烟测试：应用壳、导航与示例模式下的完整下载闭环。
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun brandIsVisible() {
        // 顶栏与登录页都会出现品牌名，只要首屏可见即可。
        composeRule.onAllNodesWithText("TextbookGo")[0].assertIsDisplayed()
    }

    @Test
    fun settingsTabShowsPreferences() {
        composeRule.onNodeWithTag("tab_SETTINGS").performClick()
        composeRule.onNodeWithText("记住登录状态").assertIsDisplayed()
    }

    @Test
    fun demoFlowDownloadsBookToShelf() {
        composeRule.onNodeWithTag("login_button").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("book_demo-7-math").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("book_demo-7-math").performClick()
        composeRule.waitUntil(3_000) {
            composeRule.onAllNodesWithText("已选 1 本").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("download_button").performClick()

        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithTag("shelf_grid").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("下载").assertIsDisplayed()
    }
}
