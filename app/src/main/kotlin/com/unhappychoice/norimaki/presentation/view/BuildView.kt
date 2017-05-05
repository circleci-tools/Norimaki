package com.unhappychoice.norimaki.presentation.view

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.jakewharton.rxbinding2.view.clicks
import com.jakewharton.rxbinding2.widget.textChanges
import com.unhappychoice.norimaki.ActivityComponent
import com.unhappychoice.norimaki.extension.bindTo
import com.unhappychoice.norimaki.extension.filterNotNull
import com.unhappychoice.norimaki.extension.subscribeNext
import com.unhappychoice.norimaki.presentation.screen.APITokenScreen
import com.unhappychoice.norimaki.presentation.screen.BuildScreen
import com.unhappychoice.norimaki.presentation.view.core.UseComponent
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import kotlinx.android.synthetic.main.api_token_view.view.*
import kotlinx.android.synthetic.main.build_view.view.*
import javax.inject.Inject

class BuildView(context: Context?, attr: AttributeSet?) : LinearLayout(context, attr), UseComponent {
  @Inject lateinit var presenter: BuildScreen.Presenter
  private val bag = CompositeDisposable()

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    presenter.takeView(this)

    presenter.buildSubject
      .filterNotNull()
      .subscribeNext { buildTitle.text = it.subject }
      .addTo(bag)
  }

  override fun onDetachedFromWindow() {
    presenter.dropView(this)
    super.onDetachedFromWindow()
  }
}
