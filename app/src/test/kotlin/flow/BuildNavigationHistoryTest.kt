package flow

import android.os.Parcel
import android.os.Parcelable
import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Build
import com.google.gson.Gson
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.domain.model.BuildStepId
import com.unhappychoice.norimaki.presentation.core.GsonParceler
import com.unhappychoice.norimaki.presentation.screen.BuildListScreen
import com.unhappychoice.norimaki.presentation.screen.BuildScreen
import com.unhappychoice.norimaki.presentation.screen.BuildStepScreen
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.reactivex.Observable

class BuildNavigationHistoryTest : DescribeSpec({
    describe("build navigation history") {
        it("writes identical route data for small and oversized build responses") {
            val small = buildFixture(2, 1)
            val large = buildFixture(64, 16_384)
            (Gson().toJson(large).length > 1_048_576) shouldBe true

            val savedRoutes = routes(large).map(::parcelJson)
            savedRoutes shouldBe routes(small).map(::parcelJson)
            (savedRoutes.sumOf { it.length } < 512) shouldBe true
        }

        it("restores the selected step by number and goes back to the same build") {
            val build = buildFixture(2, 1)
            val keys = routes(build)
            val history = History.emptyBuilder().pushAll(keys.map { restore(it, parcelJson(it)) }).build()
            val flow = Flow(KeyManager(emptyList()), history)
            flow.setDispatcher { _, callback -> callback.onTraversalCompleted() }

            val stepScreen = flow.history.top<BuildStepScreen>()
            stepScreen.buildId shouldBe BuildId("bitbucket", "team", "repo", 123)
            val api = mockk<CircleCIAPIClientV1_1> {
                every { getBuild("bitbucket", "team", "repo", 123) } returns Observable.just(build)
            }
            stepScreen.buildId.load(api).map { stepIdBuild ->
                stepScreen.stepId.resolve(stepIdBuild.steps!!.reversed())!!.actions.single().step
            }.blockingFirst() shouldBe 1
            flow.goBack() shouldBe true
            flow.history.top<BuildScreen>() shouldBe BuildScreen(stepScreen.buildId)
        }

        it("restores older history that contains embedded responses") {
            val build = buildFixture(2, 1)
            val gson = Gson()
            val keys = routes(build)
            val savedBuild = gson.toJson(build)
            val savedStep = gson.toJson(build.steps!![1])
            val savedBuildWithoutSteps = gson.toJson(build.copy(steps = null))
            restore(keys[0], """{"${BuildScreen::class.java.name}":{"build":$savedBuild}}""") shouldBe keys[0]
            restore(
                keys[1],
                """{"${BuildStepScreen::class.java.name}":{"build":$savedBuildWithoutSteps,"buildStep":$savedStep}}"""
            ) shouldBe keys[1]
        }

        it("returns to the build list when a legacy route has an incomplete build identifier") {
            val build = buildFixture(2, 1)
            listOf(build.copy(username = null), build.copy(reponame = null), build.copy(buildNum = null))
                .forEach { incomplete ->
                    val savedBuild = Gson().toJson(incomplete)
                    val key = routes(build).first()
                    restore(key, """{"${BuildScreen::class.java.name}":{"build":$savedBuild}}""") shouldBe
                        BuildListScreen("")
                }
        }

        it("uses the saved position when the API does not supply a step number") {
            val steps = buildFixture(2, 1).steps!!.map { step ->
                step.copy(actions = step.actions.map { it.copy(step = null) })
            }
            val screen = BuildStepScreen(BuildId("bitbucket", "team", "repo", 123), BuildStepId.from(steps[1], 1))
            val restored = restore(screen, parcelJson(screen)) as BuildStepScreen
            restored.stepId.resolve(steps) shouldBe steps[1]
        }
    }
})

private fun buildFixture(stepCount: Int, commandLength: Int): Build {
    val command = "x".repeat(commandLength)
    val steps = (0 until stepCount).joinToString(",") {
        """{"name":"Run command","actions":[{"step":$it,"bashCommand":"$command"}]}"""
    }
    return Gson().fromJson(
        """{"vcsUrl":"https://bitbucket.org/team/repo","username":"team","reponame":"repo","buildNum":123,"steps":[$steps]}""",
        Build::class.java
    )
}

private fun routes(build: Build): List<Any> {
    val buildId = BuildId.from(build)!!
    val steps = build.steps!!
    return listOf(BuildScreen(buildId), BuildStepScreen(buildId, BuildStepId.from(steps[1], 1)))
}

private fun parcelJson(key: Any): String {
    val json = slot<String>()
    val parcel = mockk<Parcel> { every { writeString(capture(json)) } returns Unit }
    GsonParceler().toParcelable(key).writeToParcel(parcel, 0)
    return json.captured
}

private fun restore(key: Any, json: String): Any {
    val parceler = GsonParceler()
    val creator = parceler.toParcelable(key).javaClass.getField("CREATOR").apply { isAccessible = true }
        .get(null) as Parcelable.Creator<*>
    val parcel = mockk<Parcel> { every { readString() } returns json }
    return parceler.toKey(creator.createFromParcel(parcel) as Parcelable)
}
