package com.unhappychoice.norimaki.presentation.presenter

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.unhappychoice.norimaki.MainActivity
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.domain.model.BuildId
import com.unhappychoice.norimaki.domain.model.WorkflowJob
import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.infrastructure.workflow.WorkflowClient
import com.unhappychoice.norimaki.infrastructure.workflow.WorkflowHttpException
import com.unhappychoice.norimaki.presentation.screen.APITokenScreen
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
import io.reactivex.subjects.SingleSubject
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
        session: SessionService = testSession(api),
        expectedToken: String = "test-token",
        view: WorkflowView = mockk()
    ) =
        WorkflowPresenter(WorkflowScreen("github/owner/repo", "run", "deploy")) { token ->
            token shouldBe expectedToken
            client
        }.apply {
            di = DI {
                bind<MainActivity>() with singleton { activity }
                bind<SessionService>() with singleton { session }
            }
            scope = attachToTestScope(view)
        }

    describe("workflow approvals") {
        it("reports a failed initial load and retries on refresh") {
            val client = mockk<WorkflowClient> {
                every { jobs("run") } returnsMany listOf(Single.error(IOException()), Single.just(listOf(hold)))
            }
            val presenter = presenter(client)
            val updates = presenter.updates.test()

            updates.values().last() shouldBe WorkflowState(message = R.string.workflow_load_error)
            presenter.refresh()
            updates.values().last() shouldBe WorkflowState(listOf(hold))
        }

        it("ignores refresh while loading and rejects approvals missing from the loaded jobs") {
            val jobs = SingleSubject.create<List<WorkflowJob>>()
            var loads = 0
            val client = mockk<WorkflowClient> {
                every { jobs("run") } returns jobs.doOnSubscribe { loads++ }
            }
            val presenter = presenter(client)

            presenter.refresh()
            jobs.onSuccess(emptyList())
            presenter.approve(hold)

            loads shouldBe 1
            verify(exactly = 0) { client.approve(any(), any()) }
        }

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

        it("retains an accepted approval when the workflow screen is recreated during refresh") {
            val session = testSession(mockk())
            val post = CompletableSubject.create()
            val delayedRefresh = SingleSubject.create<List<WorkflowJob>>()
            val client = mockk<WorkflowClient> {
                every { jobs("run") } returnsMany listOf(
                    Single.just(listOf(hold)), delayedRefresh, Single.just(listOf(hold))
                )
                every { approve("run", "request") } returnsMany listOf(post, Completable.complete())
            }
            val view = mockk<WorkflowView>()
            val original = presenter(client, session = session, view = view)
            val originalUpdates = original.updates.test()

            original.approve(hold)
            post.onComplete()
            delayedRefresh.hasObservers() shouldBe true
            originalUpdates.values().last() shouldBe WorkflowState(
                listOf(hold), busy = true, message = R.string.workflow_approval_submitted
            )

            original.dropView(view)
            scope?.destroy()
            delayedRefresh.hasObservers() shouldBe false
            val recreated = presenter(client, session = session)
            delayedRefresh.onSuccess(listOf(resolved))

            recreated.updates.test().values().last() shouldBe WorkflowState(
                listOf(hold), message = R.string.workflow_approval_submitted
            )
            recreated.canApprove(hold) shouldBe false
            recreated.approve(hold)
            verify(exactly = 1) { client.approve("run", "request") }

            session.replace("replacement-token").test().assertComplete()
            scope?.destroy()
            val replacement = presenter(client, session = session, expectedToken = "replacement-token")

            replacement.updates.test().values().last() shouldBe WorkflowState(listOf(hold))
            replacement.canApprove(hold) shouldBe true
            replacement.approve(hold)
            verify(exactly = 2) { client.approve("run", "request") }
        }

        it("keeps the POST in flight when the submitting screen is dropped") {
            val session = testSession(mockk())
            val post = CompletableSubject.create()
            val client = mockk<WorkflowClient> {
                every { jobs("run") } returns Single.just(listOf(hold))
                every { approve("run", "request") } returns post
            }
            val view = mockk<WorkflowView>()
            val original = presenter(client, session = session, view = view)
            original.approve(hold)

            original.dropView(view)
            scope?.destroy()
            val recreated = presenter(client, session = session)
            recreated.canApprove(hold) shouldBe false
            recreated.approve(hold)
            post.hasObservers() shouldBe true
            post.onComplete()

            recreated.updates.test().values().last() shouldBe WorkflowState(
                listOf(hold), message = R.string.workflow_approval_submitted
            )
            recreated.canApprove(hold) shouldBe false
            verify(exactly = 1) { client.approve("run", "request") }
        }

        it("retries a failed refresh without resubmitting an accepted approval") {
            val client = mockk<WorkflowClient> {
                every { jobs("run") } returnsMany listOf(
                    Single.just(listOf(hold)),
                    Single.error(IOException()),
                    Single.just(listOf(resolved))
                )
                every { approve("run", "request") } returns Completable.complete()
            }
            val presenter = presenter(client)
            val updates = presenter.updates.test()

            presenter.approve(hold)
            presenter.approve(hold)

            presenter.canApprove(hold) shouldBe false
            updates.values().last().refreshFailed shouldBe true

            presenter.refresh()

            updates.values().last() shouldBe WorkflowState(listOf(resolved))
            verify(exactly = 3) { client.jobs("run") }
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

        it("keeps the held job actionable when approval and refresh both fail") {
            val client = mockk<WorkflowClient> {
                every { jobs("run") } returnsMany listOf(Single.just(listOf(hold)), Single.error(IOException()))
                every { approve("run", "request") } returns Completable.error(IOException())
            }
            val presenter = presenter(client)
            val updates = presenter.updates.test()

            presenter.approve(hold)

            updates.values().last() shouldBe WorkflowState(
                listOf(hold), message = R.string.workflow_approval_error, refreshFailed = true
            )
            presenter.canApprove(hold) shouldBe true
        }

        it("reconciles a resolved hold on manual refresh after the automatic refresh fails") {
            val client = mockk<WorkflowClient> {
                every { jobs("run") } returnsMany listOf(
                    Single.just(listOf(hold)),
                    Single.error(IOException()),
                    Single.just(listOf(resolved))
                )
                every { approve("run", "request") } returns Completable.error(WorkflowHttpException(400))
            }
            val presenter = presenter(client)
            val updates = presenter.updates.test()

            presenter.approve(hold)

            updates.values().last() shouldBe WorkflowState(
                listOf(hold), message = R.string.workflow_approval_error, refreshFailed = true
            )

            presenter.refresh()

            updates.values().last() shouldBe WorkflowState(listOf(resolved), message = R.string.workflow_approval_resolved)
            verify(exactly = 3) { client.jobs("run") }
            verify(exactly = 1) { client.approve("run", "request") }
        }

        listOf(
            Triple(401, hold, R.string.workflow_approval_permission),
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
        val jobs = SingleSubject.create<List<WorkflowJob>>()
        val presenter = presenter(
            mockk { every { jobs("run") } returns jobs },
            activity = activity,
            session = session
        )
        val job = WorkflowJob("build", "test", "build", "success", 42, null)
        val flow = mockk<Flow>(relaxed = true)
        mockkStatic(Flow::class)
        try {
            every { Flow.get(activity) } returns flow

            presenter.openBuild(job)
            verify(exactly = 0) { flow.set(any()) }

            jobs.onSuccess(emptyList())
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

    it("opens the token screen to change credentials") {
        val activity = mockk<MainActivity>()
        val presenter = presenter(
            mockk { every { jobs("run") } returns Single.just(emptyList()) },
            activity = activity
        )
        val flow = mockk<Flow>(relaxed = true)
        mockkStatic(Flow::class)
        try {
            every { Flow.get(activity) } returns flow

            presenter.changeAPIToken()

            verify(exactly = 1) { flow.set(ofType<APITokenScreen>()) }
        } finally {
            unmockkStatic(Flow::class)
        }
    }
})
