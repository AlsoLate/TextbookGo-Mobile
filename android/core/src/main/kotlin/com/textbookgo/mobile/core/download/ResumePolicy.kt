package com.textbookgo.mobile.core.download

/**
 * 断点续传决策。只依赖服务器实际返回的 Range 语义，不把“有临时文件”当作可续传。
 */
sealed interface ResumeRequestPlan {
    /** 从头下载。 */
    data object FullDownload : ResumeRequestPlan

    /**
     * 携带 Range 请求续传。
     *
     * @param offset 已落盘字节数。
     * @param ifRange 用于 If-Range 的强验证器（ETag 优先，其次 Last-Modified）。
     * @param totalBytes 上次已知总长度；未知时为 null。
     */
    data class Resume(
        val offset: Long,
        val ifRange: String,
        val totalBytes: Long? = null,
    ) : ResumeRequestPlan
}

sealed interface ResumeOutcome {
    /** 服务器返回合法 206，可把响应追加到已有临时文件。 */
    data object Append : ResumeOutcome

    /** 必须清空临时文件，从 0 重新下载。 */
    data object Restart : ResumeOutcome

    /** 416 且能证明本地文件已完整。 */
    data object Complete : ResumeOutcome

    /** 服务器响应不可信或与请求不匹配，不能追加。 */
    data class Reject(val reason: String) : ResumeOutcome
}

/** 影响续传判断的服务器响应字段。 */
data class RangeResponse(
    val statusCode: Int,
    val contentRangeStart: Long? = null,
    val contentRangeTotal: Long? = null,
    val etag: String? = null,
    val lastModified: String? = null,
)

object ResumePolicy {

    /**
     * 决定是否发 Range。没有强验证器或偏移为 0 时，应从头上传。
     */
    fun plan(
        existingBytes: Long,
        totalBytes: Long?,
        etag: String?,
        lastModified: String?,
    ): ResumeRequestPlan {
        if (existingBytes <= 0L) return ResumeRequestPlan.FullDownload
        if (totalBytes != null && existingBytes >= totalBytes) return ResumeRequestPlan.FullDownload

        val validator = etag?.takeIf { it.isNotBlank() }
            ?: lastModified?.takeIf { it.isNotBlank() }
            ?: return ResumeRequestPlan.FullDownload

        return ResumeRequestPlan.Resume(
            offset = existingBytes,
            ifRange = validator,
            totalBytes = totalBytes,
        )
    }

    /**
     * 校验响应，决定追加、重下、完成或拒绝。
     */
    fun evaluate(plan: ResumeRequestPlan, response: RangeResponse): ResumeOutcome =
        when (plan) {
            ResumeRequestPlan.FullDownload -> when (response.statusCode) {
                200 -> ResumeOutcome.Restart
                else -> ResumeOutcome.Reject("unexpected status ${response.statusCode}")
            }

            is ResumeRequestPlan.Resume -> when (response.statusCode) {
                206 -> evaluatePartial(plan, response)
                200 -> ResumeOutcome.Restart
                416 -> evaluateRangeNotSatisfiable(plan, response)
                else -> ResumeOutcome.Reject("unexpected status ${response.statusCode}")
            }
        }

    private fun evaluatePartial(
        plan: ResumeRequestPlan.Resume,
        response: RangeResponse,
    ): ResumeOutcome {
        val start = response.contentRangeStart
            ?: return ResumeOutcome.Reject("206 without content-range start")
        val total = response.contentRangeTotal
            ?: return ResumeOutcome.Reject("206 without content-range total")

        if (start != plan.offset) {
            return ResumeOutcome.Reject("content-range start $start does not match ${plan.offset}")
        }
        if (plan.totalBytes != null && total != plan.totalBytes) {
            return ResumeOutcome.Reject("content-range total $total does not match ${plan.totalBytes}")
        }

        val responseEtag = response.etag
        if (responseEtag != null &&
            plan.ifRange.startsWith("\"") &&
            responseEtag != plan.ifRange
        ) {
            return ResumeOutcome.Restart
        }

        return ResumeOutcome.Append
    }

    private fun evaluateRangeNotSatisfiable(
        plan: ResumeRequestPlan.Resume,
        response: RangeResponse,
    ): ResumeOutcome {
        val total = response.contentRangeTotal
        if (total != null && plan.totalBytes != null && total == plan.totalBytes &&
            plan.offset >= plan.totalBytes
        ) {
            return ResumeOutcome.Complete
        }
        return ResumeOutcome.Reject("416 cannot prove the local file is complete")
    }
}
