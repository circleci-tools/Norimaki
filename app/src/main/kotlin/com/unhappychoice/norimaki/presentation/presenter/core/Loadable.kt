package com.unhappychoice.norimaki.presentation.presenter.core

import com.unhappychoice.norimaki.extension.Variable
import io.reactivex.Observable

interface Loadable {
    val isLoading: com.unhappychoice.norimaki.extension.Variable<Boolean>

    fun <T> io.reactivex.Observable<T>.startLoading(): io.reactivex.Observable<T> {
        isLoading.value = true
        return this
            .doOnError { isLoading.value = false }
            .doOnComplete { isLoading.value = false }
    }
}