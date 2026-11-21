package com.unhappychoice.norimaki.presentation.view

import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import com.unhappychoice.norimaki.R
import com.unhappychoice.norimaki.infrastructure.testresults.TestFailure
import com.unhappychoice.norimaki.presentation.adapter.TestFailureAdapter
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TestFailureRowTest {
    @Test
    fun expandsLiteralFailureMessagesAndRestoresExpansionAfterRecycling() {
        val parent = FrameLayout(RuntimeEnvironment.getApplication())
        val message = "Expected <value>\nActual <null>"
        val adapter = TestFailureAdapter(
            listOf(TestFailure("asserts", "Example", message), TestFailure("throws", null, null))
        )
        val holder = adapter.onCreateViewHolder(parent, 0)
        val details = holder.itemView.findViewById<TextView>(R.id.message)
        val toggle = holder.itemView.findViewById<Button>(R.id.toggleMessage)

        adapter.onBindViewHolder(holder, 0)
        assertEquals("Example", holder.itemView.findViewById<TextView>(R.id.testClass).text.toString())
        assertEquals("asserts", holder.itemView.findViewById<TextView>(R.id.testName).text.toString())
        assertEquals(View.GONE, details.visibility)

        toggle.performClick()
        assertEquals(View.VISIBLE, details.visibility)
        assertEquals(message, details.text.toString())

        adapter.onBindViewHolder(holder, 1)
        assertEquals(View.GONE, details.visibility)
        assertEquals(View.GONE, holder.itemView.findViewById<TextView>(R.id.testClass).visibility)

        adapter.onBindViewHolder(holder, 0)
        assertEquals(View.VISIBLE, details.visibility)
        toggle.performClick()
        assertEquals(View.GONE, details.visibility)
    }
}
