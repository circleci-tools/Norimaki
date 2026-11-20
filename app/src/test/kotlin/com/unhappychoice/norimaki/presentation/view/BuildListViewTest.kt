package com.unhappychoice.norimaki.presentation.view

import android.app.Activity
import android.app.Application
import androidx.recyclerview.widget.GridLayoutManager
import com.unhappychoice.norimaki.databinding.BuildListViewBinding
import com.unhappychoice.norimaki.extension.Variable
import com.unhappychoice.norimaki.presentation.presenter.BuildListPresenter
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.Observable
import org.junit.Test
import org.junit.runner.RunWith
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class BuildListViewTest {
    @Test
    fun workflowListPaginatesOnlyForVerticalScrollNearTheEnd() {
        withBuildList(isWorkflow = true) { presenter, scroll ->
            scroll(0, 12, 3)
            scroll(12, 0, 8)
            verify(exactly = 0) { presenter.getBuilds() }

            scroll(0, 12, 8)
            verify(exactly = 1) { presenter.getBuilds() }
        }
    }

    @Test
    fun regularListPaginatesForZeroDeltaNotificationNearTheEnd() {
        withBuildList(isWorkflow = false) { presenter, scroll ->
            scroll(0, 0, 8)
            verify(exactly = 1) { presenter.getBuilds() }
        }
    }

    private fun withBuildList(
        isWorkflow: Boolean,
        test: (BuildListPresenter, (dx: Int, dy: Int, lastVisible: Int) -> Unit) -> Unit
    ) {
        val presenter = mockk<BuildListPresenter>(relaxed = true)
        every { presenter.isWorkflow } returns isWorkflow
        every { presenter.page } returns Variable(0)
        every { presenter.builds } returns Observable.empty()

        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val activity = controller.get()
            val binding = BuildListViewBinding.inflate(activity.layoutInflater)
            binding.root.di = DI { bind<BuildListPresenter>() with singleton { presenter } }
            activity.setContentView(binding.root)

            val recycler = binding.buildsView
            val layout = object : GridLayoutManager(activity, 1) {
                var lastVisible = 0
                override fun getItemCount() = 10
                override fun findLastVisibleItemPosition() = lastVisible
            }
            recycler.layoutManager = layout
            clearMocks(presenter, answers = false)

            test(presenter) { dx, dy, lastVisible ->
                layout.lastVisible = lastVisible
                ReflectionHelpers.callInstanceMethod<Unit>(
                    recycler, "dispatchOnScrolled",
                    ClassParameter.from(Integer.TYPE, dx),
                    ClassParameter.from(Integer.TYPE, dy)
                )
            }
        }
    }
}
