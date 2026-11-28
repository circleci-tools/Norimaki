package com.unhappychoice.norimaki.presentation.view

import android.content.Context
import android.util.AttributeSet
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Observer
import com.jakewharton.rxbinding3.view.clicks
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.databinding.ApiTokenViewBinding
import com.unhappychoice.norimaki.extension.subscribeNext
import com.unhappychoice.norimaki.presentation.presenter.APITokenPresenter
import com.unhappychoice.norimaki.presentation.view.core.BaseView
import com.unhappychoice.norimaki.presentation.viewmodel.TokenVerification
import io.reactivex.disposables.Disposables
import io.reactivex.rxkotlin.addTo
import org.kodein.di.instance

class APITokenView(context: Context?, attr: AttributeSet?) : BaseView<APITokenView>(context, attr) {
    override val presenter: APITokenPresenter by instance()

    private val verificationObserver = Observer<TokenVerification>(::renderVerification)

    private val binding by lazy {
        ApiTokenViewBinding.bind(this)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        presenter.takeView(this)

        prefillToken()
        binding.submitButton.clicks()
            .subscribeNext { presenter.model.submit(binding.accessTokenTextView.text.toString().trim()) }
            .addTo(bag)
        presenter.observeVerification(verificationObserver)
    }

    override fun onDetachedFromWindow() {
        presenter.model.verification.removeObserver(verificationObserver)
        presenter.dropView(this)
        super.onDetachedFromWindow()
    }

    fun onScreenLeft() {
        presenter.model.reset()
    }

    private fun prefillToken() {
        val field = binding.accessTokenTextView
        var edited = false
        val watcher = field.doAfterTextChanged { edited = true }
        Disposables.fromAction { field.removeTextChangedListener(watcher) }.addTo(bag)
        presenter.model.token().onErrorReturnItem("").subscribe { token ->
            field.removeTextChangedListener(watcher)
            if (!edited) field.setText(token)
        }.addTo(bag)
    }

    private fun renderVerification(state: TokenVerification) {
        val submitting = state == TokenVerification.VERIFYING || state == TokenVerification.VERIFIED
        binding.accessTokenTextView.isEnabled = !submitting
        binding.submitButton.isEnabled = !submitting
        binding.accessTokenTextView.error = when (state) {
            TokenVerification.REQUIRED -> context.getString(R.string.api_token_required)
            TokenVerification.REJECTED -> context.getString(R.string.api_token_verification_failed)
            else -> null
        }
        if (state == TokenVerification.VERIFIED) presenter.goToBuildList()
    }
}
