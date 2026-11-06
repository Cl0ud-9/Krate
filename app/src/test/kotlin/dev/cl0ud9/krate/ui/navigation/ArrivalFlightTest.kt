package dev.cl0ud9.krate.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrivalFlightTest {
    @Test
    fun `the icon flies while the page arrives and stops once it has`() {
        val flight = ArrivalFlight()

        assertTrue(flight.linked(entering = true, touched = false))
        assertFalse(flight.linked(entering = false, touched = false))
    }

    @Test
    fun `a touch ends the flight, and letting go doesn't start it again`() {
        val flight = ArrivalFlight()
        IconFlight.arrivals = 0

        assertTrue(flight.linked(entering = true, touched = false))
        assertFalse(flight.linked(entering = true, touched = true))
        // a swipe down at the top leaves the page unscrolled, so after the finger lifts it reads as untouched
        assertFalse(flight.linked(entering = true, touched = false))
        assertEquals(1, IconFlight.arrivals)
    }

    @Test
    fun `a page touched before its icon set off never flies it`() {
        val flight = ArrivalFlight()

        assertFalse(flight.linked(entering = true, touched = true))
        assertFalse(flight.linked(entering = true, touched = false))
    }
}
