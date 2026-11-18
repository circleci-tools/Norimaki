package com.unhappychoice.norimaki.domain.service

import com.github.unhappychoice.circleci.CircleCIAPIClientV1_1
import com.github.unhappychoice.circleci.v1.response.Build
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject

class CacheFreeRebuildTest : DescribeSpec({
    describe("Rebuild without cache") {
        val build = mockk<Build> {
            every { vcsUrl } returns "https://github.com/owner/repo"
            every { username } returns "owner"
            every { reponame } returns "repo"
            every { buildNum } returns 123
        }

        it("should submit one retry after the view stops observing a pending deletion") {
            val deletion = PublishSubject.create<Unit>()
            val api = mockk<CircleCIAPIClientV1_1> {
                every { deleteCache("github", "owner", "repo") } returns deletion
                every { retryBuild("github", "owner", "repo", 123) } returns Observable.just(build)
            }
            val result = api.rebuildWithoutCache(build, Schedulers.trampoline())
            val viewObserver = result.test()
            verify(exactly = 1) { api.deleteCache("github", "owner", "repo") }
            verify(exactly = 0) { api.retryBuild(any(), any(), any(), any()) }

            viewObserver.dispose()
            deletion.onNext(Unit)
            deletion.onComplete()

            viewObserver.assertEmpty()
            result.test().assertResult(build)
            verify(exactly = 1) { api.deleteCache("github", "owner", "repo") }
            verify(exactly = 1) { api.retryBuild("github", "owner", "repo", 123) }
        }

        it("should deliver the retry result to an attached observer") {
            val api = mockk<CircleCIAPIClientV1_1> {
                every { deleteCache("github", "owner", "repo") } returns Observable.just(Unit)
                every { retryBuild("github", "owner", "repo", 123) } returns Observable.just(build)
            }

            api.rebuildWithoutCache(build, Schedulers.trampoline()).test().assertResult(build)
        }

        it("should skip retry when cache deletion fails") {
            val deletion = PublishSubject.create<Unit>()
            val failure = IllegalStateException("Cache deletion failed")
            val api = mockk<CircleCIAPIClientV1_1> {
                every { deleteCache("github", "owner", "repo") } returns deletion
            }
            val observer = api.rebuildWithoutCache(build, Schedulers.trampoline()).test()

            deletion.onError(failure)

            observer.assertFailure(IllegalStateException::class.java).assertError(failure)
            verify(exactly = 0) { api.retryBuild(any(), any(), any(), any()) }
        }
    }
})
