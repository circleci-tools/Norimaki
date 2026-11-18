package com.unhappychoice.norimaki.presentation.adapter

import android.app.Application
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.github.unhappychoice.circleci.v1.response.Build
import com.github.unhappychoice.circleci.v1.response.BuildStep
import com.github.unhappychoice.circleci.v1.response.Project
import com.google.gson.Gson
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.extension.Variable
import de.hdodenhof.circleimageview.CircleImageView
import io.reactivex.subjects.PublishSubject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
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
        assertReboundClicks(row, adapter, adapter.builds, adapter.onClickItem, builds) {
            adapter.ViewHolder(row)
        }
    }

    @Test
    fun buildStepClickFollowsTheBoundItem() {
        val adapter = BuildStepAdapter(context)
        val row = rowWithTextViews(R.id.buildTitle, R.id.time)
        row.addView(View(context).apply { id = R.id.statusIndicator })
        val steps = List(10) { BuildStep("Step $it", emptyList()) }
        assertReboundClicks(row, adapter, adapter.steps, adapter.onClickItem, steps) {
            adapter.ViewHolder(row)
        }
    }

    @Test
    fun projectClickFollowsTheBoundItem() {
        val adapter = ProjectAdapter(context)
        val row = rowWithTextViews(R.id.projectNameView)
        val projects = List(10) {
            gson.fromJson("""{"username":"circleci-tools","reponame":"project-$it"}""", Project::class.java)
        }
        assertReboundClicks(row, adapter, adapter.projects, adapter.onClickItem, projects) {
            adapter.ViewHolder(row)
        }
    }

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val gson = Gson()

    private fun rowWithTextViews(vararg textIds: Int) = ClickCountingRow(context).apply {
        textIds.forEach { textId -> addView(TextView(context).apply { id = textId }) }
    }

    private fun <T : Any, H : RecyclerView.ViewHolder> assertReboundClicks(
        row: ClickCountingRow,
        adapter: RecyclerView.Adapter<H>,
        items: Variable<List<T>>,
        clicks: PublishSubject<T>,
        snapshots: List<T>,
        createHolder: () -> H
    ) {
        val observer = clicks.test()
        val holder = createHolder()
        row.performClick()
        observer.assertNoValues()

        snapshots.forEach { snapshot ->
            items.value = listOf(snapshot)
            adapter.onBindViewHolder(holder, 0)
        }
        row.performClick()
        observer.assertValueCount(1)
        assertSame(snapshots.last(), observer.values().single())

        items.value = emptyList()
        adapter.onViewRecycled(holder)
        row.performClick()
        observer.assertValueCount(1)

        items.value = listOf(snapshots.first())
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
