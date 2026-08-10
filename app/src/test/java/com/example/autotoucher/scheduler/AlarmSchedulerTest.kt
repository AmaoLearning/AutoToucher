package com.example.autotoucher.scheduler

import java.util.Calendar
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AlarmSchedulerTest {

    private lateinit var originalTimeZone: TimeZone

    @Before
    fun useUtc() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun `future time schedules later on the same day`() {
        val now = utcMillis(2026, Calendar.AUGUST, 10, 8, 30)

        val actual = AlarmScheduler.calcNextTriggerMillis(9, 15, now)

        assertEquals(utcMillis(2026, Calendar.AUGUST, 10, 9, 15), actual)
    }

    @Test
    fun `elapsed time schedules on the next day`() {
        val now = utcMillis(2026, Calendar.AUGUST, 10, 9, 15)

        val actual = AlarmScheduler.calcNextTriggerMillis(9, 15, now)

        assertEquals(utcMillis(2026, Calendar.AUGUST, 11, 9, 15), actual)
    }

    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis
}
