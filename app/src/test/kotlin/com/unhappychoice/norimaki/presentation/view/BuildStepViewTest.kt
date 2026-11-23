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
import com.unhappychoice.norimaki.infrastructure.output.RawOutputChunk
import com.unhappychoice.norimaki.presentation.presenter.ActionSnapshot
import com.unhappychoice.norimaki.presentation.presenter.BuildStepPresenter
import com.unhappychoice.norimaki.presentation.presenter.completedActionLogs
import com.unhappychoice.norimaki.presentation.presenter.pollActionOutputs
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.Observable
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.TestScheduler
import io.reactivex.subjects.PublishSubject
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
import java.util.concurrent.TimeUnit.SECONDS

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class BuildStepViewTest {
    private val scheduler = TestScheduler()
    private val log = Variable("")
    private val loadFailed = Variable(false)
    private val searchAvailable = Variable(false)
    private val canRetry = Variable(false)

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
            val retry = view.findViewById<View>(R.id.retryStepLoad)
            val error = view.findViewById<TextView>(R.id.logLoadError)

            log.value = "<b>Build output</b>"
            renderUpdates()

            assertEquals("Build output", output.text.toString())
            assertEquals(View.GONE, error.visibility)
            assertEquals(View.GONE, retry.visibility)

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
            assertEquals(View.GONE, retry.visibility)

            canRetry.value = true
            renderUpdates()
            assertEquals(View.VISIBLE, retry.visibility)
            retry.performClick()
            verify(exactly = 1) { view.presenter.retryStepLoad() }

            canRetry.value = false
            loadFailed.value = false
            renderUpdates()
            assertEquals(View.GONE, retry.visibility)
            assertEquals(View.GONE, error.visibility)
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
    fun searchesAndWrapsThroughNearlyOneMebibyteOfMatches() {
        withView { activity, view ->
            val count = view.findViewById<TextView>(R.id.logMatchCount)
            val output = view.findViewById<TextView>(R.id.logText)
            val previous = view.findViewById<View>(R.id.logPrevious)
            val next = view.findViewById<View>(R.id.logNext)
            val matchCount = 1024 * 1024 - 1
            log.value = "x".repeat(matchCount)
            searchAvailable.value = true
            renderUpdates()

            view.findViewById<View>(R.id.logFind).performClick()
            view.findViewById<EditText>(R.id.logQuery).setText("X")
            finishSearch()

            assertEquals(activity.getString(R.string.build_log_match_count, 1, matchCount), count.text.toString())
            assertEquals(0 until 1, highlightedRange(output))
            previous.performClick()
            assertEquals(
                activity.getString(R.string.build_log_match_count, matchCount, matchCount),
                count.text.toString()
            )
            assertEquals((matchCount - 1) until matchCount, highlightedRange(output))
            next.performClick()
            assertEquals(activity.getString(R.string.build_log_match_count, 1, matchCount), count.text.toString())
            assertEquals(0 until 1, highlightedRange(output))
            next.performClick()
            assertEquals(activity.getString(R.string.build_log_match_count, 2, matchCount), count.text.toString())
            assertEquals(1 until 2, highlightedRange(output))
        }
    }

    @Test
    fun resumesSearchingWhenTheSameViewIsReattached() {
        withView { activity, view ->
            val query = view.findViewById<EditText>(R.id.logQuery)
            val count = view.findViewById<TextView>(R.id.logMatchCount)
            val output = view.findViewById<TextView>(R.id.logText)
            log.value = "old output"
            searchAvailable.value = true
            renderUpdates()

            view.findViewById<View>(R.id.logFind).performClick()
            query.setText("output")
            finishSearch()
            assertEquals(4 until 10, highlightedRange(output))

            query.setText("old")
            activity.setContentView(View(activity))
            log.value = "new output new"
            finishSearch()
            assertEquals(null, highlightedRange(output))

            activity.setContentView(view)
            renderUpdates()
            finishSearch()
            assertEquals(activity.getString(R.string.build_log_no_matches), count.text.toString())

            query.setText("new")
            finishSearch()
            assertEquals(activity.getString(R.string.build_log_match_count, 1, 2), count.text.toString())
            assertEquals(0 until 3, highlightedRange(output))
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
        assertSearchOmitsHyperlinkControls { chunks ->
            completedActionLogs(listOf("action")) { Observable.fromIterable(chunks) }
                .toList().blockingGet()
        }
    }

    @Test
    fun searchesLiveLogAfterTheFinalReadCompletesASplitHyperlink() {
        assertSearchOmitsHyperlinkControls { chunks ->
            val finished = PublishSubject.create<ActionSnapshot>()
            val parts = chunks.map { it.toByteArray() }
            val partsByOffset = parts.runningFold(0L) { offset, part -> offset + part.size }
                .zip(parts).toMap()
            val output = pollActionOutputs(mapOf("action" to false), finished, { _, offset ->
                Observable.just(RawOutputChunk(partsByOffset.getValue(offset)))
            }, scheduler, fetchFinalOutput = { _, offset ->
                assertEquals(parts.dropLast(1).sumOf { it.size.toLong() }, offset)
                Observable.just(RawOutputChunk(parts.last()))
            }).test()

            scheduler.advanceTimeBy((parts.size - 2) * BuildStepPresenter.LOG_POLLING_SECONDS, SECONDS)
            output.assertNotComplete()
            finished.onNext(ActionSnapshot(emptyMap(), finished = true))
            output.assertComplete().assertNoErrors()
            output.values()
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
            log.value = "needle<br>" + "line<br>".repeat(80) + "X".repeat(200) + "needle" + "X".repeat(100)
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
            val matchLeft = output.left + output.totalPaddingLeft + output.layout.getPrimaryHorizontal(match.first)
            val matchRight = output.left + output.totalPaddingLeft + output.layout.getPrimaryHorizontal(match.last + 1)
            assertTrue(matchLeft > horizontal.width)
            assertTrue(matchLeft >= horizontal.scrollX + horizontal.paddingLeft)
            assertTrue(matchRight <= horizontal.scrollX + horizontal.width - horizontal.paddingRight)
            assertTrue(vertical.scrollY > 0)
        }
    }

    private fun assertSearchOmitsHyperlinkControls(readLog: (List<String>) -> List<String>) {
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
            log.value = readLog(chunks).joinToString("") { it.replaceAnsiColorCodeToHtml() }
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
            every { canRetryStepLoad } returns canRetry
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
