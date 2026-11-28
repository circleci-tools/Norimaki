package com.unhappychoice.norimaki.presentation.adapter

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.github.unhappychoice.circleci.v1.response.Build
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.unhappychoice.norimaki.R
import de.hdodenhof.circleimageview.CircleImageView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BuildAdapterTest {
    @Test
    fun rendersBuildIndicatorsFromStatusLifecycleAndOutcome() {
        val context = RuntimeEnvironment.getApplication()
        val adapter = BuildAdapter(context)
        val parent = FrameLayout(context)
        val gson = GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()
        val cases = listOf(
            """{"status":"not_run","lifecycle":"not_run"}""" to Color.rgb(137, 137, 137),
            """{"status":"retried","lifecycle":"finished","outcome":"failed"}""" to Color.rgb(237, 92, 92),
            """{"status":"retried","lifecycle":"finished","outcome":"success"}""" to Color.rgb(66, 200, 138),
            """{"status":"retried","lifecycle":"finished"}""" to Color.rgb(137, 137, 137),
            """{"status":"running","lifecycle":"running"}""" to Color.rgb(92, 211, 228)
        )
        adapter.submitList(cases.map { (json, _) -> gson.fromJson(json, Build::class.java) })

        cases.forEachIndexed { position, (json, expectedColor) ->
            val holder = adapter.createViewHolder(parent, adapter.getItemViewType(position))
            adapter.bindViewHolder(holder, position)
            val indicator = holder.itemView.findViewById<View>(R.id.statusIndicator)
            assertEquals(json, expectedColor, (indicator.background as ColorDrawable).color)
        }
    }

    @Test
    fun bindsBothRepositoriesWithoutAdvertisingStableIds() {
        val context = RuntimeEnvironment.getApplication()
        val adapter = BuildAdapter(context)
        val parent = FrameLayout(context)
        val gson = GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()
        val repositories = listOf("Aa", "BB")
        adapter.submitList(repositories.map { repository ->
            gson.fromJson(
                """{"build_num":1,"username":"owner","reponame":"$repository"}""",
                Build::class.java
            )
        })

        assertFalse(adapter.hasStableIds())
        assertEquals(repositories.size, adapter.itemCount)
        repositories.forEachIndexed { position, repository ->
            assertEquals(RecyclerView.NO_ID, adapter.getItemId(position))
            val holder = adapter.createViewHolder(parent, adapter.getItemViewType(position))
            adapter.bindViewHolder(holder, position)
            assertEquals("owner / $repository", holder.itemView.findViewById<TextView>(R.id.repositoryTitle).text.toString())
            assertNotNull(holder.itemView.findViewById<CircleImageView>(R.id.author).drawable)
        }
    }
}
