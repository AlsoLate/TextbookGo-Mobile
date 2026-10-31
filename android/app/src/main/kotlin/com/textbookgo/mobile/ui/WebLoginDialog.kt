package com.textbookgo.mobile.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.textbookgo.mobile.core.session.SessionTokenParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 应用内网页登录。
 *
 * 复刻旧版桌面端的授权方式：在平台登录页完成账号登录后，从页面 localStorage 的
 * `ND_UC_AUTH*` 中读取 `access_token`，随后所有请求使用 `X-ND-AUTH` 头。
 * 账号密码只在平台页面内输入，应用不接触、不保存。
 *
 * 若网页登录被平台限制（WebView 被识别、验证码无法完成等），可改用“手动输入
 * Token”：从桌面端工具的 `token.json` 复制 token 粘贴进来。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebLoginDialog(
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onToken: (String) -> Unit,
) {
    val context = LocalContext.current
    var manual by rememberSaveable { mutableStateOf(false) }
    var tokenInput by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("正在打开平台登录页…") }
    var detected by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    // 正式包关闭“手动输入 Token”；debug/verify 仍保留用于排障。
    val allowTestEntry = com.textbookgo.mobile.BuildConfig.ALLOW_TEST_ENTRY

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    val url = request.url
                    val host = url.host.orEmpty()
                    val allowed = url.scheme == "https" &&
                        (host == "smartedu.cn" || host.endsWith(".smartedu.cn") ||
                            host == "cbern.com.cn" || host.endsWith(".cbern.com.cn"))
                    if (!allowed) {
                        status = "已拦截非平台页面：$host"
                    }
                    return !allowed
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    if (!detected) status = "请在页面中完成登录，成功后会自动返回"
                    // 平台登录页按大屏设计，WebView 窄视口下登录框可能被顶出屏幕，这里校正一次。
                    view.evaluateJavascript(FIT_LOGIN_JS, null)
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: android.webkit.WebResourceError,
                ) {
                    if (request.isForMainFrame) {
                        status = "登录页加载失败：${error.description}"
                    }
                }
            }
            loadUrl(LOGIN_URL)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.destroy()
            }
        }
    }

    LaunchedEffect(manual) {
        if (manual || detected) return@LaunchedEffect
        val startedAt = System.currentTimeMillis()
        while (isActive && !detected) {
            delay(POLL_INTERVAL_MS)
            val dump = webView.evaluate(COLLECT_STORAGE_JS)
            val token = SessionTokenParser.extractFromStorageDump(dump)
            if (token != null) {
                detected = true
                status = "已获取登录凭据，正在保存…"
                onToken(token)
                break
            }
            if (System.currentTimeMillis() - startedAt > TIMEOUT_MS) {
                status = if (allowTestEntry) {
                    "等待登录超时，可重试或改用手动输入 Token"
                } else {
                    "等待登录超时，请重试"
                }
                break
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
        ),
    ) {
        androidx.activity.compose.BackHandler {
            if (!manual && webView.canGoBack()) webView.goBack() else onDismiss()
        }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "登录国家智慧教育平台",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (allowTestEntry) {
                        TextButton(
                            onClick = {
                                manual = !manual
                                status = if (manual) {
                                    "粘贴桌面端 token.json 中的 token"
                                } else {
                                    "正在打开平台登录页…"
                                }
                            },
                            modifier = Modifier.testTag("toggle_manual_token"),
                        ) {
                            Text(if (manual) "网页登录" else "手动输入")
                        }
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("close_login")) {
                        Text("关闭")
                    }
                }

                if (manual && allowTestEntry) {
                    ManualTokenPanel(
                        tokenInput = tokenInput,
                        onTokenChange = { tokenInput = it },
                        onPaste = {
                            clipboard.getText()?.text?.let { tokenInput = it.trim() }
                        },
                        onConfirm = { onToken(tokenInput.trim()) },
                        status = status,
                        busy = busy,
                        error = error,
                    )
                } else {
                    Box(Modifier.weight(1f)) {
                        AndroidView(
                            factory = { webView },
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("login_webview"),
                        )
                        if (detected) {
                            CircularProgressIndicator(Modifier.align(Alignment.Center))
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = status,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!error.isNullOrBlank()) {
                                Text(
                                    text = error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { webView.loadUrl(LOGIN_URL) }) { Text("重新加载") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualTokenPanel(
    tokenInput: String,
    onTokenChange: (String) -> Unit,
    onPaste: () -> Unit,
    onConfirm: () -> Unit,
    status: String,
    busy: Boolean,
    error: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "在电脑上运行原版 TextbookGo 完成一次登录，打开用户目录下的 " +
                "token.json，复制其中的 token 粘贴到下方。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Token 等同于登录凭据，请勿分享给他人。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        OutlinedTextField(
            value = tokenInput,
            onValueChange = onTokenChange,
            label = { Text("access_token") },
            minLines = 3,
            maxLines = 6,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("manual_token_field"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onPaste) { Text("从剪贴板粘贴") }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onConfirm,
                enabled = !busy && SessionTokenParser.isPlausible(tokenInput.trim()),
                modifier = Modifier.testTag("manual_token_confirm"),
            ) {
                Text(if (busy) "保存中…" else "使用此 Token")
            }
        }
        if (tokenInput.isNotBlank() && !SessionTokenParser.isPlausible(tokenInput.trim())) {
            Text(
                text = "凭据长度或字符不符合平台 token 格式，请确认复制完整。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (!error.isNullOrBlank()) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
    }
}

private suspend fun WebView.evaluate(script: String): String? =
    suspendCancellableCoroutine { continuation ->
        evaluateJavascript(script) { value ->
            if (continuation.isActive) continuation.resume(value)
        }
    }

private const val LOGIN_URL = "https://auth.smartedu.cn/uias/login"
private const val POLL_INTERVAL_MS = 1_000L
private const val TIMEOUT_MS = 5 * 60 * 1_000L

/**
 * 平台登录页按桌面/大屏设计，在 WebView 窄视口里登录框可能整体位于屏幕上方。
 * 页面加载完成后把登录框推回可视区域；已经正常显示时不做任何改动。
 */
private const val FIT_LOGIN_JS = """
(function () {
  try {
    var box = document.querySelector('.flx_loginbox');
    if (!box) { return 'nobox'; }
    var rect = box.getBoundingClientRect();
    if (!rect.height) { return 'hidden'; }
    var viewport = window.innerHeight;
    if (rect.top < 16) {
      var current = parseFloat(window.getComputedStyle(box).marginTop) || 0;
      box.style.marginTop = (current + (16 - rect.top)) + 'px';
      return 'shifted:' + Math.round(16 - rect.top);
    }
    if (rect.bottom > viewport - 16 && document.documentElement.scrollHeight > viewport) {
      window.scrollTo(0, document.documentElement.scrollHeight);
      return 'scrolled';
    }
    return 'ok';
  } catch (e) { return 'err:' + e; }
})()
"""

/** 只读取 ND_UC_AUTH* 键，避免把整个 localStorage 带出页面。 */private const val COLLECT_STORAGE_JS = """
(function () {
  try {
    var out = {};
    for (var i = 0; i < localStorage.length; i++) {
      var key = localStorage.key(i);
      if (key && key.indexOf('ND_UC_AUTH') === 0) {
        out[key] = localStorage.getItem(key);
      }
    }
    return JSON.stringify(out);
  } catch (e) {
    return '{}';
  }
})()
"""
