package com.unhappychoice.norimaki.presentation.screen

import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.domain.model.ProjectId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.kodein.di.DI
import org.kodein.di.direct
import org.kodein.di.instance

class BuildListScreenTest : DescribeSpec({
    val projectId = ProjectId("github", "unhappychoice", "Norimaki")
    val subject = BuildListScreen(null)

    describe("BuildListScreen") {
        describe(".getTitle()") {
            it("should return 'Recent Builds' for no project") {
                BuildListScreen(null).getTitle() shouldBe "Recent Builds"
            }
            it("should return the project path when set") {
                BuildListScreen(projectId).getTitle() shouldBe "github/unhappychoice/Norimaki"
            }
        }
        describe(".equals()") {
            it("should be equal for the same project regardless of the selected branch") {
                (BuildListScreen(projectId) == BuildListScreen(projectId, "main")) shouldBe true
                BuildListScreen(projectId).hashCode() shouldBe BuildListScreen(projectId, "main").hashCode()
            }
            it("should not be equal for another project") {
                (BuildListScreen(projectId) == BuildListScreen(projectId.copy(repository = "other"))) shouldBe false
            }
        }
        describe(".getLayoutResource()") {
            it("should return view resource") {
                subject.getLayoutResource() shouldBe R.layout.build_list_view
            }
        }
        describe("selectedBranch") {
            it("defaults to all branches") {
                BuildListScreen(projectId).selectedBranch shouldBe ""
            }
            it("keeps the branch chosen after construction") {
                val screen = BuildListScreen(projectId)
                screen.selectedBranch = "release/Next"
                screen.selectedBranch shouldBe "release/Next"
            }
        }
        describe(".module()") {
            it("binds itself so the presenter can read and persist the selected branch") {
                val screen = BuildListScreen(projectId, "release/Next")
                val dependencies = screen.module(DI {}).direct
                dependencies.instance<BuildListScreen>() shouldBeSameInstanceAs screen
            }
        }
    }
})
