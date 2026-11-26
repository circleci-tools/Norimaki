package com.unhappychoice.norimaki.extension

import com.unhappychoice.norimaki.domain.model.BuildStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BuildStatusColorTest {
    @Test
    fun mapsEachStatusToItsColor() {
        assertEquals(0xFF42C88A.toInt(), BuildStatus.SUCCESS.color())
        assertEquals(0xFF898989.toInt(), BuildStatus.CANCELED.color())
        assertEquals(0xFFED5C5C.toInt(), BuildStatus.FAILED.color())
        assertEquals(0xFF5CD3E4.toInt(), BuildStatus.ACTIVE.color())
    }
}
