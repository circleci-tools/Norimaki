package com.unhappychoice.norimaki.presentation.view

import android.app.Activity
import android.content.DialogInterface
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
