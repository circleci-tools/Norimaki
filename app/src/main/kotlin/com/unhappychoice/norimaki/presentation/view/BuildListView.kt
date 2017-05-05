package com.unhappychoice.norimaki.presentation.view

import android.content.Context
import android.support.v7.widget.LinearLayoutManager
import android.util.AttributeSet
import android.widget.LinearLayout
import com.unhappychoice.norimaki.extension.subscribeNext
import com.unhappychoice.norimaki.extension.subscribeOnIoObserveOnUI
import com.unhappychoice.norimaki.presentation.adapter.BuildAdapter
import com.unhappychoice.norimaki.presentation.screen.BuildListScreen
import com.unhappychoice.norimaki.presentation.view.core.UseComponent
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import kotlinx.android.synthetic.main.build_list_view.view.*
import javax.inject.Inject

class BuildListView(context: Context, attr: AttributeSet) : UseComponent, LinearLayout(context, attr) {
  @Inject lateinit var presenter: BuildListScreen.Presenter
  private val adapter = BuildAdapter(context)
  private val bag = CompositeDisposable()

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
  }

  override fun onDetachedFromWindow() {
    presenter.dropView(this)
    super.onDetachedFromWindow()
  }
}