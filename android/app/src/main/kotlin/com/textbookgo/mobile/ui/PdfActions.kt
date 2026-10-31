package com.textbookgo.mobile.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/** 打开/分享教材 PDF，统一使用 FileProvider 的 content URI。 */
object PdfActions {

    fun open(context: Context, file: File) {
        val uri = uriFor(context, file) ?: return
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/pdf")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching {
            context.startActivity(Intent.createChooser(intent, "打开教材"))
        }.onFailure {
            Toast.makeText(context, "未找到可打开 PDF 的应用，可先导出或分享", Toast.LENGTH_LONG).show()
        }
    }

    fun share(context: Context, file: File) {
        val uri = uriFor(context, file) ?: return
        val intent = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching {
            context.startActivity(Intent.createChooser(intent, "分享教材"))
        }.onFailure {
            Toast.makeText(context, "分享失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uriFor(context: Context, file: File): Uri? {
        if (!file.exists()) {
            Toast.makeText(context, "教材文件不存在", Toast.LENGTH_SHORT).show()
            return null
        }
        return runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        }.onFailure {
            Toast.makeText(context, "无法访问教材文件", Toast.LENGTH_SHORT).show()
        }.getOrNull()
    }
}
