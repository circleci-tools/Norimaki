package com.unhappychoice.norimaki.presentation.adapter

import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import com.github.unhappychoice.circleci.v1.response.Build
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.unhappychoice.norimaki.domain.model.BuildHistory
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import java.util.Date

class BuildListUpdatesTest : DescribeSpec({
    val gson = GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()

    fun build(number: Int, status: String = "success"): Build =
        gson.fromJson(
            """{"build_num":$number,"username":"circleci-tools","reponame":"Norimaki","status":"$status"}""",
            Build::class.java
        ).copy(queuedAt = Date(number * 1000L))

    fun dispatchUpdates(old: List<Build>, new: List<Build>): ListUpdateCallback {
        val updates = mockk<ListUpdateCallback>(relaxed = true)
        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = new.size
            override fun areItemsTheSame(oldPosition: Int, newPosition: Int) =
                BuildDiff.areItemsTheSame(old[oldPosition], new[newPosition])

            override fun areContentsTheSame(oldPosition: Int, newPosition: Int) =
                BuildDiff.areContentsTheSame(old[oldPosition], new[newPosition])
        }).dispatchUpdatesTo(updates)
        return updates
    }

    describe("Build list page updates") {
        val history = listOf(60 downTo 41, 40 downTo 21, 20 downTo 1)
            .fold(BuildHistory()) { history, numbers ->
                checkNotNull(history.merge(numbers.map { build(it) }))
            }

        it("skips repeated first pages after older pages have loaded") {
            history.builds.size shouldBe 60
            repeat(3) {
                history.merge((60 downTo 41).map { build(it) }) shouldBe null
            }
        }

        it("updates only the row whose build changed") {
            val new = checkNotNull(history.merge(listOf(build(59, "failed"), build(60))))
            new.builds[1].status shouldBe "failed"
            val updates = dispatchUpdates(history.builds, new.builds)

            verify(exactly = 1) { updates.onChanged(1, 1, null) }
            verify(exactly = 1) { updates.onChanged(any(), any(), anyNullable()) }
            verify(exactly = 0) {
                updates.onInserted(any(), any())
                updates.onRemoved(any(), any())
                updates.onMoved(any(), any())
            }
        }

        it("inserts a new build in descending queue order") {
            val new = checkNotNull(history.merge(listOf(build(60), build(61))))
            new.builds.map { it.buildNum } shouldBe (61 downTo 1).toList()
            val updates = dispatchUpdates(history.builds, new.builds)

            verify(exactly = 1) { updates.onInserted(0, 1) }
            verify(exactly = 0) { updates.onChanged(any(), any(), anyNullable()) }
        }
    }
})
