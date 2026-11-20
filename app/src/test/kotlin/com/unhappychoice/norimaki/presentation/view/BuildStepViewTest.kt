package com.unhappychoice.norimaki.presentation.view

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.TextView
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.extension.Variable
import com.unhappychoice.norimaki.extension.removeAnsiEscapeCode
import com.unhappychoice.norimaki.extension.replaceAnsiColorCodeToHtml
import com.unhappychoice.norimaki.presentation.presenter.BuildStepPresenter
import com.unhappychoice.norimaki.presentation.presenter.completedActionLogs
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.TestScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.TimeUnit.MILLISECONDS

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class BuildStepViewTest {
    private val scheduler = TestScheduler()
    private val log = Variable("")
    private val loadFailed = Variable(false)
    private val searchAvailable = Variable(false)

    @Before
    fun useTestScheduler() {
        RxJavaPlugins.setIoSchedulerHandler { scheduler }
        RxJavaPlugins.setComputationSchedulerHandler { scheduler }
    }

    @After
    fun restoreSchedulers() {
        RxJavaPlugins.reset()
    }

    @Test
    fun searchesRetainedOutputWhileShowingTheLoadError() {
        withView { activity, view ->
            val output = view.findViewById<TextView>(R.id.logText)
            val error = view.findViewById<TextView>(R.id.logLoadError)

            log.value = "<b>Build output</b>"
            renderUpdates()

            assertEquals("Build output", output.text.toString())
            assertEquals(View.GONE, error.visibility)

            loadFailed.value = true
            searchAvailable.value = true
            renderUpdates()

            assertEquals(View.VISIBLE, error.visibility)
            assertEquals(activity.getString(R.string.build_log_load_error), error.text.toString())
            assertEquals("Build output", output.text.toString())

            val find = view.findViewById<View>(R.id.logFind)
            assertEquals(View.VISIBLE, find.visibility)
            find.performClick()
            view.findViewById<EditText>(R.id.logQuery).setText("output")
            finishSearch()

            assertEquals(6 until 12, highlightedRange(output))
            assertEquals(View.VISIBLE, error.visibility)
        }
    }

    @Test
    fun searchesRenderedOutputAndWrapsThroughLiteralMatches() {
        withView { activity, view ->
            val find = view.findViewById<View>(R.id.logFind)
            val query = view.findViewById<EditText>(R.id.logQuery)
            val count = view.findViewById<TextView>(R.id.logMatchCount)
            val output = view.findViewById<TextView>(R.id.logText)
            val previous = view.findViewById<View>(R.id.logPrevious)
            val next = view.findViewById<View>(R.id.logNext)

            log.value = "😀 \u001b[31m[ERR]\u001b[0m <tag>\n\u001b[1;31m[err]".removeAnsiEscapeCode()
                .replaceAnsiColorCodeToHtml()
            renderUpdates()
            assertEquals(View.GONE, find.visibility)

            searchAvailable.value = true
            renderUpdates()
            assertEquals(View.VISIBLE, find.visibility)
            find.performClick()
            query.setText("[err]")
            assertEquals(activity.getString(R.string.build_log_searching), count.text.toString())
            finishSearch()
            assertEquals("😀 [ERR] <tag>\n[err]", output.text.toString())
            assertEquals(activity.getString(R.string.build_log_match_count, 1, 2), count.text.toString())
            assertEquals(3 until 8, highlightedRange(output))

            previous.performClick()
            assertEquals(activity.getString(R.string.build_log_match_count, 2, 2), count.text.toString())
            assertEquals(15 until 20, highlightedRange(output))
            next.performClick()
            assertEquals(3 until 8, highlightedRange(output))

            query.setText("<tag>")
            query.setText("missing")
            finishSearch()
            assertEquals(activity.getString(R.string.build_log_no_matches), count.text.toString())
            assertEquals(null, highlightedRange(output))
            assertEquals(false, next.isEnabled)

            query.setText("[err]")
            view.findViewById<View>(R.id.logCloseSearch).performClick()
            finishSearch()
            assertEquals(View.VISIBLE, find.visibility)
            assertEquals("", query.text.toString())
            assertEquals(null, highlightedRange(output))
        }
    }

    @Test
    fun ignoresHiddenQueriesAndDropsResultsQueuedBeforeTheQueryIsCleared() {
        withView { activity, view ->
            val query = view.findViewById<EditText>(R.id.logQuery)
            val count = view.findViewById<TextView>(R.id.logMatchCount)
            val output = view.findViewById<TextView>(R.id.logText)
            log.value = "Échec"
            searchAvailable.value = true
            renderUpdates()

            query.setText("échec")
            finishSearch()
            assertEquals("", count.text.toString())
            assertEquals(null, highlightedRange(output))

            view.findViewById<View>(R.id.logFind).performClick()
            finishSearch()
            assertEquals(activity.getString(R.string.build_log_match_count, 1, 1), count.text.toString())
            assertEquals(0 until 5, highlightedRange(output))

            searchAvailable.value = true
            renderUpdates()
            assertEquals(View.GONE, view.findViewById<View>(R.id.logFind).visibility)
            assertEquals(0 until 5, highlightedRange(output))

            query.setText("chec")
            scheduler.advanceTimeBy(150, MILLISECONDS)
            query.text.clear()
            renderUpdates()
            assertEquals("", count.text.toString())
            assertEquals(null, highlightedRange(output))
        }
    }

    @Test
    fun findsHyperlinkLabelsAndIgnoresSplitOsc8TargetsAndParameters() {
        withView { activity, view ->
            val query = view.findViewById<EditText>(R.id.logQuery)
            val output = view.findViewById<TextView>(R.id.logText)
            val count = view.findViewById<TextView>(R.id.logMatchCount)
            val links = listOf("\u001B]" to "\u0007", "\u001B]" to "\u001B\\", "\u009D" to "\u009C")
            val chunks = links.flatMapIndexed { index, (osc, terminator) ->
                listOf(
                    (if (index == 0) "" else "\n") + "${osc}8;id=",
                    "hidden-id;https://example.test/hidden-target${terminator.dropLast(1)}",
                    "${terminator.last()}\u001B[31mReport & details\u001B[0m${osc}8;;${terminator}"
                )
            }
            log.value = completedActionLogs(listOf("action")) { Observable.fromIterable(chunks) }
                .toList().blockingGet().joinToString("") { it.replaceAnsiColorCodeToHtml() }
            searchAvailable.value = true
            renderUpdates()

            assertEquals(List(links.size) { "Report & details" }.joinToString("\n"), output.text.toString())
            view.findViewById<View>(R.id.logFind).performClick()
            query.setText("report & details")
            finishSearch()
            assertEquals(activity.getString(R.string.build_log_match_count, 1, links.size), count.text.toString())
            assertEquals(0 until "Report & details".length, highlightedRange(output))

            listOf("hidden-target", "hidden-id").forEach {
                query.setText(it)
                finishSearch()
                assertEquals(activity.getString(R.string.build_log_no_matches), count.text.toString())
                assertEquals(null, highlightedRange(output))
            }
        }
    }

    @Test
    fun refreshesAnOpenSearchAndClearsItWhenSearchBecomesUnavailable() {
        withView { activity, view ->
            val find = view.findViewById<View>(R.id.logFind)
            val query = view.findViewById<EditText>(R.id.logQuery)
            val output = view.findViewById<TextView>(R.id.logText)
            val count = view.findViewById<TextView>(R.id.logMatchCount)
            log.value = "needle needle"
            searchAvailable.value = true
            renderUpdates()

            find.performClick()
            query.setText("needle")
            finishSearch()
            assertEquals(0 until 6, highlightedRange(output))

            log.value = "new needle"
            scheduler.triggerActions()
            view.findViewById<View>(R.id.logNext).performClick()
            assertEquals(7 until 13, highlightedRange(output))
            renderUpdates()
            finishSearch()
            assertEquals(4 until 10, highlightedRange(output))
            assertEquals(activity.getString(R.string.build_log_match_count, 1, 1), count.text.toString())

            searchAvailable.value = false
            renderUpdates()
            assertEquals(View.GONE, view.findViewById<View>(R.id.logSearchPanel).visibility)
            assertEquals(View.GONE, find.visibility)
            assertEquals("", query.text.toString())
            assertEquals(null, highlightedRange(output))
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun bringsAMatchBelowAndToTheRightOfTheViewportIntoView() {
        withView { _, view ->
            log.value = "needle<br>" + "line<br>".repeat(80) + "X".repeat(200) + "needle"
            searchAvailable.value = true
            renderUpdates()

            view.findViewById<View>(R.id.logFind).performClick()
            view.findViewById<EditText>(R.id.logQuery).setText("needle")
            val width = View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY)
            val height = View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY)
            view.measure(width, height)
            view.layout(0, 0, 400, 500)
            finishSearch()

            val output = view.findViewById<TextView>(R.id.logText)
            val horizontal = output.parent as HorizontalScrollView
            val vertical = horizontal.parent as ScrollView
            view.findViewById<View>(R.id.logNext).performClick()
            renderUpdates()

            val match = checkNotNull(highlightedRange(output))
            assertTrue(output.layout.getPrimaryHorizontal(match.first) > horizontal.width)
            assertTrue(horizontal.scrollX > 0)
            assertTrue(vertical.scrollY > 0)
        }
    }

    private fun highlightedRange(output: TextView): IntRange? {
        val text = output.text as Spanned
        val span = text.getSpans(0, text.length, BackgroundColorSpan::class.java).singleOrNull() ?: return null
        return text.getSpanStart(span) until text.getSpanEnd(span)
    }

    private fun withView(test: (Activity, BuildStepView) -> Unit) {
        val presenter = mockk<BuildStepPresenter>(relaxUnitFun = true) {
            every { logString } returns log
            every { logLoadFailed } returns loadFailed
            every { logSearchAvailable } returns searchAvailable
        }
        Robolectric.buildActivity(Activity::class.java).use { controller ->
            val activity = controller.setup().get()
            val view = activity.layoutInflater.inflate(R.layout.build_step_view, null) as BuildStepView
            view.di = DI { bind<BuildStepPresenter>() with singleton { presenter } }
            activity.setContentView(view)
            test(activity, view)
        }
    }

    private fun finishSearch() {
        scheduler.advanceTimeBy(150, MILLISECONDS)
        renderUpdates()
    }

    private fun renderUpdates() {
        scheduler.triggerActions()
        shadowOf(Looper.getMainLooper()).idle()
    }
}
