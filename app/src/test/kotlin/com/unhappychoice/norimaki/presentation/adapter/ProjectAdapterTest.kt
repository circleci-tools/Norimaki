package com.unhappychoice.norimaki.presentation.adapter

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.unhappychoice.circleci.v1.response.Project
import com.unhappychoice.norimaki.R
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProjectAdapterTest {
    @Test
    fun displaysAndSelectsProjectsWithCollidingUrlHashes() {
        val projects = listOf(project("Aa"), project("BB"))
        assertEquals(projects[0].vcsUrl.hashCode(), projects[1].vcsUrl.hashCode())

        val context = RuntimeEnvironment.getApplication()
        val projectAdapter = ProjectAdapter(context).apply { this.projects.value = projects }
        val selections = projectAdapter.onClickItem.test()
        val picker = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = projectAdapter
        }
        val size = View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY)

        picker.measure(size, size)
        picker.layout(0, 0, 1000, 1000)

        assertFalse(projectAdapter.hasStableIds())
        assertEquals(2, picker.childCount)
        projects.forEachIndexed { position, project ->
            val row = checkNotNull(picker.findViewHolderForAdapterPosition(position)).itemView
            val name = row.findViewById<TextView>(R.id.projectNameView).text.toString()
            assertEquals("GitHub · example/${project.reponame}", name)
            assertTrue(row.performClick())
        }
        selections.assertValues(projects[0], projects[1])
    }

    private fun project(name: String): Project = mockk {
        every { vcsUrl } returns "https://github.com/example/$name"
        every { username } returns "example"
        every { reponame } returns name
    }
}
