package com.unhappychoice.norimaki.presentation.presenter.core

import com.unhappychoice.norimaki.extension.Variable
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject

class PresenterCoreTest : DescribeSpec({
    describe("Loadable") {
        val loadable = object : Loadable {
            override val isLoading = Variable(false)
        }

        it("should be loading until the observable completes") {
            val subject = PublishSubject.create<Int>()
            val observer = with(loadable) { subject.startLoading() }.test()

            loadable.isLoading.value shouldBe true
            subject.onComplete()
            loadable.isLoading.value shouldBe false
            observer.dispose()
        }
        it("should stop loading on error") {
            with(loadable) { Observable.error<Int>(RuntimeException()).startLoading() }.test()
            loadable.isLoading.value shouldBe false
        }
    }

    describe("Refreshable") {
        val refreshable = object : Refreshable {
            override val isRefreshing = Variable(false)
            override fun refresh() {}
        }

        it("should be refreshing until the observable completes") {
            val subject = PublishSubject.create<Int>()
            val observer = with(refreshable) { subject.startRefresh() }.test()

            refreshable.isRefreshing.value shouldBe true
            subject.onComplete()
            refreshable.isRefreshing.value shouldBe false
            observer.dispose()
        }
    }

    describe("Paginatable") {
        fun paginatable() = object : Paginatable {
            override val page = Variable(0)
            override val hasMore = Variable(true)
        }

        it("should advance the page for each result") {
            val subject = paginatable()
            with(subject) { Observable.just(listOf(1), listOf(2)).paginate() }.test()
            subject.page.value shouldBe 2
            subject.hasMore.value shouldBe true
        }
        it("should have no more pages after an empty result") {
            val subject = paginatable()
            with(subject) { Observable.just(listOf<Int>()).paginate() }.test()
            subject.hasMore.value shouldBe false
        }
    }
})
