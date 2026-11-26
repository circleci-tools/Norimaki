package com.unhappychoice.norimaki.presentation.view

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.extension.Variable
import com.unhappychoice.norimaki.extension.removeAnsiEscapeCode
import com.unhappychoice.norimaki.infrastructure.output.RawOutputChunk
import com.unhappychoice.norimaki.presentation.log.BuildLog
import com.unhappychoice.norimaki.presentation.presenter.ActionSnapshot
import com.unhappychoice.norimaki.presentation.presenter.BuildStepPresenter
import com.unhappychoice.norimaki.presentation.presenter.completedActionLogs
import com.unhappychoice.norimaki.presentation.presenter.pollActionOutputs
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.Observable
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import io.reactivex.schedulers.TestScheduler
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
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

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class BuildStepViewTest {
    private val scheduler = TestScheduler()
    private val log = BuildLog()
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
    fun rendersBuildStepOutputAndShowsTheErrorWithoutClearingExistingLogs() {
        val presenter = mockk<BuildStepPresenter>(relaxUnitFun = true) {
            every { this@mockk.log } returns this@BuildStepViewTest.log
            every { logSearchAvailable } returns searchAvailable
            every { logLoadFailed } returns loadFailed
            every { canRetryStepLoad } returns canRetry
        }

        Robolectric.buildActivity(Activity::class.java).use { controller ->
            val activity = controller.setup().get()
            val view = activity.layoutInflater.inflate(R.layout.build_step_view, null) as BuildStepView
            view.di = DI { bind<BuildStepPresenter>() with singleton { presenter } }
            activity.setContentView(view)
            val output = view.findViewById<RecyclerView>(R.id.logLines)
            val retry = view.findViewById<View>(R.id.retryStepLoad)
            val error = view.findViewById<TextView>(R.id.logLoadError)

            val chunks = PublishSubject.create<String>()
            val subscription = log.render(chunks, Schedulers.trampoline())
            try {
                chunks.onNext("Build out")
                assertEquals("Build out", renderOutput(output))
                chunks.onNext("put")
                assertEquals("Build output", renderOutput(output))
                assertEquals(View.GONE, error.visibility)
                assertEquals(View.GONE, retry.visibility)

                loadFailed.value = true

                assertEquals(View.VISIBLE, error.visibility)
                assertEquals(activity.getString(R.string.build_log_load_error), error.text.toString())
                assertEquals("Build output", renderOutput(output))
                assertEquals(View.GONE, retry.visibility)

                canRetry.value = true

                assertEquals(View.VISIBLE, retry.visibility)
                retry.performClick()
                verify(exactly = 1) { presenter.retryStepLoad() }

                canRetry.value = false
                loadFailed.value = false

                assertEquals(View.GONE, retry.visibility)
                assertEquals(View.GONE, error.visibility)

                log.clear()
                assertEquals(0, output.adapter?.itemCount)
            } finally {
                subscription.dispose()
            }
        }
    }

    @Test
    fun searchesRetainedOutputWhileShowingTheLoadError() {
        withView { activity, view ->
            val output = view.findViewById<RecyclerView>(R.id.logLines)
            val retry = view.findViewById<View>(R.id.retryStepLoad)
            val error = view.findViewById<TextView>(R.id.logLoadError)

            replaceLog("Build output")
            renderUpdates()

            assertEquals("Build output", renderOutput(output))
            assertEquals(View.GONE, error.visibility)
            assertEquals(View.GONE, retry.visibility)

            loadFailed.value = true
            searchAvailable.value = true
            renderUpdates()

            assertEquals(View.VISIBLE, error.visibility)
            assertEquals(activity.getString(R.string.build_log_load_error), error.text.toString())
            assertEquals("Build output", renderOutput(output))

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
            val output = view.findViewById<RecyclerView>(R.id.logLines)
            val previous = view.findViewById<View>(R.id.logPrevious)
            val next = view.findViewById<View>(R.id.logNext)

            replaceLog("😀 \u001b[31m[ER\u001b[0mR] <tag>\n\u001b[1;31m[err]".removeAnsiEscapeCode())
            renderUpdates()
            assertEquals(View.GONE, find.visibility)

            searchAvailable.value = true
            renderUpdates()
            assertEquals(View.VISIBLE, find.visibility)
            find.performClick()
            query.setText("[err]")
            assertEquals(activity.getString(R.string.build_log_searching), count.text.toString())
            finishSearch()
            assertEquals("😀 [ERR] <tag>", lineText(output, 0).toString())
            val secondLine = lineText(output, 1) as Spanned
            assertEquals("[err]", secondLine.toString())
            val color = secondLine.getSpans(0, secondLine.length, ForegroundColorSpan::class.java).single()
            assertEquals(0xFFF00000.toInt(), color.foregroundColor)
            assertEquals(activity.getString(R.string.build_log_match_count, 1, 2), count.text.toString())
            assertEquals(3 until 8, highlightedRange(output))

            previous.performClick()
            assertEquals(activity.getString(R.string.build_log_match_count, 2, 2), count.text.toString())
            assertEquals(0 until 5, highlightedRange(output, 1))
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
            val output = view.findViewById<RecyclerView>(R.id.logLines)
            val previous = view.findViewById<View>(R.id.logPrevious)
            val next = view.findViewById<View>(R.id.logNext)
            val lineLength = 1023
            val lineCount = 1024
            val matchCount = lineLength * lineCount
            replaceLog(List(lineCount) { "x".repeat(lineLength) }.joinToString("\n"))
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
            assertEquals((lineLength - 1) until lineLength, highlightedRange(output, lineCount - 1))
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
            val output = view.findViewById<RecyclerView>(R.id.logLines)
            replaceLog("old output")
            searchAvailable.value = true
            renderUpdates()

            view.findViewById<View>(R.id.logFind).performClick()
            query.setText("output")
            finishSearch()
            assertEquals(4 until 10, highlightedRange(output))

            query.setText("old")
            activity.setContentView(View(activity))
            replaceLog("new output new")
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
            val output = view.findViewById<RecyclerView>(R.id.logLines)
            replaceLog("Échec")
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
            val output = view.findViewById<RecyclerView>(R.id.logLines)
            val count = view.findViewById<TextView>(R.id.logMatchCount)
            replaceLog("needle needle")
            searchAvailable.value = true
            renderUpdates()

            find.performClick()
            query.setText("needle")
            finishSearch()
            assertEquals(0 until 6, highlightedRange(output))

            view.findViewById<View>(R.id.logNext).performClick()
            assertEquals(7 until 13, highlightedRange(output))
            replaceLog("new needle")
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
    fun bringsAMatchInAnOffscreenLogRowIntoView() {
        withView { _, view ->
            replaceLog("needle\n" + "line\n".repeat(80) + "X".repeat(200) + "needle")
            searchAvailable.value = true
            renderUpdates()

            view.findViewById<View>(R.id.logFind).performClick()
            view.findViewById<EditText>(R.id.logQuery).setText("needle")
            val width = View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY)
            val height = View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY)
            view.measure(width, height)
            view.layout(0, 0, 400, 500)
            finishSearch()

            val output = view.findViewById<RecyclerView>(R.id.logLines)
            assertEquals(null, output.findViewHolderForAdapterPosition(81))
            view.findViewById<View>(R.id.logNext).performClick()
            assertEquals(200 until 206, highlightedRange(output, 81))
            assertTrue(output.canScrollVertically(-1))
        }
    }

    private fun assertSearchOmitsHyperlinkControls(readLog: (List<String>) -> List<String>) {
        withView { activity, view ->
            val query = view.findViewById<EditText>(R.id.logQuery)
            val output = view.findViewById<RecyclerView>(R.id.logLines)
            val count = view.findViewById<TextView>(R.id.logMatchCount)
            val links = listOf("\u001B]" to "\u0007", "\u001B]" to "\u001B\\", "\u009D" to "\u009C")
            val chunks = links.flatMapIndexed { index, (osc, terminator) ->
                listOf(
                    (if (index == 0) "" else "\n") + "${osc}8;id=",
                    "hidden-id;https://example.test/hidden-target${terminator.dropLast(1)}",
                    "${terminator.last()}\u001B[31mReport & details\u001B[0m${osc}8;;${terminator}"
                )
            }
            renderLog(readLog(chunks))
            searchAvailable.value = true
            renderUpdates()

            assertEquals(List(links.size) { "Report & details" }, links.indices.map { lineText(output, it).toString() })
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

    private fun highlightedRange(output: RecyclerView, position: Int = 0): IntRange? {
        val text = lineText(output, position) as? Spanned ?: return null
        val span = text.getSpans(0, text.length, BackgroundColorSpan::class.java).singleOrNull() ?: return null
        return text.getSpanStart(span) until text.getSpanEnd(span)
    }

    private fun replaceLog(text: String) {
        log.clear()
        renderLog(listOf(text))
    }

    private fun renderLog(chunks: List<String>) {
        log.render(Observable.fromIterable(chunks), Schedulers.trampoline())
    }

    private fun renderOutput(output: RecyclerView): String = lineText(output, 0).toString()

    private fun lineText(output: RecyclerView, position: Int): CharSequence? {
        val width = View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY)
        val height = View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY)
        output.measure(width, height)
        output.layout(0, 0, 480, 320)
        return (output.findViewHolderForAdapterPosition(position)?.itemView as? TextView)?.text
    }

    private fun withView(test: (Activity, BuildStepView) -> Unit) {
        val presenter = mockk<BuildStepPresenter>(relaxUnitFun = true) {
            every { log } returns this@BuildStepViewTest.log
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
