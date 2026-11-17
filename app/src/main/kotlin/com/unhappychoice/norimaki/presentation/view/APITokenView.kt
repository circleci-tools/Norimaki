package com.unhappychoice.norimaki.presentation.view

import android.content.Context
import android.util.AttributeSet
import com.jakewharton.rxbinding2.view.clicks
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.databinding.ApiTokenViewBinding
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

        binding.accessTokenTextView.setText(presenter.savedToken)
        binding.submitButton.clicks()
            .subscribeNext { submitToken() }
            .addTo(bag)
    }

    override fun onDetachedFromWindow() {
        presenter.dropView(this)
        super.onDetachedFromWindow()
    }

    private fun submitToken() {
        val field = binding.accessTokenTextView
        val token = field.text.toString().trim()
        field.error = null
        field.isEnabled = false
        binding.submitButton.isEnabled = false
        presenter.verifyToken(token)
            .subscribe({ presenter.goToBuildList() }, {
                field.isEnabled = true
                binding.submitButton.isEnabled = true
                field.error = context.getString(
                    if (token.isBlank()) R.string.api_token_required else R.string.api_token_verification_failed
                )
            })
            .addTo(bag)
    }
}
