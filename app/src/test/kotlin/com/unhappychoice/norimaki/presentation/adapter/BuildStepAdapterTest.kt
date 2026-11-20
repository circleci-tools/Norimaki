package com.unhappychoice.norimaki.presentation.adapter

import android.app.Activity
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.unhappychoice.circleci.v1.response.BuildStep
import com.google.gson.Gson
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.domain.model.BuildStepId
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BuildStepAdapterTest {
    @Test
    fun opensTheCurrentStepAfterRowsMove() {
        Robolectric.buildActivity(Activity::class.java).use { controller ->
            val activity = controller.setup().get()
            activity.setTheme(R.style.AppTheme)
            val adapter = BuildStepAdapter(activity)
            val rows = RecyclerView(activity).apply {
                layoutManager = LinearLayoutManager(activity)
                this.adapter = adapter
                itemAnimator = null
            }
            activity.setContentView(rows)
            val selection = adapter.onClickItem.test()
            val numbered = step("""[{"step":5}]""")
            val unnumbered = step("[{}]")
            val empty = step("[]")

            adapter.submitSteps(listOf(numbered, unnumbered, empty))
            layoutRows(rows)
            val holder = rows.findViewHolderForAdapterPosition(0) as BuildStepAdapter.ViewHolder
            val listener = shadowOf(holder.itemView).onClickListener
            adapter.submitSteps(listOf(empty, numbered, unnumbered))
            layoutRows(rows)

            assertSame(listener, shadowOf(holder.itemView).onClickListener)
            holder.itemView.performClick()
            requireNotNull(rows.findViewHolderForAdapterPosition(2)).itemView.performClick()
            requireNotNull(rows.findViewHolderForAdapterPosition(0)).itemView.performClick()
            adapter.onViewRecycled(holder)
            holder.itemView.performClick()
            selection.assertValues(BuildStepId(5), BuildStepId(null, position = 2))
        }
    }

    private fun step(actions: String): BuildStep =
        Gson().fromJson("""{"name":"Run tests","actions":$actions}""", BuildStep::class.java)

    private fun layoutRows(rows: RecyclerView) {
        val size = View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY)
        rows.measure(size, size)
        rows.layout(0, 0, 600, 600)
    }
}
