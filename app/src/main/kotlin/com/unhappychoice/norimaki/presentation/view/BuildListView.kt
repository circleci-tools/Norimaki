package com.unhappychoice.norimaki.presentation.view

import android.content.Context
import android.content.DialogInterface
import android.os.Parcelable
import android.util.AttributeSet
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import com.jakewharton.rxbinding3.recyclerview.scrollEvents
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.databinding.BuildBranchFilterBinding
import com.unhappychoice.norimaki.databinding.BuildListViewBinding
import com.unhappychoice.norimaki.extension.isNearEnd
import com.unhappychoice.norimaki.extension.subscribeNext
import com.unhappychoice.norimaki.presentation.adapter.BuildAdapter
import com.unhappychoice.norimaki.presentation.presenter.BuildListPresenter
import com.unhappychoice.norimaki.presentation.view.core.BaseView
import com.unhappychoice.norimaki.presentation.view.core.HasMenu
import io.reactivex.rxkotlin.addTo
import org.kodein.di.instance

class BuildListView(context: Context, attr: AttributeSet) : BaseView<BuildListView>(context, attr), HasMenu {
    override val presenter: BuildListPresenter by instance()
    private val adapter = BuildAdapter(context)
    private var branchDialog: AlertDialog? = null
    private val elapsedTimeRefresh = object : Runnable {
        override fun run() {
            refreshElapsedTimes()
            postDelayed(this, 1_000L)
        }
    }

    private val binding by lazy {
        BuildListViewBinding.bind(this)
    }

    private enum class MenuResource(val id: Int) {
        LogOut(0)
    }

    override fun onCreateOptionsMenu(menu: Menu?) {
        menu?.add(Menu.NONE, MenuResource.LogOut.id, Menu.NONE, "Change API Token")
    }

    override fun onOptionsItemSelected(item: MenuItem?) {
        when (item?.itemId) {
            MenuResource.LogOut.id -> presenter.changeAPIToken()
        }
    }

    override fun dispatchRestoreInstanceState(container: SparseArray<Parcelable>?) {
        super.dispatchRestoreInstanceState(container)
        binding.buildsView.restoreHierarchyState(container)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        presenter.takeView(this)

        binding.buildsView.adapter = adapter
        binding.buildsView.layoutManager = GridLayoutManager(context, resources.getInteger(R.integer.build_list_columns))

        presenter.builds
            .subscribeNext { adapter.submitList(it) }
            .addTo(bag)

        bindBranchFilter()

        adapter.onClickItem
            .subscribeNext { presenter.goToBuildView(it) }
            .addTo(bag)

        binding.buildsView.scrollEvents()
            .filter { binding.buildsView.isNearEnd() }
            .subscribeNext { presenter.getBuilds() }
            .addTo(bag)

        presenter.observePolling()
        postDelayed(elapsedTimeRefresh, 1_000L)
    }

    override fun onDetachedFromWindow() {
        branchDialog?.dismiss()
        removeCallbacks(elapsedTimeRefresh)
        presenter.dropView(this)
        super.onDetachedFromWindow()
    }

    private fun bindBranchFilter() {
        binding.branchFilter.isVisible = presenter.canFilterByBranch
        if (!presenter.canFilterByBranch) return

        binding.branchFilter.setOnClickListener { showBranchFilter() }
        presenter.selectedBranch.subscribeNext { branch ->
            val name = branch.ifEmpty { context.getString(R.string.build_list_all_branches) }
            binding.branchFilter.text = context.getString(R.string.build_list_branch_filter, name)
            binding.buildsView.scrollToPosition(0)
        }.addTo(bag)
    }

    private fun showBranchFilter() {
        val content = BuildBranchFilterBinding.inflate(LayoutInflater.from(context))
        val input = content.branchName.apply {
            setAdapter(ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, presenter.recentBranches))
            setOnClickListener { if (text.isEmpty() && adapter.count > 0) showDropDown() }
        }
        branchDialog = AlertDialog.Builder(context)
            .setTitle(R.string.build_list_filter_by_branch)
            .setView(content.root)
            .setNeutralButton(R.string.build_list_all_branches) { _, _ -> presenter.selectBranch(null) }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.build_list_apply_branch) { _, _ ->
                presenter.selectBranch(input.text.toString().trim())
            }
            .create().also { dialog ->
                dialog.setOnDismissListener { branchDialog = null }
                dialog.show()
                val apply = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                apply.isEnabled = false
                input.doAfterTextChanged { apply.isEnabled = !it.isNullOrBlank() }
            }
    }

    private fun refreshElapsedTimes() {
        val recycler = binding.buildsView
        if (!recycler.isShown) return
        recycler.children.forEach { row ->
            (recycler.getChildViewHolder(row) as BuildAdapter.ViewHolder).refreshElapsedTime()
        }
    }
}
