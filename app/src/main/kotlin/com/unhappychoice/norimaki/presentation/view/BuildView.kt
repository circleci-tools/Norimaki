package com.unhappychoice.norimaki.presentation.view

import android.content.Context
import android.support.v7.widget.LinearLayoutManager
import android.util.AttributeSet
import android.view.Menu
import android.view.MenuItem
import com.github.salomonbrys.kodein.instance
import com.unhappychoice.norimaki.extension.subscribeNext
import com.unhappychoice.norimaki.extension.subscribeOnIoObserveOnUI
import com.unhappychoice.norimaki.presentation.adapter.BuildStepAdapter
import com.unhappychoice.norimaki.presentation.presenter.BuildPresenter
import com.unhappychoice.norimaki.presentation.view.core.BaseView
import com.unhappychoice.norimaki.presentation.view.core.HasMenu
import io.reactivex.rxkotlin.addTo
import kotlinx.android.synthetic.main.build_view.view.*

class BuildView(context: Context, attr: AttributeSet) : BaseView<BuildView>(context, attr), HasMenu {
    override val presenter: BuildPresenter by instance()

    private val adapter = BuildStepAdapter(context)

    override fun onCreateOptionsMenu(menu: Menu?) {
        menu?.add(Menu.NONE, MenuResource.Rebuild.id, Menu.NONE, "Rebuild")
        menu?.add(Menu.NONE, MenuResource.RebuildWithoutCache.id, Menu.NONE, "Rebuild without cache")
    }

    override fun onOptionsItemSelected(item: MenuItem?) {
        when (item?.itemId) {
            MenuResource.Rebuild.id -> presenter.rebuild()
            MenuResource.RebuildWithoutCache.id -> presenter.rebuildWithoutCache()
        }
    }

    private enum class MenuResource(val id: Int) {
        Rebuild(0), RebuildWithoutCache(1)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        presenter.takeView(this)

        stepsView.adapter = adapter
        stepsView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)

        presenter.steps.asObservable()
            .subscribeOnIoObserveOnUI()
            .subscribeNext {
                adapter.steps.value = it
                adapter.notifyDataSetChanged()
            }.addTo(bag)

        adapter.onClickItem
            .subscribeNext { presenter.goToBuildStepScreen(it) }
            .addTo(bag)
    }

    override fun onDetachedFromWindow() {
        presenter.dropView(this)
        super.onDetachedFromWindow()
    }
}
