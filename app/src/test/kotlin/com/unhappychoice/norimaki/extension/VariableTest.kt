package com.unhappychoice.norimaki.extension

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class VariableTest : DescribeSpec({
    describe("Variable") {
        it("should hold the initial value") {
            Variable(1).value shouldBe 1
        }
        it("should emit the current and following values") {
            val variable = Variable(1)
            val observer = variable.asObservable().test()

            variable.value = 2
            variable.value = 3

            observer.values() shouldBe listOf(1, 2, 3)
        }
        it("should complete on finalize") {
            val variable = Variable("a")
            val observer = variable.asObservable().test()

            variable.finalize()

            observer.assertComplete()
        }
    }
})
