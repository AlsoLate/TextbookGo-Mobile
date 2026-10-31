package com.textbookgo.mobile.core.download

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ResumePolicyTest {

    @Test
    fun `offset zero always starts from scratch`() {
        assertThat(ResumePolicy.plan(0, 100, "\"etag\"", null))
            .isEqualTo(ResumeRequestPlan.FullDownload)
    }

    @Test
    fun `without a validator a partial file is not resumed`() {
        assertThat(ResumePolicy.plan(10, 100, null, null))
            .isEqualTo(ResumeRequestPlan.FullDownload)
    }

    @Test
    fun `strong etag is preferred as if range validator`() {
        assertThat(ResumePolicy.plan(10, 100, "\"abc\"", "Wed, 21 Oct 2015 07:28:00 GMT"))
            .isEqualTo(ResumeRequestPlan.Resume(offset = 10, ifRange = "\"abc\"", totalBytes = 100))
    }

    @Test
    fun `last modified is used when etag is absent`() {
        assertThat(ResumePolicy.plan(10, 100, null, "Wed, 21 Oct 2015 07:28:00 GMT"))
            .isEqualTo(
                ResumeRequestPlan.Resume(
                    offset = 10,
                    ifRange = "Wed, 21 Oct 2015 07:28:00 GMT",
                    totalBytes = 100,
                ),
            )
    }

    @Test
    fun `already complete local file is not requested again`() {
        assertThat(ResumePolicy.plan(100, 100, "\"abc\"", null))
            .isEqualTo(ResumeRequestPlan.FullDownload)
    }

    @Test
    fun `valid 206 matching offset and validator appends`() {
        val outcome = ResumePolicy.evaluate(
            plan = ResumeRequestPlan.Resume(10, "\"abc\""),
            response = RangeResponse(
                statusCode = 206,
                contentRangeStart = 10,
                contentRangeTotal = 100,
                etag = "\"abc\"",
            ),
        )

        assertThat(outcome).isEqualTo(ResumeOutcome.Append)
    }

    @Test
    fun `200 means the server ignored the range and we restart`() {
        val outcome = ResumePolicy.evaluate(
            plan = ResumeRequestPlan.Resume(10, "\"abc\""),
            response = RangeResponse(statusCode = 200, contentRangeTotal = 100),
        )

        assertThat(outcome).isEqualTo(ResumeOutcome.Restart)
    }

    @Test
    fun `206 with a different start is rejected`() {
        val outcome = ResumePolicy.evaluate(
            plan = ResumeRequestPlan.Resume(10, "\"abc\""),
            response = RangeResponse(
                statusCode = 206,
                contentRangeStart = 0,
                contentRangeTotal = 100,
                etag = "\"abc\"",
            ),
        )

        assertThat(outcome).isInstanceOf(ResumeOutcome.Reject::class.java)
    }

    @Test
    fun `206 without content range is rejected`() {
        val outcome = ResumePolicy.evaluate(
            plan = ResumeRequestPlan.Resume(10, "\"abc\""),
            response = RangeResponse(statusCode = 206, etag = "\"abc\""),
        )

        assertThat(outcome).isInstanceOf(ResumeOutcome.Reject::class.java)
    }

    @Test
    fun `changed etag restarts instead of appending`() {
        val outcome = ResumePolicy.evaluate(
            plan = ResumeRequestPlan.Resume(10, "\"old\""),
            response = RangeResponse(
                statusCode = 206,
                contentRangeStart = 10,
                contentRangeTotal = 100,
                etag = "\"new\"",
            ),
        )

        assertThat(outcome).isEqualTo(ResumeOutcome.Restart)
    }

    @Test
    fun `206 with a different total is rejected`() {
        val outcome = ResumePolicy.evaluate(
            plan = ResumeRequestPlan.Resume(10, "\"abc\"", totalBytes = 100),
            response = RangeResponse(
                statusCode = 206,
                contentRangeStart = 10,
                contentRangeTotal = 90,
                etag = "\"abc\"",
            ),
        )

        assertThat(outcome).isInstanceOf(ResumeOutcome.Reject::class.java)
    }

    @Test
    fun `416 with matching total proves the file is complete`() {
        val outcome = ResumePolicy.evaluate(
            plan = ResumeRequestPlan.Resume(100, "\"abc\"", totalBytes = 100),
            response = RangeResponse(statusCode = 416, contentRangeTotal = 100),
        )

        assertThat(outcome).isEqualTo(ResumeOutcome.Complete)
    }

    @Test
    fun `416 with mismatching total is rejected`() {
        val outcome = ResumePolicy.evaluate(
            plan = ResumeRequestPlan.Resume(100, "\"abc\"", totalBytes = 100),
            response = RangeResponse(statusCode = 416, contentRangeTotal = 50),
        )

        assertThat(outcome).isInstanceOf(ResumeOutcome.Reject::class.java)
    }

    @Test
    fun `full download response restarts the temp file`() {
        assertThat(
            ResumePolicy.evaluate(
                plan = ResumeRequestPlan.FullDownload,
                response = RangeResponse(statusCode = 200, contentRangeTotal = 100),
            ),
        ).isEqualTo(ResumeOutcome.Restart)
    }

    @Test
    fun `full download must not accept a partial response`() {
        assertThat(
            ResumePolicy.evaluate(
                plan = ResumeRequestPlan.FullDownload,
                response = RangeResponse(statusCode = 206, contentRangeStart = 10, contentRangeTotal = 100),
            ),
        ).isInstanceOf(ResumeOutcome.Reject::class.java)
    }

    @Test
    fun `unexpected status is rejected`() {
        listOf(401, 403, 404, 429, 500).forEach { status ->
            assertThat(
                ResumePolicy.evaluate(
                    plan = ResumeRequestPlan.Resume(10, "\"abc\""),
                    response = RangeResponse(statusCode = status),
                ),
            ).isInstanceOf(ResumeOutcome.Reject::class.java)
        }
    }
}
