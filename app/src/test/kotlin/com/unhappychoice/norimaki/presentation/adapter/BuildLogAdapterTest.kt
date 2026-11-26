package com.unhappychoice.norimaki.presentation.adapter

import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.FrameLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.RecyclerView
import com.unhappychoice.norimaki.presentation.log.BuildLog
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BuildLogAdapterTest {
    @Test
    fun appendsColoredOutputAndBindsNewLinesWithoutDuplicatingText() {
        val log = BuildLog()
        val adapter = BuildLogAdapter(log)
        val source = PublishSubject.create<String>()
        val payloads = mutableListOf<Any>()
        val notifications = mutableListOf<String>()
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
                notifications += "changed:$positionStart:$itemCount"
                payloads += requireNotNull(payload)
            }

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                notifications += "inserted:$positionStart:$itemCount"
            }
        })
        val updates = log.changes.subscribe(adapter::update)
        val rendering = log.render(source, Schedulers.trampoline())
        try {
            source.onNext("prefix ")
            assertEquals(1, adapter.itemCount)
            val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), androidx.appcompat.R.style.Theme_AppCompat)
            val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
            adapter.onBindViewHolder(holder, 0)
            assertEquals("prefix ", holder.textView.text.toString())
            assertTrue(holder.textView.isTextSelectable)

            source.onNext("\u001B[31mred\u001B[0m\nnext")
            assertEquals(listOf("inserted:0:1", "changed:0:1", "inserted:1:1"), notifications)
            assertEquals(2, adapter.itemCount)
            adapter.onBindViewHolder(holder, 0, payloads)
            adapter.onBindViewHolder(holder, 0, payloads)
            val styled = holder.textView.text as Spanned
            assertEquals("prefix red", styled.toString())
            val color = styled.getSpans(0, styled.length, ForegroundColorSpan::class.java).single()
            assertEquals(0xFFF00000.toInt(), color.foregroundColor)
            assertEquals(7, styled.getSpanStart(color))
            assertEquals(10, styled.getSpanEnd(color))

            adapter.onBindViewHolder(holder, 1, mutableListOf())
            assertEquals("next", holder.textView.text.toString())
            adapter.onBindViewHolder(holder, 0, mutableListOf(Any()))
            assertEquals("prefix red", holder.textView.text.toString())
        } finally {
            rendering.dispose()
            updates.dispose()
        }
    }
}
