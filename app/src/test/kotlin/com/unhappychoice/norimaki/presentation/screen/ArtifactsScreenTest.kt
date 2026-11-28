package com.unhappychoice.norimaki.presentation.screen

import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.domain.model.BuildId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.kodein.di.DI
import org.kodein.di.direct
import org.kodein.di.instance

class ArtifactsScreenTest : DescribeSpec({
    val screen = ArtifactsScreen(BuildId("github", "team", "repo", 123))

    it("uses the repository and build number for the title") {
        screen.getTitle() shouldBe "Artifacts · repo #123"
        screen.getLayoutResource() shouldBe R.layout.artifacts_view
    }

    it("binds the restored build identifier for the presenter") {
        val dependencies = screen.module(DI {}).direct
        dependencies.instance<BuildId>() shouldBe screen.buildId
    }
})
