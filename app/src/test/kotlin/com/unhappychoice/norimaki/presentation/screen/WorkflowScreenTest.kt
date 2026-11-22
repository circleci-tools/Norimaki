package com.unhappychoice.norimaki.presentation.screen

import com.unhappychoice.norimaki.R
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class WorkflowScreenTest : DescribeSpec({
    val subject = WorkflowScreen("github/circleci-tools/Norimaki", "workflow-id", "test-and-deploy")

    describe("WorkflowScreen") {
        it(".getTitle() should return the workflow name") {
            subject.getTitle() shouldBe "test-and-deploy"
        }
        it(".getLayoutResource() should use the workflow job view") {
            subject.getLayoutResource() shouldBe R.layout.workflow_view
        }
        it(".equals() should compare workflow ids") {
            val sameRun = WorkflowScreen("other", "workflow-id", "other")
            (subject == sameRun) shouldBe true
            subject.hashCode() shouldBe sameRun.hashCode()
            subject.equals("workflow-id") shouldBe false
            (subject == WorkflowScreen("github/circleci-tools/Norimaki", "another-id", "test-and-deploy")) shouldBe false
        }
    }
})
