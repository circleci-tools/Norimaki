package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.domain.model.WorkflowJob
import com.unhappychoice.norimaki.infrastructure.workflow.WorkflowClient
import com.unhappychoice.norimaki.infrastructure.workflow.WorkflowHttpException
import com.unhappychoice.norimaki.presentation.screen.BuildScreen
import com.unhappychoice.norimaki.presentation.screen.WorkflowScreen
import flow.Flow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.CompletableSubject
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import java.io.IOException

class WorkflowPresenterTest : DescribeSpec({
    val hold = WorkflowJob("job", "deploy", "approval", "on_hold", null, "request")
    val resolved = hold.copy(status = "success")

    beforeSpec {
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }
    }

    afterSpec {
        RxJavaPlugins.reset()
        RxAndroidPlugins.reset()
    }

    fun presenter(
        client: WorkflowClient,
        api: CircleCIAPIClientV1_1 = mockk(),
        activity: MainActivity = mockk()
    ) =
        WorkflowPresenter(WorkflowScreen("github/owner/repo", "run", "deploy")).apply {
            di = DI {
                bind<MainActivity>() with singleton { activity }
                bind<WorkflowClient>() with singleton { client }
                bind<CircleCIAPIClientV1_1>() with singleton { api }
            }
        }

    describe("workflow approvals") {
        it("serializes approval and refreshes the workflow after acceptance") {
            val pending = CompletableSubject.create()
            val client = mockk<WorkflowClient> {
                every { jobs("run") } returnsMany listOf(Single.just(listOf(hold)), Single.just(listOf(resolved)))
                every { approve("run", "request") } returns pending
            }
            val presenter = presenter(client)
            val updates = presenter.updates.test()

            presenter.refresh()
            presenter.canApprove(hold) shouldBe true
            presenter.approve(hold)
            presenter.approve(hold)
            updates.values().last().busy shouldBe true
            pending.onComplete()
            presenter.approve(resolved)

            verify(exactly = 1) { client.approve("run", "request") }
            verify(exactly = 2) { client.jobs("run") }
            updates.values().last() shouldBe WorkflowState(listOf(resolved), message = R.string.workflow_approval_submitted)
        }

        it("keeps a submitted approval disabled when the refresh fails") {
            val client = mockk<WorkflowClient> {
                every { jobs("run") } returnsMany listOf(Single.just(listOf(hold)), Single.error(IOException()))
                every { approve("run", "request") } returns Completable.complete()
            }
            val presenter = presenter(client)
            val updates = presenter.updates.test()

            presenter.refresh()
            presenter.approve(hold)
            presenter.approve(hold)

            presenter.canApprove(hold) shouldBe false
            updates.values().last().refreshFailed shouldBe true
            verify(exactly = 1) { client.approve("run", "request") }
        }

        listOf(
            Triple(403, hold, R.string.workflow_approval_permission),
            Triple(400, resolved, R.string.workflow_approval_resolved),
            Triple(500, hold, R.string.workflow_approval_error)
        ).forEach { (status, latestJob, message) ->
            it("refreshes and reports HTTP $status with job status ${latestJob.status}") {
                val client = mockk<WorkflowClient> {
                    every { jobs("run") } returnsMany listOf(Single.just(listOf(hold)), Single.just(listOf(latestJob)))
                    every { approve("run", "request") } returns Completable.error(WorkflowHttpException(status))
                }
                val presenter = presenter(client)
                val updates = presenter.updates.test()

                presenter.refresh()
                presenter.approve(hold)

                updates.values().last() shouldBe WorkflowState(listOf(latestJob), message = message)
            }
        }
    }

    it("uses the existing build API for numbered jobs and skips jobs without numbers") {
        val api = mockk<CircleCIAPIClientV1_1> {
            every { getBuild("github", "owner", "repo", 42) } returns Observable.error(IOException())
        }
        val presenter = presenter(mockk(), api)
        val updates = presenter.updates.test()

        presenter.openBuild(hold)
        presenter.openBuild(WorkflowJob("build", "test", "build", "success", 42, null))

        verify(exactly = 1) { api.getBuild("github", "owner", "repo", 42) }
        updates.values().last().message shouldBe R.string.workflow_build_load_error
    }

    it("opens build details with the fetched build for a numbered job") {
        val activity = mockk<MainActivity>()
        val build = mockk<Build>()
        val api = mockk<CircleCIAPIClientV1_1> {
            every { getBuild("github", "owner", "repo", 42) } returns Observable.just(build)
        }
        val presenter = presenter(mockk(), api, activity)
        val flow = mockk<Flow>(relaxed = true)
        val destination = slot<BuildScreen>()
        mockkStatic(Flow::class)
        try {
            every { Flow.get(activity) } returns flow

            presenter.openBuild(WorkflowJob("build", "test", "build", "success", 42, null))

            verify(exactly = 1) { flow.set(capture(destination)) }
            (destination.captured.build === build) shouldBe true
        } finally {
            presenter.bag.dispose()
            unmockkStatic(Flow::class)
        }
    }
})
