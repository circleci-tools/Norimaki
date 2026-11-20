package com.unhappychoice.norimaki.presentation.screen

import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.domain.model.BuildId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.kodein.di.DI
import org.kodein.di.direct
import org.kodein.di.instance

class BuildScreenTest : DescribeSpec({
    val screen = BuildScreen(BuildId("bitbucket", "team", "repo", 123))

    it("uses the build identifier for the title while loading") {
        screen.getTitle() shouldBe "repo #123"
        screen.getLayoutResource() shouldBe R.layout.build_view
    }

    it("binds the restored identifiers for the presenter") {
        val dependencies = screen.module(DI {}).direct
        dependencies.instance<BuildId>() shouldBe screen.buildId
    }
})
