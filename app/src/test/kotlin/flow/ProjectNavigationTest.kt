package flow

import com.github.unhappychoice.circleci.v1.response.Project
import com.gojuno.koptional.None
import com.gojuno.koptional.Some
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.domain.model.BuildStepId
import com.unhappychoice.norimaki.domain.model.ProjectId
import com.unhappychoice.norimaki.domain.service.EventBusService
import com.unhappychoice.norimaki.presentation.core.subscribeToProjectNavigation
import com.unhappychoice.norimaki.presentation.screen.BuildListScreen
import com.unhappychoice.norimaki.presentation.screen.BuildScreen
import com.unhappychoice.norimaki.presentation.screen.BuildStepScreen
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.reactivex.disposables.Disposable

class ProjectNavigationTest : DescribeSpec({
    describe("Flow.subscribeToProjectNavigation()") {
        val eventBus = EventBusService()
        val previousBuildId = BuildId("github", "team", "previous", 1)
        val previousHistory = History.single(BuildListScreen(ProjectId("github", "team", "previous"))).buildUpon()
            .push(BuildScreen(previousBuildId))
            .push(BuildStepScreen(previousBuildId, BuildStepId(0)))
            .build()
        lateinit var flow: Flow
        lateinit var subscription: Disposable
        var isTokenScreenVisible = false

        beforeEach {
            flow = Flow(KeyManager(emptyList()), previousHistory)
            flow.setDispatcher { _, callback -> callback.onTraversalCompleted() }
            isTokenScreenVisible = false
            subscription = flow.subscribeToProjectNavigation(eventBus) { isTokenScreenVisible }
        }

        afterEach { subscription.dispose() }

        it("resets step detail history to Recent Builds") {
            eventBus.selectProject.onNext(None)

            flow.assertBuildReturnsTo(BuildListScreen(null), previousBuildId)
        }

        it("resets step detail history to the selected project") {
            val project = mockk<Project> {
                every { vcsUrl } returns "https://bitbucket.org/team/repo"
                every { username } returns "team"
                every { reponame } returns "repo"
            }

            eventBus.selectProject.onNext(Some(project))

            flow.assertBuildReturnsTo(
                BuildListScreen(ProjectId("bitbucket", "team", "repo")),
                BuildId("bitbucket", "team", "repo", 2)
            )
        }

        it("stops handling selections when the subscription is disposed") {
            subscription.dispose()
            eventBus.selectProject.onNext(None)

            flow.history.toList() shouldBe previousHistory.toList()
        }

        it("ignores Recent Builds and project selections while the token screen is active") {
            isTokenScreenVisible = true

            eventBus.selectProject.onNext(None)
            eventBus.selectProject.onNext(Some(mockk<Project>(relaxed = true)))

            flow.history.toList() shouldBe previousHistory.toList()

            isTokenScreenVisible = false
            eventBus.selectProject.onNext(None)

            flow.history.toList() shouldBe listOf(BuildListScreen(null))
        }
    }
})

private fun Flow.assertBuildReturnsTo(list: BuildListScreen, buildId: BuildId) {
    history.toList() shouldBe listOf(list)
    val build = BuildScreen(buildId)
    set(build)
    history.top<BuildScreen>() shouldBe build
    goBack() shouldBe true
    history.toList() shouldBe listOf(list)
}
