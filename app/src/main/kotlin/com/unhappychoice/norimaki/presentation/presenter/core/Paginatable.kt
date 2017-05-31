package com.unhappychoice.norimaki.presentation.presenter.core

interface Paginatable {
    val page: com.unhappychoice.norimaki.extension.Variable<Int>
    val hasMore: com.unhappychoice.norimaki.extension.Variable<Boolean>

    fun <T> io.reactivex.Observable<List<T>>.paginate(): io.reactivex.Observable<List<T>> = doOnNext {
        hasMore.value = it.isNotEmpty()
        page.value = page.value + 1
    }
}