package com.unhappychoice.norimaki.presentation.presenter

import io.kotest.core.spec.style.DescribeSpec
import io.reactivex.Observable
import java.io.IOException

class BuildStepPresenterTest : DescribeSpec({
    describe("completedActionLogs()") {
        it("should show a missing output notice and continue to the next action") {
            completedActionLogs(listOf("first", "second")) { url ->
                when (url) {
                    "first" -> Observable.error(IOException("HTTP 503"))
                    else -> Observable.just("Second action log")
                }
            }.test().assertResult("\n[Some output could not be loaded.]\n", "Second action log")
        }

        it("should preserve action order when all downloads succeed") {
            completedActionLogs(listOf("first", "second")) { Observable.just(it) }
                .test().assertResult("first", "second")
        }
    }
})
