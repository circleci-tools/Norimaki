package com.unhappychoice.norimaki.domain.model

import com.github.unhappychoice.circleci.v1.response.Build
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.util.Date

class BuildNotificationTest : DescribeSpec({
    fun build(buildStatus: String? = "success", stop: Date? = null, message: String? = "Fix bug"): Build = mockk {
        every { username } returns "circleci-tools"
        every { reponame } returns "Norimaki"
        every { branch } returns "master"
        every { buildNum } returns 42
        every { vcsRevision } returns "abcdef123456"
        every { status } returns buildStatus
        every { stopTime } returns stop
        every { subject } returns message
    }

    describe("Build.notificationTitle()") {
        it("should contain the status and repository") {
            build("success").notificationTitle() shouldBe "Success: circleci-tools / Norimaki"
        }
        it("should humanize the status") {
            build("infrastructure_fail").notificationTitle() shouldBe "Infrastructure fail: circleci-tools / Norimaki"
        }
    }

    describe("Build.notificationText()") {
        it("should contain the revision and commit subject") {
            build().notificationText() shouldBe "master #42 (abcdef)\nFix bug"
        }
        it("should skip a missing subject") {
            build(message = null).notificationText() shouldBe "master #42 (abcdef)"
        }
    }

    describe("List<Build>.finishedAfter()") {
        it("should keep builds stopped after the time") {
            val builds = listOf(build(stop = Date(1000)), build(stop = Date(3000)), build(stop = null))
            builds.finishedAfter(Date(2000)) shouldBe listOf(builds[1])
        }
    }

    describe("List<Build>.latestStopTime()") {
        it("should return the latest stop time") {
            listOf(build(stop = Date(1000)), build(stop = Date(3000)), build()).latestStopTime() shouldBe Date(3000)
        }
        it("should be null when no build has stopped") {
            listOf(build()).latestStopTime() shouldBe null
        }
    }
})
