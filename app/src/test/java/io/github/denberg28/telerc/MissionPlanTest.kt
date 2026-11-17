package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test

class MissionPlanTest {
    @Test fun editReorderDeleteAndRoundTrip() {
        val plan = MissionPlan()
        val a = Waypoint(13.62, 123.19)
        val b = Waypoint(-13.61, -123.18)
        plan.add(a); plan.add(b); plan.move(1, -1)
        assertEquals(listOf(b, a), plan.waypoints)
        plan.edit(0, Waypoint(0.0, 0.0))
        assertEquals(plan.waypoints, MissionPlan.decode(plan.encode()).waypoints)
        plan.remove(0); assertEquals(listOf(a), plan.waypoints)
        plan.clear(); assertTrue(plan.waypoints.isEmpty())
        assertTrue(MissionPlan.decode(plan.encode()).waypoints.isEmpty())
    }
    @Test fun distanceUsesGeographicCoordinatesAndDoesNotIncludePhoneHome() {
        val plan = MissionPlan()
        plan.add(Waypoint(0.0, 0.0)); assertEquals(0.0, plan.distanceMeters, 0.001)
        plan.add(Waypoint(0.0, 1.0))
        assertEquals(111_195.0, plan.distanceMeters, 1.0)
        plan.add(Waypoint(0.0, 1.0))
        assertEquals(111_195.0, plan.distanceMeters, 1.0)
    }
    @Test fun rejectsInvalidCoordinatesAndOversizedPlans() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, 91.0, -91.0).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { Waypoint(invalid, 0.0) }
        }
        assertThrows(IllegalArgumentException::class.java) { Waypoint(0.0, 181.0) }
        val plan = MissionPlan()
        repeat(MissionPlan.MAX_WAYPOINTS) { plan.add(Waypoint(0.0, it.toDouble())) }
        assertEquals(plan.waypoints, MissionPlan.decode(plan.encode()).waypoints)
        assertThrows(IllegalArgumentException::class.java) { plan.add(Waypoint(1.0, 1.0)) }
    }
    @Test fun invalidImportCannotPartlyReplaceCurrentPlan() {
        val plan = MissionPlan().apply { add(Waypoint(13.0, 123.0)) }
        val old = plan.encode()
        listOf("", "bad header", "sequence,latitude,longitude\n1,13,123\n2,NaN,0",
            "sequence,latitude,longitude\n2,13,123", "sequence,latitude,longitude\n1,13,123,extra",
            "sequence,latitude,longitude\n1,13,123\n\n2,14,124", "x".repeat(MissionPlan.MAX_CSV_CHARS + 1)).forEach { csv ->
            assertThrows(IllegalArgumentException::class.java) { plan.replaceWith(MissionPlan.decode(csv)) }
            assertEquals(old, plan.encode())
        }
    }
    @Test fun savedCsvHandlesWindowsLinesAndBom() {
        val plan = MissionPlan.decode("\uFEFFsequence,latitude,longitude\r\n1,-90,-180\r\n2,90,180\r\n")
        assertEquals(2, plan.waypoints.size)
        assertTrue(plan.distanceMeters.isFinite())
    }
}
