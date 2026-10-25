package com.unhappychoice.norimaki.presentation.core

import flow.Direction
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ScreenTransitionTest : DescribeSpec({
    describe("ScreenTransition.of()") {
        it("should slide the incoming screen in from the right when going forward") {
            ScreenTransition.of(Direction.FORWARD, 100f) shouldBe ScreenTransition(100f, 1f, -100f * 0.3f, true)
        }
        it("should slide the outgoing screen out to the right when going backward") {
            ScreenTransition.of(Direction.BACKWARD, 100f) shouldBe ScreenTransition(-100f * 0.3f, 1f, 100f, false)
        }
        it("should fade the incoming screen in when replacing") {
            ScreenTransition.of(Direction.REPLACE, 100f) shouldBe ScreenTransition(0f, 0f, 0f, true)
        }
    }
})
