package com.unhappychoice.norimaki.extension

import io.kotest.core.spec.style.DescribeSpec
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject

class ObservableUtf8Test : DescribeSpec({
    describe("decodeUtf8()") {
        it("should decode a character split between regular responses") {
            Observable.just(
                byteArrayOf(0x41, 0xC3.toByte()),
                byteArrayOf(),
                byteArrayOf(0xA9.toByte(), 0x42)
            ).decodeUtf8().test().assertResult("A", "éB")
        }

        it("should complete a split character using the final response") {
            val polls = PublishSubject.create<ByteArray>()
            val result = polls.concatWith(Observable.just(byteArrayOf(0xA9.toByte())))
                .decodeUtf8()
                .test()

            polls.onNext(byteArrayOf(0xC3.toByte()))
            result.assertNoValues().assertNotComplete()
            polls.onComplete()

            result.assertResult("é")
        }

        it("should replace an incomplete sequence when the final response ends") {
            Observable.just(byteArrayOf(0xE2.toByte(), 0x82.toByte()))
                .decodeUtf8().test().assertResult("\uFFFD")
        }
    }
})
