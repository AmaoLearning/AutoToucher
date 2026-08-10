package com.example.autotoucher.service

import org.junit.Assert.assertFalse
import org.junit.Test

class TaskExecutorServiceTest {

    @Test
    fun `preparing wake session clears stale readiness`() {
        TaskExecutorService.keyguardReady.value = true

        TaskExecutorService.prepareWakeSession()

        assertFalse(TaskExecutorService.keyguardReady.value)
    }
}
