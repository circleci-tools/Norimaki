package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.domain.model.WorkflowJob
import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.infrastructure.workflow.WorkflowClient
import com.unhappychoice.norimaki.infrastructure.workflow.WorkflowHttpException
import com.unhappychoice.norimaki.presentation.screen.BuildScreen
import com.unhappychoice.norimaki.presentation.screen.WorkflowScreen
import com.unhappychoice.norimaki.presentation.view.WorkflowView
import flow.Flow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.CompletableSubject
import mortar.MortarScope
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import java.io.IOException

class WorkflowPresenterTest : DescribeSpec({
    val hold = WorkflowJob("job", "deploy", "approval", "on_hold", null, "request")
    val resolved = hold.copy(status = "success")

    var scope: MortarScope? = null

    beforeSpec {
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }
    }

    afterEach {
        scope?.destroy()
        scope = null
    }

    afterSpec {
        RxJavaPlugins.reset()
        RxAndroidPlugins.reset()
    }

    fun presenter(
        client: WorkflowClient,
        api: CircleCIAPIClientV1_1 = mockk(),
        activity: MainActivity = mockk(),
        session: SessionService = testSession(api)
    ) =
        WorkflowPresenter(WorkflowScreen("github/owner/repo", "run", "deploy")) { token ->
            token shouldBe "test-token"
            client
        }.apply {
            di = DI {
                bind<MainActivity>() with singleton { activity }
                bind<SessionService>() with singleton { session }
            }
            scope = attachToTestScope(mockk<WorkflowView>())
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

            presenter.approve(hold)
            presenter.approve(hold)

            presenter.canApprove(hold) shouldBe false
            updates.values().last().refreshFailed shouldBe true
            verify(exactly = 1) { client.approve("run", "request") }
        }

        it("clears held jobs and blocks requests when the verified session changes") {
            val session = testSession(mockk())
            val client = mockk<WorkflowClient> {
                every { jobs("run") } returns Single.just(listOf(hold))
            }
            val presenter = presenter(client, session = session)
            val updates = presenter.updates.test()

            presenter.canApprove(hold) shouldBe true
            session.replace("replacement-token").test().assertComplete()
            presenter.approve(hold)
            presenter.refresh()

            updates.values().last() shouldBe WorkflowState()
            presenter.canApprove(hold) shouldBe false
            verify(exactly = 0) { client.approve(any(), any()) }
            verify(exactly = 1) { client.jobs("run") }
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

                presenter.approve(hold)

                updates.values().last() shouldBe WorkflowState(listOf(latestJob), message = message)
            }
        }
    }

    it("opens build details only for numbered jobs with an active session") {
        val activity = mockk<MainActivity>()
        val session = testSession(mockk())
        val presenter = presenter(
            mockk { every { jobs("run") } returns Single.just(emptyList()) },
            activity = activity,
            session = session
        )
        val job = WorkflowJob("build", "test", "build", "success", 42, null)
        val flow = mockk<Flow>(relaxed = true)
        mockkStatic(Flow::class)
        try {
            every { Flow.get(activity) } returns flow

            presenter.openBuild(hold)
            presenter.openBuild(job)
            session.replace("replacement-token").test().assertComplete()
            presenter.openBuild(job)

            verify(exactly = 1) { flow.set(any()) }
            verify { flow.set(BuildScreen(BuildId("github", "owner", "repo", 42))) }
        } finally {
            unmockkStatic(Flow::class)
        }
    }
})
