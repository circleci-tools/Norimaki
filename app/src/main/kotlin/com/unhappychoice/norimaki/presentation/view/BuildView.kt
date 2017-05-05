package com.unhappychoice.norimaki.presentation.view

import android.content.Context
import android.support.v7.widget.LinearLayoutManager
import android.util.AttributeSet
import android.widget.LinearLayout
import com.unhappychoice.norimaki.extension.filterNotNull
import com.unhappychoice.norimaki.extension.subscribeNext
import com.unhappychoice.norimaki.extension.subscribeOnIoObserveOnUI
import com.unhappychoice.norimaki.presentation.adapter.BuildStepAdapter
import com.unhappychoice.norimaki.presentation.screen.BuildScreen
import com.unhappychoice.norimaki.presentation.view.core.UseComponent
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import kotlinx.android.synthetic.main.build_view.view.*
import javax.inject.Inject

class BuildView(context: Context, attr: AttributeSet) : LinearLayout(context, attr), UseComponent {
  @Inject lateinit var presenter: BuildScreen.Presenter
  private val adapter = BuildStepAdapter(context)
  private val bag = CompositeDisposable()

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    presenter.takeView(this)

    stepsView.adapter = adapter
    stepsView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)

    presenter.buildSubject
      .map { it.steps }
      .subscribeOnIoObserveOnUI()
      .filterNotNull()
      .subscribeNext {
        adapter.steps.value = it
        adapter.notifyDataSetChanged()
      }.addTo(bag)
  }

  override fun onDetachedFromWindow() {
    presenter.dropView(this)
    super.onDetachedFromWindow()
  }
}
