package com.unhappychoice.norimaki.domain.model

import android.graphics.Color
import com.github.unhappychoice.circleci.v1.response.Build
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.util.Date

class BuildTest : DescribeSpec({
    fun build(number: Int = 1, queued: Date? = null, buildStatus: String? = null): Build = mockk {
        every { username } returns "circleci-tools"
        every { reponame } returns "Norimaki"
        every { branch } returns "master"
        every { buildNum } returns number
        every { vcsRevision } returns "abcdef123456"
        every { vcsUrl } returns "https://github.com/circleci-tools/Norimaki"
        every { user } returns mapOf("login" to "unhappychoice")
        every { queuedAt } returns queued
        every { status } returns buildStatus
    }

    describe("Build.repositoryString()") {
        it("should join user and repository names") {
            build().repositoryString() shouldBe "circleci-tools / Norimaki"
        }
    }

    describe("Build.revisionString()") {
        it("should contain branch, number and short revision") {
            build(number = 42).revisionString() shouldBe "master #42 (abcdef)"
        }
    }

    describe("Build.avatarUrl()") {
        it("should point to the GitHub avatar of the user") {
            build().avatarUrl() shouldBe "https://github.com/unhappychoice.png"
        }
    }

    describe("Build.uniqueId()") {
        it("should combine repository and build number") {
            build(number = 7).uniqueId() shouldBe "circleci-tools / Norimaki/7"
        }
    }

    describe("vcsTypeFromUrl()") {
        it("should detect bitbucket") {
            vcsTypeFromUrl("https://bitbucket.org/user/repo") shouldBe "bitbucket"
        }
        it("should fall back to github") {
            vcsTypeFromUrl("https://github.com/user/repo") shouldBe "github"
            vcsTypeFromUrl(null) shouldBe "github"
        }
        it("should be used by Build.vcsType()") {
            build().vcsType() shouldBe "github"
        }
    }

    describe("List<Build>.addDistinctByNumber()") {
        it("should prefer new builds and drop duplicates") {
            val old = build(number = 1, buildStatus = "running")
            val updated = build(number = 1, buildStatus = "success")
            val other = build(number = 2)

            val result = listOf(old, other).addDistinctByNumber(listOf(updated))

            result.map { it.buildNum } shouldBe listOf(1, 2)
            result.first().status shouldBe "success"
        }
    }

    describe("List<Build>.sortByQueuedAt()") {
        it("should sort builds from newest to oldest") {
            val builds = listOf(build(number = 1, queued = Date(1000)), build(number = 2, queued = Date(3000)), build(number = 3, queued = Date(2000)))
            builds.sortByQueuedAt().map { it.buildNum } shouldBe listOf(2, 3, 1)
        }
    }

    describe("Build.statusColor()") {
        beforeEach {
            mockkStatic(Color::class)
            every { Color.rgb(any<Int>(), any<Int>(), any<Int>()) } answers { (firstArg<Int>() shl 16) or (secondArg<Int>() shl 8) or thirdArg<Int>() }
        }
        afterEach { unmockkStatic(Color::class) }

        it("should be green for successful builds") {
            listOf("success", "fixed", "no_tests").forEach { build(buildStatus = it).statusColor() shouldBe 0x42C88A }
        }
        it("should be gray for canceled builds") {
            build(buildStatus = "canceled").statusColor() shouldBe 0x898989
        }
        it("should be red for failed builds") {
            listOf("infrastructure_fail", "timedout", "failed").forEach { build(buildStatus = it).statusColor() shouldBe 0xED5C5C }
        }
        it("should be blue for other builds") {
            build(buildStatus = "running").statusColor() shouldBe 0x5CD3E4
        }
    }
})
