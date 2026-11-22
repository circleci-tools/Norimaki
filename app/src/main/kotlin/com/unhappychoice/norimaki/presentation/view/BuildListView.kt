package com.unhappychoice.norimaki.presentation.view

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.util.SparseArray
import android.view.Menu
import android.view.MenuItem
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import com.jakewharton.rxbinding3.recyclerview.scrollEvents
import com.unhappychoice.norimaki.R
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

        adapter.onClickItem
            .subscribeNext { presenter.goToBuildView(it) }
            .addTo(bag)

        binding.retryPage.setOnClickListener { presenter.getBuilds() }
        presenter.canRetryPage.asObservable()
            .subscribeNext { binding.retryPage.isVisible = it }
            .addTo(bag)

        binding.buildsView.scrollEvents()
            .filter { it.dy != 0 || !presenter.canRetryPage.value }
            .filter { binding.buildsView.isNearEnd() }
            .subscribeNext { presenter.getBuilds() }
            .addTo(bag)

        presenter.observePolling()
        postDelayed(elapsedTimeRefresh, 1_000L)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(elapsedTimeRefresh)
        presenter.dropView(this)
        super.onDetachedFromWindow()
    }

    private fun refreshElapsedTimes() {
        val recycler = binding.buildsView
        if (!recycler.isShown) return
        recycler.children.forEach { row ->
            (recycler.getChildViewHolder(row) as BuildAdapter.ViewHolder).refreshElapsedTime()
        }
    }
}
