package com.unhappychoice.norimaki.presentation.view

import android.content.Context
import android.support.v7.widget.LinearLayoutManager
import android.util.AttributeSet
import android.view.Menu
import android.view.MenuItem
import com.github.salomonbrys.kodein.instance
import com.jakewharton.rxbinding2.support.v7.widget.scrollEvents
import com.unhappychoice.norimaki.extension.isNearEnd
import com.unhappychoice.norimaki.extension.subscribeNext
import com.unhappychoice.norimaki.extension.subscribeOnIoObserveOnUI
import com.unhappychoice.norimaki.presentation.adapter.BuildAdapter
import com.unhappychoice.norimaki.presentation.presenter.BuildListPresenter
import com.unhappychoice.norimaki.presentation.view.core.BaseView
import com.unhappychoice.norimaki.presentation.view.core.HasMenu
import io.reactivex.rxkotlin.addTo
import kotlinx.android.synthetic.main.build_list_view.view.*

class BuildListView(context: Context, attr: AttributeSet) : BaseView<BuildListView>(context, attr), HasMenu {
    override val presenter: BuildListPresenter by instance()
    private val adapter = BuildAdapter(context)

    override fun onCreateOptionsMenu(menu: Menu?) {
        menu?.add(Menu.NONE, MenuResource.LogOut.id, Menu.NONE, "Change API Token")
    }

    override fun onOptionsItemSelected(item: MenuItem?) {
        when (item?.itemId) {
            MenuResource.LogOut.id -> presenter.changeAPIToken()
        }
    }

    private enum class MenuResource(val id: Int) {
        LogOut(0)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        presenter.takeView(this)

        buildsView.adapter = adapter
        buildsView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)

        presenter.builds.asObservable()
            .subscribeOnIoObserveOnUI()
            .subscribeNext {
                adapter.builds.value = it
                adapter.notifyDataSetChanged()
            }.addTo(bag)

        adapter.onClickItem
            .subscribeNext { presenter.goToBuildView(it) }
            .addTo(bag)

        buildsView.scrollEvents()
            .filter { buildsView.isNearEnd() }
            .subscribeNext { presenter.getBuilds() }
            .addTo(bag)
    }

    override fun onDetachedFromWindow() {
        presenter.dropView(this)
        super.onDetachedFromWindow()
    }
}