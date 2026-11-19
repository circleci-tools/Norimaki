package com.unhappychoice.norimaki.presentation.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.databinding.WorkflowViewBinding
import com.unhappychoice.norimaki.domain.model.WorkflowJob
import com.unhappychoice.norimaki.presentation.presenter.WorkflowPresenter
import com.unhappychoice.norimaki.presentation.presenter.WorkflowState
import com.unhappychoice.norimaki.presentation.view.core.BaseView
import io.reactivex.disposables.Disposable
import org.kodein.di.instance

class WorkflowView(context: Context, attr: AttributeSet) : BaseView<WorkflowView>(context, attr) {
    override val presenter: WorkflowPresenter by instance()
    private val binding by lazy { WorkflowViewBinding.bind(this) }
    private val adapter = JobsAdapter()
    private var updates: Disposable? = null
    private var confirmation: AlertDialog? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        presenter.takeView(this)
        binding.jobs.adapter = adapter
        binding.jobs.setOnItemClickListener { _, _, position, _ ->
            adapter.getItem(position)?.let(::openJob)
        }
        binding.refresh.setOnClickListener { presenter.refresh() }
        updates = presenter.updates.subscribe(::render)
    }

    override fun onDetachedFromWindow() {
        confirmation?.dismiss()
        updates?.dispose()
        presenter.dropView(this)
        super.onDetachedFromWindow()
    }

    private fun openJob(job: WorkflowJob) {
        if (presenter.canApprove(job)) confirmApproval(job) else presenter.openBuild(job)
    }

    private fun confirmApproval(job: WorkflowJob) {
        val screen = presenter.screen
        confirmation = AlertDialog.Builder(context)
            .setTitle(R.string.workflow_approve_title)
            .setMessage(resources.getString(
                R.string.workflow_approve_confirmation, job.name, screen.projectName, screen.getTitle(), screen.workflowId
            ))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.workflow_approve) { _, _ -> presenter.approve(job) }
            .show()
    }

    private fun render(state: WorkflowState) {
        binding.refresh.isEnabled = !state.busy
        binding.jobs.isEnabled = !state.busy
        binding.progress.isVisible = state.busy
        val messages = listOfNotNull(
            state.message?.let { resources.getString(it) },
            resources.getString(R.string.workflow_load_error).takeIf { state.refreshFailed },
            resources.getString(R.string.workflow_no_jobs)
                .takeIf { state.jobs.isEmpty() && !state.busy && state.message == null }
        )
        binding.message.text = messages.joinToString("\n")
        binding.message.isVisible = messages.isNotEmpty()
        adapter.setNotifyOnChange(false)
        adapter.clear()
        adapter.addAll(state.jobs)
        adapter.notifyDataSetChanged()
    }

    private inner class JobsAdapter : ArrayAdapter<WorkflowJob>(context, android.R.layout.simple_list_item_2) {
        override fun areAllItemsEnabled() = false

        override fun isEnabled(position: Int): Boolean = getItem(position)?.let {
            presenter.canApprove(it) || it.jobNumber != null
        } == true

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView ?: LayoutInflater.from(context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            val job = checkNotNull(getItem(position))
            row.findViewById<TextView>(android.R.id.text1).text = job.name
            val status = job.status.replace('_', ' ')
            row.findViewById<TextView>(android.R.id.text2).text = when {
                presenter.canApprove(job) -> resources.getString(R.string.workflow_job_approval, status)
                job.jobNumber != null -> resources.getString(R.string.workflow_job_build, status, job.jobNumber)
                else -> status
            }
            return row
        }
    }
}
