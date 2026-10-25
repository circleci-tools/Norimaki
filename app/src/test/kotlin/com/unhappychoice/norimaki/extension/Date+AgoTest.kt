package com.unhappychoice.norimaki.extension

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import java.util.Calendar
import java.util.Date

class DateAgoTest : DescribeSpec({
    fun secondsAgo(seconds: Long): Date {
        val offset = Calendar.getInstance().timeZone.rawOffset
        return Date(System.currentTimeMillis() - offset - seconds * 1000)
    }

    val minute = 60L
    val hour = 60 * minute
    val day = 24 * hour

    describe("Date.getTimeAgo()") {
        it("should be 'now' for future dates") {
            secondsAgo(-hour).getTimeAgo() shouldBe "now"
        }
        it("should be in seconds under a minute") {
            secondsAgo(20).getTimeAgo() shouldEndWith "s ago"
        }
        it("should be in minutes under an hour") {
            secondsAgo(10 * minute + 30).getTimeAgo() shouldBe "10m ago"
        }
        it("should be in hours under a day") {
            secondsAgo(3 * hour + 30).getTimeAgo() shouldBe "3h ago"
        }
        it("should be in days under a week") {
            secondsAgo(2 * day + 30).getTimeAgo() shouldBe "2D ago"
        }
        it("should be in weeks under four weeks") {
            secondsAgo(15 * day).getTimeAgo() shouldBe "2W ago"
        }
        it("should be in months under twelve four-week months") {
            secondsAgo(60 * day).getTimeAgo() shouldBe "2M ago"
        }
        it("should be in years otherwise") {
            secondsAgo(400 * day).getTimeAgo() shouldBe "1Y ago"
        }
    }
})
