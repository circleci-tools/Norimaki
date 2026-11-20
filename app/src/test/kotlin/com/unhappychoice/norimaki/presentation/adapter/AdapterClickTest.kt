package com.unhappychoice.norimaki.presentation.adapter

import android.app.Application
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.unhappychoice.circleci.v1.response.Build
import com.github.unhappychoice.circleci.v1.response.Project
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.unhappychoice.norimaki.R
import de.hdodenhof.circleimageview.CircleImageView
import io.reactivex.subjects.PublishSubject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [35])
class AdapterClickTest {
    @Test
    fun buildClickFollowsTheBoundItem() {
        val adapter = BuildAdapter(context)
        val row = rowWithTextViews(R.id.repositoryTitle, R.id.branchTitle, R.id.commitTitle, R.id.createdAt)
        row.addView(View(context).apply { id = R.id.statusIndicator })
        row.addView(CircleImageView(context).apply { id = R.id.author })
        val builds = List(10) { gson.fromJson("""{"build_num":$it,"subject":"Build $it"}""", Build::class.java) }
        assertReboundClicks(row, adapter, adapter::submitList, adapter.onClickItem, builds) {
            adapter.ViewHolder(row)
        }
    }

    @Test
    fun projectClickUsesTheCurrentAdapterPositionAcrossSessionChanges() {
        val adapter = ProjectAdapter(context)
        val observer = adapter.onClickItem.test()
        val projects = List(10) {
            Project(
                branches = emptyMap(),
                parallel = 1,
                reponame = "project-$it",
                username = "circleci-tools",
                vcsUrl = "https://github.com/circleci-tools/project-$it"
            )
        }
        adapter.projects.value = listOf(projects.first())
        val recycler = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
            val size = View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY)
            measure(size, size)
            layout(0, 0, 400, 400)
        }
        val holder = recycler.findViewHolderForAdapterPosition(0) as ProjectAdapter.ViewHolder
        val row = holder.itemView
        val listener = shadowOf(row).onClickListener

        projects.forEach { project ->
            adapter.projects.value = listOf(project)
            adapter.onBindViewHolder(holder, 0)
        }
        assertSame(listener, shadowOf(row).onClickListener)
        row.performClick()
        observer.assertValueCount(1)
        assertSame(projects.last(), observer.values().single())

        adapter.projects.value = emptyList()
        adapter.notifyDataSetChanged()
        adapter.projects.value = listOf(projects.first())
        adapter.notifyDataSetChanged()
        row.performClick()
        observer.assertValueCount(1)
    }

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val gson = GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()

    private fun rowWithTextViews(vararg textIds: Int) = ClickCountingRow(context).apply {
        textIds.forEach { textId -> addView(TextView(context).apply { id = textId }) }
    }

    private fun <T : Any, H : RecyclerView.ViewHolder> assertReboundClicks(
        row: ClickCountingRow,
        adapter: RecyclerView.Adapter<H>,
        submitItems: (List<T>) -> Unit,
        clicks: PublishSubject<T>,
        snapshots: List<T>,
        createHolder: () -> H
    ) {
        val observer = clicks.test()
        val holder = createHolder()
        row.performClick()
        observer.assertNoValues()

        submitItems(snapshots)
        snapshots.indices.forEach { position -> adapter.onBindViewHolder(holder, position) }
        row.performClick()
        observer.assertValueCount(1)
        assertSame(snapshots.last(), observer.values().single())

        adapter.onViewRecycled(holder)
        row.performClick()
        observer.assertValueCount(1)

        adapter.onBindViewHolder(holder, 0)
        row.performClick()
        observer.assertValueCount(2)
        assertSame(snapshots.first(), observer.values().last())
        assertEquals(1, row.listenerInstallations)
    }

    private class ClickCountingRow(context: Context) : FrameLayout(context) {
        var listenerInstallations = 0
            private set

        override fun setOnClickListener(listener: OnClickListener?) {
            super.setOnClickListener(listener)
            listenerInstallations++
        }
    }
}
