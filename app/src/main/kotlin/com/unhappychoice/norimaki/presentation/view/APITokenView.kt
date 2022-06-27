package com.unhappychoice.norimaki.presentation.view

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import com.jakewharton.rxbinding2.view.clicks
import com.jakewharton.rxbinding2.widget.textChanges
import com.unhappychoice.norimaki.databinding.ApiTokenViewBinding
import com.unhappychoice.norimaki.extension.bindTo
import com.unhappychoice.norimaki.extension.subscribeNext
import com.unhappychoice.norimaki.presentation.presenter.APITokenPresenter
import com.unhappychoice.norimaki.presentation.view.core.BaseView
import io.reactivex.rxkotlin.addTo
import org.kodein.di.instance

class APITokenView(context: Context?, attr: AttributeSet?) : BaseView<APITokenView>(context, attr) {
    override val presenter: APITokenPresenter by instance()

    private val binding by lazy {
        ApiTokenViewBinding.bind(this)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        presenter.takeView(this)

        Log.d("binder", binding?.toString() ?: "null")

        binding.accessTokenTextView.setText(presenter.token.value)
        binding.accessTokenTextView.textChanges()
            .doOnNext { Log.d("D", it.toString()) }
            .map { it.toString() }
            .filter { it.isNotEmpty() }
            .bindTo(presenter.token)
            .addTo(bag)

        binding.submitButton.clicks()
            .doOnNext { Log.d("D", it.toString()) }
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