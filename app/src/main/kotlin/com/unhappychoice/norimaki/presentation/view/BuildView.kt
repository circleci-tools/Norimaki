package com.unhappychoice.norimaki.presentation.view

import android.content.Context
import android.util.AttributeSet
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.databinding.BuildViewBinding
import com.unhappychoice.norimaki.domain.model.revisionString
import com.unhappychoice.norimaki.extension.subscribeNext
import com.unhappychoice.norimaki.extension.subscribeOnIoObserveOnUI
import com.unhappychoice.norimaki.presentation.adapter.BuildStepAdapter
import com.unhappychoice.norimaki.presentation.presenter.BuildPresenter
import com.unhappychoice.norimaki.presentation.view.core.BaseView
import com.unhappychoice.norimaki.presentation.view.core.HasMenu
import io.reactivex.rxkotlin.addTo
import org.kodein.di.instance

class BuildView(context: Context, attr: AttributeSet) : BaseView<BuildView>(context, attr), HasMenu {
    override val presenter: BuildPresenter by instance()

    private val binding by lazy {
        BuildViewBinding.bind(this)
    }

    private val adapter = BuildStepAdapter(context)
    private var cacheClearDialog: AlertDialog? = null

    override fun onCreateOptionsMenu(menu: Menu?) {
        menu?.add(Menu.NONE, MenuResource.Rebuild.id, Menu.NONE, "Rebuild")
        menu?.add(Menu.NONE, MenuResource.RebuildWithoutCache.id, Menu.NONE, R.string.clear_project_cache_and_rebuild)
        menu?.add(Menu.NONE, MenuResource.Artifacts.id, Menu.NONE, R.string.artifacts)
        if (presenter.hasWorkflow()) menu?.add(Menu.NONE, MenuResource.Workflow.id, Menu.NONE, "Show workflow jobs")
    }

    override fun onOptionsItemSelected(item: MenuItem?) {
        when (item?.itemId) {
            MenuResource.Rebuild.id -> presenter.rebuild()
            MenuResource.RebuildWithoutCache.id -> confirmCacheClearAndRebuild()
            MenuResource.Artifacts.id -> presenter.goToArtifactsScreen()
            MenuResource.Workflow.id -> presenter.goToWorkflowScreen()
        }
    }

    private enum class MenuResource(val id: Int) {
        Rebuild(0), RebuildWithoutCache(1), Workflow(2), Artifacts(3)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        presenter.takeView(this)
        binding.testResults.setOnClickListener { presenter.goToTestResultsScreen() }
        presenter.buildLoadFailed.asObservable()
            .subscribeNext { failed ->
                binding.buildLoadError.visibility = if (failed) View.VISIBLE else View.GONE
            }
            .addTo(bag)
        binding.retryBuildLoad.setOnClickListener {
            if (presenter.buildLoadFailed.value) presenter.pollBuild()
        }

        binding.stepsView.adapter = adapter
        binding.stepsView.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)

        presenter.rebuildFailed.asObservable()
            .subscribeNext { failed -> binding.rebuildError.visibility = if (failed) View.VISIBLE else View.GONE }
            .addTo(bag)

        presenter.steps.asObservable()
            .subscribeOnIoObserveOnUI()
            .subscribeNext(adapter::submitSteps)
            .addTo(bag)

        adapter.onClickItem
            .subscribeNext { presenter.goToBuildStepScreen(it) }
            .addTo(bag)
    }

    internal fun showBuild(build: Build) {
        binding.testResults.isEnabled = true
        updateScreenTitle(presenter.activity, build.revisionString())
    }

    internal fun isCurrentlyDisplayed(): Boolean =
        isAttachedToWindow && (parent as? ViewGroup)?.childCount == 1

    override fun onDetachedFromWindow() {
        cacheClearDialog?.dismiss()
        presenter.dropView(this)
        super.onDetachedFromWindow()
    }

    private fun confirmCacheClearAndRebuild() {
        if (cacheClearDialog != null) return

        val message = context.getString(R.string.clear_project_cache_message, presenter.projectId.toPathString())
        cacheClearDialog = AlertDialog.Builder(context)
            .setTitle(R.string.clear_project_cache_and_rebuild)
            .setMessage(message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.clear_cache_and_rebuild) { _, _ -> presenter.rebuildWithoutCache() }
            .setOnDismissListener { cacheClearDialog = null }
            .show()
    }
}
