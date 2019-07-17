package com.unhappychoice.norimaki.presentation.view

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.util.SparseArray
import android.view.Menu
import android.view.MenuItem
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
import org.kodein.di.generic.instance

class BuildListView(context: Context, attr: AttributeSet) : BaseView<BuildListView>(context, attr), HasMenu {
    override val presenter: BuildListPresenter by instance()
    private val adapter = BuildAdapter(context)

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
        buildsView.restoreHierarchyState(container)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        presenter.takeView(this)

        buildsView.adapter = adapter
        buildsView.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)

        adapter.builds.value = presenter.builds.value

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
