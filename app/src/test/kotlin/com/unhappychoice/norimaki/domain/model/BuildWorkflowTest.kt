package com.unhappychoice.norimaki.domain.model

import com.github.unhappychoice.circleci.v1.response.Build
import com.github.unhappychoice.circleci.v1.response.Workflow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class BuildWorkflowTest : DescribeSpec({
    fun build(workflow: Workflow?): Build = mockk {
        every { username } returns "circleci-tools"
        every { reponame } returns "Norimaki"
        every { vcsUrl } returns "https://github.com/circleci-tools/Norimaki"
        every { workflows } returns workflow
    }

    fun workflow(id: String?, name: String?, job: String?): Workflow = mockk {
        every { workflowId } returns id
        every { workflowName } returns name
        every { jobName } returns job
    }

    describe("Build.workflowString()") {
        it("should join workflow and job names") {
            build(workflow("1", "test-and-deploy", "build")).workflowString() shouldBe "test-and-deploy / build"
        }
        it("should be null without workflow") {
            build(null).workflowString() shouldBe null
            build(workflow("1", null, null)).workflowString() shouldBe null
        }
    }

    describe("Build.repositoryWithWorkflowString()") {
        it("should append the workflow to the repository") {
            build(workflow("1", "wf", "job")).repositoryWithWorkflowString() shouldBe "circleci-tools / Norimaki  ·  wf / job"
        }
        it("should be the repository only without workflow") {
            build(null).repositoryWithWorkflowString() shouldBe "circleci-tools / Norimaki"
        }
    }

    describe("Build.projectName()") {
        it("should contain vcs type, user and repository") {
            build(null).projectName() shouldBe "github/circleci-tools/Norimaki"
        }
    }

    describe("List<Build>.inWorkflow()") {
        val builds = listOf(build(workflow("a", "wf", "build")), build(workflow("b", "wf", "test")), build(null))

        it("should keep builds of the workflow") {
            builds.inWorkflow("a") shouldBe listOf(builds[0])
        }
        it("should keep every build without workflow id") {
            builds.inWorkflow(null) shouldBe builds
            builds.inWorkflow("") shouldBe builds
        }
    }
})
