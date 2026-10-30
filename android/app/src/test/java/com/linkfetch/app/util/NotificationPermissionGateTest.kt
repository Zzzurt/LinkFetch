package com.linkfetch.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPermissionGateTest {

    @Test
    fun `does not request when already granted`() {
        val gate = NotificationPermissionGate()
        var requests = 0
        gate.updateGranted(true)
        gate.attach { requests++ }

        gate.ensure()

        assertEquals(0, requests)
    }

    @Test
    fun `requests only once before first save`() {
        val gate = NotificationPermissionGate()
        var requests = 0
        gate.updateGranted(false)
        gate.attach { requests++ }

        gate.ensure()
        gate.ensure()
        gate.ensure()

        assertEquals(1, requests)
    }

    @Test
    fun `does not consume the request slot before an activity attaches`() {
        val gate = NotificationPermissionGate()
        gate.updateGranted(false)

        gate.ensure()

        var requests = 0
        gate.attach { requests++ }
        gate.ensure()

        assertEquals(1, requests)
    }
}
