package com.unhappychoice.norimaki.presentation.presenter

import io.kotest.core.spec.style.DescribeSpec
import io.reactivex.Observable
import java.io.IOException

class BuildStepPresenterTest : DescribeSpec({
    describe("completedActionLogs()") {
        it("should keep earlier output, show a missing output notice, and continue") {
            completedActionLogs(listOf("first", "second", "third")) { url ->
                when (url) {
                    "second" -> Observable.error(IOException("HTTP 503"))
                    else -> Observable.just(url)
                }
            }.test().assertResult("first", "\n[Some output could not be loaded.]\n", "third")
        }

        it("should preserve action order when all downloads succeed") {
            completedActionLogs(listOf("first", "second")) { Observable.just(it) }
                .test().assertResult("first", "second")
        }
    }
})
