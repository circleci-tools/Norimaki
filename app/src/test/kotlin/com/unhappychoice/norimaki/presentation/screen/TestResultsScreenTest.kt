package com.unhappychoice.norimaki.presentation.screen

import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.presentation.presenter.TestResultsPresenter
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import org.kodein.di.DI
import org.kodein.di.direct
import org.kodein.di.instance

class TestResultsScreenTest : DescribeSpec({
    val screen = TestResultsScreen(BuildId("github", "team", "repo", 123))

    it("uses a fixed title") {
        screen.getTitle() shouldBe "Test Results"
        screen.getLayoutResource() shouldBe R.layout.test_results_view
    }

    it("binds the restored build identifier and a fresh presenter per resolution") {
        val dependencies = screen.module(DI {}).direct
        dependencies.instance<BuildId>() shouldBe screen.buildId
        dependencies.instance<TestResultsPresenter>() shouldNotBeSameInstanceAs dependencies.instance<TestResultsPresenter>()
    }
})
