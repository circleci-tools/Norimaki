package com.unhappychoice.norimaki.presentation.screen.core

import com.unhappychoice.norimaki.extension.Variable
import io.reactivex.Observable

interface Paginatable {
    val page: Variable<Int>
    val hasMore: Variable<Boolean>

    fun <T> Observable<List<T>>.paginate(): Observable<List<T>> = doOnNext {
        hasMore.value = it.isNotEmpty()
        page.value = page.value + 1
    }
}