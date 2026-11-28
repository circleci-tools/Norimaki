package com.unhappychoice.norimaki.presentation.view

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.infrastructure.flakytests.FlakyTestSummary
import com.unhappychoice.norimaki.presentation.presenter.FlakyTestsPresenter
import com.unhappychoice.norimaki.presentation.presenter.FlakyTestsState
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
class FlakyTestsViewTest {
    @Test
    fun showsDistinctEmptyAndFailedStatesAndListsAggregatedFlakyTests() {
        val presenter = mockk<FlakyTestsPresenter>(relaxed = true)
        Robolectric.buildActivity(Activity::class.java).use { controller ->
            val activity = controller.setup().get().apply { setTheme(R.style.AppTheme) }
            val view = LayoutInflater.from(activity).inflate(R.layout.flaky_tests_view, null) as FlakyTestsView
            view.di = DI { bind<FlakyTestsPresenter>() with singleton { presenter } }
            activity.setContentView(view)
            val status = view.findViewById<TextView>(R.id.status)
            val retry = view.findViewById<View>(R.id.retry)
            val tests = view.findViewById<RecyclerView>(R.id.tests)

            view.render(FlakyTestsState.Loaded(emptyList()))
            assertEquals(activity.getString(R.string.flaky_tests_empty), status.text.toString())
            assertEquals(View.GONE, retry.visibility)

            view.render(FlakyTestsState.Failed)
            assertEquals(activity.getString(R.string.flaky_tests_load_error), status.text.toString())
            assertEquals(View.VISIBLE, retry.visibility)
            retry.performClick()
            verify { presenter.load() }

            val test = FlakyTestSummary("testA", "ExampleTest", 3)
            view.render(FlakyTestsState.Loaded(listOf(test)))
            assertEquals(View.GONE, status.visibility)
            assertEquals(1, requireNotNull(tests.adapter).itemCount)
        }
    }
}
