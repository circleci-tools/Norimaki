package com.unhappychoice.norimaki.presentation.screen

import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.domain.model.BuildStepId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class BuildStepScreenTest : DescribeSpec({
    val screen = BuildStepScreen(BuildId("github", "team", "repo", 123), BuildStepId(7))

    it("uses the step identifier for the title while loading") {
        screen.getTitle() shouldBe "Step 7"
        screen.getLayoutResource() shouldBe R.layout.build_step_view
    }
})
