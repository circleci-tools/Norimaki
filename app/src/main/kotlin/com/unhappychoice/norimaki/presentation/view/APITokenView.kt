package com.unhappychoice.norimaki.presentation.view

import android.content.Context
import android.util.AttributeSet
import com.jakewharton.rxbinding2.view.clicks
import com.jakewharton.rxbinding2.widget.textChanges
import com.unhappychoice.norimaki.extension.bindTo
import com.unhappychoice.norimaki.extension.subscribeNext
import com.unhappychoice.norimaki.presentation.presenter.APITokenPresenter
import com.unhappychoice.norimaki.presentation.view.core.BaseView
import io.reactivex.rxkotlin.addTo
import kotlinx.android.synthetic.main.api_token_view.view.*
import org.kodein.di.generic.instance

class APITokenView(context: Context?, attr: AttributeSet?) : BaseView<APITokenView>(context, attr) {
    override val presenter: APITokenPresenter by instance()

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