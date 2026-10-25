package com.unhappychoice.norimaki.extension

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject

class ObservableSugarTest : DescribeSpec({
    describe("Observable+Sugar") {
        it(".subscribeNext() should receive values") {
            val values = mutableListOf<Int>()
            Observable.just(1, 2).subscribeNext { values.add(it) }
            values shouldBe listOf(1, 2)
        }
        it(".subscribeError() should receive the error") {
            val error = RuntimeException("error")
            var received: Throwable? = null
            Observable.error<Int>(error).subscribeError { received = it }
            received shouldBe error
        }
        it(".subscribeCompleted() should be called on completion") {
            var completed = false
            Observable.just(1).subscribeCompleted { completed = true }
            completed shouldBe true
        }
        it(".filterNotNull() should pass non-null values through") {
            Observable.just<Int?>(1, 2).filterNotNull().test().values() shouldBe listOf(1, 2)
        }
        it(".bindTo() should forward values to a subject") {
            val subject = PublishSubject.create<Int>()
            val observer = subject.test()
            Observable.just(1, 2).bindTo(subject)
            observer.values() shouldBe listOf(1, 2)
        }
        it(".bindTo() should update a variable") {
            val variable = Variable(0)
            Observable.just(1, 2).bindTo(variable)
            variable.value shouldBe 2
        }
    }
})
