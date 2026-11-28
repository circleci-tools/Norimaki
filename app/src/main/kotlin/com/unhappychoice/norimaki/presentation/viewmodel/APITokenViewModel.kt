package com.unhappychoice.norimaki.presentation.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.unhappychoice.norimaki.domain.service.SessionService
import com.unhappychoice.norimaki.domain.service.TokenSaveException
import io.reactivex.Single
import io.reactivex.disposables.SerialDisposable

enum class TokenVerification {
    IDLE, VERIFYING, VERIFIED, REQUIRED, REJECTED, SAVE_FAILED
}

class APITokenViewModel(private val session: SessionService) : ViewModel() {
    private val result = MutableLiveData(TokenVerification.IDLE)
    private val request = SerialDisposable()
    private var submittedToken: String? = null

    val verification: LiveData<TokenVerification> = result
    fun token(): Single<String> = submittedToken?.let { Single.just(it) } ?: session.savedToken()

    fun submit(token: String) {
        if (result.value == TokenVerification.VERIFYING || result.value == TokenVerification.VERIFIED) return

        submittedToken = token
        result.value = TokenVerification.VERIFYING
        request.set(session.replace(token).subscribe(
            { result.value = TokenVerification.VERIFIED },
            { error ->
                result.value = when {
                    error is TokenSaveException -> TokenVerification.SAVE_FAILED
                    token.isBlank() -> TokenVerification.REQUIRED
                    else -> TokenVerification.REJECTED
                }
            }
        ))
    }

    fun reset() {
        request.set(null)
        submittedToken = null
        result.value = TokenVerification.IDLE
    }

    override fun onCleared() {
        request.dispose()
    }
}
