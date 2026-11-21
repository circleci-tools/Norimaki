package com.unhappychoice.norimaki.presentation.view

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.widget.ListView
import android.widget.TextView
import com.github.unhappychoice.circleci.v1.response.Artifact
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.presentation.presenter.ArtifactListState
import com.unhappychoice.norimaki.presentation.presenter.ArtifactsPresenter
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArtifactsViewTest {
    @Test
    fun showsDistinctEmptyAndFailedStatesAndSelectsAnArtifactByPath() {
        val presenter = mockk<ArtifactsPresenter>(relaxed = true)
        Robolectric.buildActivity(Activity::class.java).use { controller ->
            val activity = controller.setup().get().apply { setTheme(R.style.AppTheme) }
            val view = LayoutInflater.from(activity).inflate(R.layout.artifacts_view, null) as ArtifactsView
            view.di = DI { bind<ArtifactsPresenter>() with singleton { presenter } }
            activity.setContentView(view)
            val status = view.findViewById<TextView>(R.id.artifact_status)
            val retry = view.findViewById<View>(R.id.retry_artifacts)
            val list = view.findViewById<ListView>(R.id.artifact_list)

            view.render(ArtifactListState.Loaded(emptyList()))
            assertEquals(activity.getString(R.string.artifacts_empty), status.text)
            assertEquals(View.GONE, retry.visibility)

            view.render(ArtifactListState.Failed)
            assertEquals(activity.getString(R.string.artifacts_load_failed), status.text)
            assertEquals(View.VISIBLE, retry.visibility)
            retry.performClick()
            verify { presenter.load() }

            val artifact = Artifact(0, "reports/check.txt", "check.txt", "https://dl.circleci.com/file")
            view.render(ArtifactListState.Loaded(listOf(artifact)))
            assertEquals(View.GONE, status.visibility)
            assertEquals("reports/check.txt", list.adapter.getItem(0))
            list.performItemClick(View(activity), 0, 0L)
            verify { presenter.chooseDestination(artifact) }
        }
    }
}
