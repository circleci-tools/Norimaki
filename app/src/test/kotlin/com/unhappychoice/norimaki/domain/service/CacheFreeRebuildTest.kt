package com.unhappychoice.norimaki.domain.service

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Build
import com.unhappychoice.norimaki.domain.model.BuildId
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject

class CacheFreeRebuildTest : DescribeSpec({
    describe("Rebuild without cache") {
        val buildId = BuildId("github", "owner", "repo", 123)
        val build = mockk<Build>()

        it("should deliver the retry result to an attached observer") {
            val api = mockk<CircleCIAPIClientV1_1> {
                every { deleteCache("github", "owner", "repo") } returns Observable.just(Unit)
                every { retryBuild("github", "owner", "repo", 123) } returns Observable.just(build)
            }

            api.rebuildWithoutCache(buildId).test().assertResult(build)
        }

        it("should skip retry when cache deletion fails") {
            val deletion = PublishSubject.create<Unit>()
            val failure = IllegalStateException("Cache deletion failed")
            val api = mockk<CircleCIAPIClientV1_1> {
                every { deleteCache("github", "owner", "repo") } returns deletion
            }
            val observer = api.rebuildWithoutCache(buildId).test()

            deletion.onError(failure)

            observer.assertFailure(IllegalStateException::class.java).assertError(failure)
            verify(exactly = 0) { api.retryBuild(any(), any(), any(), any()) }
        }
    }
})
