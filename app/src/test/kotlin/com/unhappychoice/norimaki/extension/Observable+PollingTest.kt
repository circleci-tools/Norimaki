package com.unhappychoice.norimaki.extension

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.reactivex.Observable
import io.reactivex.schedulers.TestScheduler
import java.util.concurrent.TimeUnit

class ObservablePollingTest : DescribeSpec({
    describe("polling()") {
        it("should fetch immediately and then every period") {
            val scheduler = TestScheduler()
            var count = 0
            val observer = polling(5, TimeUnit.SECONDS, scheduler) { Observable.just(++count) }.test()

            scheduler.advanceTimeBy(10, TimeUnit.SECONDS)

            observer.values() shouldBe listOf(1, 2, 3)
            observer.dispose()
        }

        it("should keep polling after a failed fetch") {
            val scheduler = TestScheduler()
            var count = 0
            val observer = polling(1, TimeUnit.SECONDS, scheduler) {
                count += 1
                if (count == 1) Observable.error(RuntimeException()) else Observable.just(count)
            }.test()

            scheduler.advanceTimeBy(2, TimeUnit.SECONDS)

            observer.values() shouldBe listOf(2, 3)
            observer.errorCount() shouldBe 0
            observer.dispose()
        }
    }
})
