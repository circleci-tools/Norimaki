package com.unhappychoice.norimaki.presentation.view

import android.app.Activity
import android.content.DialogInterface
import android.view.View
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.domain.model.WorkflowJob
import com.unhappychoice.norimaki.presentation.presenter.WorkflowPresenter
import com.unhappychoice.norimaki.presentation.presenter.WorkflowState
import com.unhappychoice.norimaki.presentation.screen.WorkflowScreen
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkflowViewTest {
    @Test
    fun rendersLoadingErrorsAndJobRowsAndDispatchesRefreshAndBuildClicks() {
        val build = WorkflowJob("build", "test", "build", "success", 42, null)
        val resolved = WorkflowJob("hold", "release", "approval", "success", null, "approval-id")
        val states = BehaviorSubject.createDefault(WorkflowState(busy = true))
        val presenter = mockk<WorkflowPresenter>(relaxUnitFun = true) {
            every { updates } returns states
            every { canApprove(any()) } returns false
        }

        Robolectric.buildActivity(Activity::class.java).use { controller ->
            val activity = controller.setup().get()
            activity.setTheme(R.style.AppTheme)
            val view = activity.layoutInflater.inflate(R.layout.workflow_view, null) as WorkflowView
            view.di = DI { bind<WorkflowPresenter>() with singleton { presenter } }
            activity.setContentView(view)
            val jobs = view.findViewById<ListView>(R.id.jobs)
            val refresh = view.findViewById<View>(R.id.refresh)
            val progress = view.findViewById<View>(R.id.progress)
            val message = view.findViewById<TextView>(R.id.message)

            assertFalse(jobs.isEnabled)
            assertFalse(refresh.isEnabled)
            assertEquals(View.VISIBLE, progress.visibility)
            assertEquals(View.GONE, message.visibility)

            states.onNext(WorkflowState())
            assertEquals(activity.getString(R.string.workflow_no_jobs), message.text.toString())
            states.onNext(WorkflowState(message = R.string.workflow_load_error))
            assertEquals(activity.getString(R.string.workflow_load_error), message.text.toString())

            states.onNext(
                WorkflowState(
                    jobs = listOf(build, resolved),
                    message = R.string.workflow_approval_submitted,
                    refreshFailed = true
                )
            )
            assertEquals(
                listOf(R.string.workflow_approval_submitted, R.string.workflow_load_error)
                    .joinToString("\n") { activity.getString(it) },
                message.text.toString()
            )
            assertTrue(jobs.isEnabled)
            assertTrue(refresh.isEnabled)
            assertEquals(View.GONE, progress.visibility)
            val adapter = jobs.adapter
            assertTrue(adapter.isEnabled(0))
            assertFalse(adapter.isEnabled(1))
            val row = adapter.getView(0, null, jobs)
            assertEquals(
                activity.getString(R.string.workflow_job_build, "success", 42),
                row.findViewById<TextView>(android.R.id.text2).text.toString()
            )
            jobs.performItemClick(row, 0, 0)
            assertSame(row, adapter.getView(1, row, jobs))
            assertEquals("success", row.findViewById<TextView>(android.R.id.text2).text.toString())

            refresh.performClick()
            verify(exactly = 1) { presenter.openBuild(build) }
            verify(exactly = 1) { presenter.refresh() }
        }
    }

    @Test
    fun identifiesTheRunAndOnlyApprovesAfterConfirmation() {
        val hold = WorkflowJob("hold-job", "release", "approval", "on_hold", null, "approval-id")
        val presenter = mockk<WorkflowPresenter>(relaxUnitFun = true) {
            every { screen } returns WorkflowScreen("github/owner/repo", "workflow-run-id", "deploy")
            every { updates } returns Observable.just(WorkflowState(listOf(hold)))
            every { canApprove(hold) } returns true
        }

        Robolectric.buildActivity(Activity::class.java).use { controller ->
            val activity = controller.setup().get()
            activity.setTheme(R.style.AppTheme)
            val view = activity.layoutInflater.inflate(R.layout.workflow_view, null) as WorkflowView
            view.di = DI { bind<WorkflowPresenter>() with singleton { presenter } }
            activity.setContentView(view)
            val jobs = view.findViewById<ListView>(R.id.jobs)
            val row = jobs.adapter.getView(0, null, jobs)
            assertTrue(row.findViewById<TextView>(android.R.id.text2).text.contains("on hold"))

            jobs.performItemClick(row, 0, 0)
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            val message = dialog.findViewById<TextView>(android.R.id.message)?.text?.toString().orEmpty()
            listOf("release", "github/owner/repo", "deploy", "workflow-run-id").forEach {
                assertTrue(message.contains(it))
            }
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
            shadowOf(activity.mainLooper).idle()
            verify(exactly = 0) { presenter.approve(any()) }

            jobs.performItemClick(row, 0, 0)
            (ShadowDialog.getLatestDialog() as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            shadowOf(activity.mainLooper).idle()

            verify(exactly = 1) { presenter.approve(hold) }
        }
    }
}
