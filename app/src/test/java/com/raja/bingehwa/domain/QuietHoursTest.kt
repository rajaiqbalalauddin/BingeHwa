package com.raja.bingehwa.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietHoursTest {
    @Test fun nightIsQuiet() {
        assertTrue(QuietHours.isQuiet(23))
        assertTrue(QuietHours.isQuiet(0))
        assertTrue(QuietHours.isQuiet(6))
    }

    @Test fun daytimeIsNot() {
        assertFalse(QuietHours.isQuiet(7))
        assertFalse(QuietHours.isQuiet(12))
        assertFalse(QuietHours.isQuiet(22))
    }
}
