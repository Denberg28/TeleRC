package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test

class RouteSessionTest {
    @Test fun homeRemainsFirstPhoneFixAndRoverHasSeparateLine() {
        val route = RouteSession()
        val home = TrackPoint(14.5995, 120.9842, 1000)
        assertFalse(route.addPhone(home, 80f))
        assertTrue(route.addPhone(home, 8f))
        assertTrue(route.addPhone(TrackPoint(14.5996, 120.9843, 2000), 8f))
        assertEquals(home, route.home)
        assertTrue(route.addRover(TrackPoint(14.5997, 120.9843, 2000)))
        assertEquals(2, route.phone.size)
        assertEquals(1, route.rover.size)
        route.addCommand(ControlSample(2000, 1300, 1700))
        route.addEstimate(TrackPoint(14.5997, 120.9843, 2100, 90.0))
        val restored = RouteSession().apply { decode(route.encode()) }
        assertEquals(home, restored.home)
        assertEquals(route.phone, restored.phone)
        assertEquals(route.rover, restored.rover)
        assertEquals(route.commands, restored.commands)
        assertEquals(route.estimated, restored.estimated)
        route.reset()
        assertNull(route.home)
        assertTrue(route.estimated.isEmpty())
    }
    @Test fun stationaryTurnUpdatesHeadingWithoutAddingTravelAndPersists() {
        val session = RouteSession()
        val point = TrackPoint(14.5995, 120.9842, 1000, 0.0)
        assertTrue(session.addRover(point))
        assertTrue(session.addRover(point.copy(timeMs = 2000, headingDegrees = 90.0)))
        assertEquals(1, session.rover.size)
        assertEquals(90.0, session.rover.single().headingDegrees!!, 0.001)
        assertFalse(session.addRover(point.copy(timeMs = 3000, headingDegrees = 90.0)))
        assertEquals(90.0, RouteSession().apply { decode(session.encode()) }.rover.single().headingDegrees!!, 0.001)
        val old = "kind,time_ms,latitude,longitude,steer_us,drive_us\nrover,1000,14.5995,120.9842,,\n"
        assertNull(RouteSession().apply { decode(old) }.rover.single().headingDegrees)
    }
}
