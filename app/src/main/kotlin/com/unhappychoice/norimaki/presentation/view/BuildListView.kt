package com.unhappychoice.norimaki.presentation.view

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.util.SparseArray
import android.view.Menu
import android.view.MenuItem
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import com.unhappychoice.norimaki.R
import com.jakewharton.rxbinding2.support.v7.widget.scrollEvents
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
    private var listPending = false
    private var underfillPending = false

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
            .subscribeNext { builds ->
                listPending = true
                adapter.submitList(builds) {
                    listPending = false
                    checkUnderfill()
                }
            }
            .addTo(bag)

        binding.retryPage.setOnClickListener { presenter.getBuilds() }
        presenter.canRetryPage.asObservable()
            .subscribeNext { binding.retryPage.isVisible = it }
            .addTo(bag)

        if (presenter.isWorkflow) {
            presenter.page.asObservable()
                .subscribeNext {
                    underfillPending = true
                    checkUnderfill()
                }
                .addTo(bag)
        }

        adapter.onClickItem
            .subscribeNext { presenter.goToBuildView(it) }
            .addTo(bag)

        binding.buildsView.scrollEvents()
            .filter { !presenter.isWorkflow || it.dy() != 0 }
            .filter { binding.buildsView.isNearEnd() }
            .subscribeNext { presenter.getBuilds() }
            .addTo(bag)
    }

    override fun onDetachedFromWindow() {
        presenter.dropView(this)
        super.onDetachedFromWindow()
    }

    private fun checkUnderfill() {
        if (!underfillPending || listPending || !isAttachedToWindow) return
        underfillPending = false
        val recycler = binding.buildsView
        recycler.doOnPreDraw {
            if (listPending) {
                underfillPending = true
                return@doOnPreDraw
            }
            presenter.getBuildsIfUnderfilled(
                canScroll = recycler.canScrollVertically(-1) || recycler.canScrollVertically(1)
            )
        }
        recycler.invalidate()
    }
}
