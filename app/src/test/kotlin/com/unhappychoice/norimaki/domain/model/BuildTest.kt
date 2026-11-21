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
    fun build(
        number: Int = 1,
        queued: Date? = null,
        buildStatus: String? = null,
        repositoryUrl: String = "https://github.com/circleci-tools/Norimaki",
        author: Map<String, String>? = mapOf("login" to "unhappychoice")
    ): Build = mockk {
        every { username } returns "circleci-tools"
        every { reponame } returns "Norimaki"
        every { branch } returns "master"
        every { buildNum } returns number
        every { vcsRevision } returns "abcdef123456"
        every { vcsUrl } returns repositoryUrl
        every { user } returns author
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
        val bitbucketUrl = "https://bitbucket.org/team/repo"
        val bitbucketAvatar = "https://bitbucket.org/account/alice/avatar/"

        it("should point to the GitHub avatar of the user") {
            build().avatarUrl() shouldBe "https://github.com/unhappychoice.png"
        }

        it("should use the avatar supplied for a Bitbucket build") {
            val author = mapOf("login" to "alice", "vcs_type" to "bitbucket", "avatar_url" to bitbucketAvatar)
            build(repositoryUrl = bitbucketUrl, author = author).avatarUrl() shouldBe bitbucketAvatar
        }

        it("should return no avatar for Bitbucket builds without a usable avatar URL") {
            build(repositoryUrl = bitbucketUrl).avatarUrl() shouldBe null
            listOf("", "invalid", "https://github.com/alice.png", "https://avatars.githubusercontent.com/u/1")
                .forEach { avatar ->
                    val author = mapOf("login" to "alice", "avatar_url" to avatar)
                    build(repositoryUrl = bitbucketUrl, author = author).avatarUrl() shouldBe null
                }
        }

        it("should return no avatar for a Bitbucket build with a GitHub author") {
            val author = mapOf("login" to "alice", "vcs_type" to "github", "avatar_url" to bitbucketAvatar)
            build(repositoryUrl = bitbucketUrl, author = author).avatarUrl() shouldBe null
        }

        it("should return no avatar when the author or login is missing or blank") {
            listOf(null, emptyMap(), mapOf("login" to "  ")).forEach { author ->
                build(author = author).avatarUrl() shouldBe null
            }
            val author = mapOf("avatar_url" to bitbucketAvatar)
            build(repositoryUrl = bitbucketUrl, author = author).avatarUrl() shouldBe null
        }
    }

    describe("Build.uniqueId()") {
        it("should combine VCS provider, repository and build number") {
            build(number = 7).uniqueId() shouldBe "github/circleci-tools/Norimaki/7"
            build(number = 7, repositoryUrl = "https://bitbucket.org/circleci-tools/Norimaki").uniqueId() shouldBe "bitbucket/circleci-tools/Norimaki/7"
        }
    }

    describe("vcsTypeFromUrl()") {
        it("should detect bitbucket") {
            vcsTypeFromUrl("https://bitbucket.org/acme/repo") shouldBe "bitbucket"
            vcsTypeFromUrl("https://BITBUCKET.ORG:443/acme/repo") shouldBe "bitbucket"
        }
        it("should only match the Bitbucket hostname") {
            vcsTypeFromUrl("https://github.com/acme/bitbucket.org") shouldBe "github"
            vcsTypeFromUrl("https://bitbucket.org.example.com/acme/repo") shouldBe "github"
        }
        it("should fall back to github") {
            vcsTypeFromUrl("https://github.com/user/repo") shouldBe "github"
            vcsTypeFromUrl(null) shouldBe "github"
            vcsTypeFromUrl("bitbucket.org/acme/repo") shouldBe "github"
        }
        it("should resolve the provider used by build detail requests") {
            build(repositoryUrl = "https://github.com/acme/bitbucket.org").vcsType() shouldBe "github"
            build(repositoryUrl = "https://bitbucket.org/acme/repo").vcsType() shouldBe "bitbucket"
        }
    }

    describe("List<Build>.addDistinctByNumber()") {
        it("should retain builds with matching repository and number across providers when merging pages") {
            val github = build(number = 7)
            val bitbucket = build(number = 7, repositoryUrl = "https://bitbucket.org/circleci-tools/Norimaki")

            listOf(github).addDistinctByNumber(listOf(bitbucket)) shouldBe listOf(bitbucket, github)
        }

        it("should replace only the matching provider's build when polling") {
            val github = build(number = 7, buildStatus = "running")
            val bitbucket = build(number = 7, buildStatus = "running", repositoryUrl = "https://bitbucket.org/circleci-tools/Norimaki")
            val updated = build(number = 7, buildStatus = "success", repositoryUrl = "https://bitbucket.org/circleci-tools/Norimaki")

            listOf(github, bitbucket).addDistinctByNumber(listOf(updated)) shouldBe listOf(updated, github)
        }

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
