package com.unhappychoice.norimaki.screen.accessToken

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.jakewharton.rxbinding2.view.clicks
import com.jakewharton.rxbinding2.widget.textChanges
import com.unhappychoice.norimaki.extension.bindTo
import com.unhappychoice.norimaki.extension.subscribeNext
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import kotlinx.android.synthetic.main.api_token_view.view.*
import javax.inject.Inject

class APITokenView(context: Context?, attr: AttributeSet?) : LinearLayout(context, attr) {
  @Inject lateinit var presenter: APITokenScreen.Presenter
  private val bag = CompositeDisposable()

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    presenter.takeView(this)

    accessTokenTextView.setText(presenter.token.value)
    accessTokenTextView.textChanges()
      .map { it.toString() }
      .bindTo(presenter.token)
      .addTo(bag)

    submitButton.clicks()
      .subscribeNext {
        presenter.saveToken()
        presenter.goToBuildList()
      }.addTo(bag)
  }

  override fun onDetachedFromWindow() {
    presenter.dropView(this)
    super.onDetachedFromWindow()
  }
}