package com.unhappychoice.norimaki.extension

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class RecyclerViewNearEndTest : DescribeSpec({
    fun recyclerView(totalCount: Int, lastVisiblePosition: Int): RecyclerView {
        val layoutManager = mockk<LinearLayoutManager> {
            every { itemCount } returns totalCount
            every { findLastVisibleItemPosition() } returns lastVisiblePosition
        }
        return mockk<RecyclerView> {
            every { getLayoutManager() } returns layoutManager
        }
    }

    describe("RecyclerView.isNearEnd()") {
        it("is near the end within the last four items") {
            recyclerView(totalCount = 10, lastVisiblePosition = 6).isNearEnd() shouldBe true
            recyclerView(totalCount = 10, lastVisiblePosition = 9).isNearEnd() shouldBe true
        }
        it("is not near the end when more than four items remain") {
            recyclerView(totalCount = 10, lastVisiblePosition = 5).isNearEnd() shouldBe false
        }
    }
})
