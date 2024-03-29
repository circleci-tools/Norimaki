package com.unhappychoice.norimaki.presentation.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.unhappychoice.norimaki.databinding.BuildViewBinding
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

        binding.stepsView.adapter = adapter
        binding.stepsView.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)

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
