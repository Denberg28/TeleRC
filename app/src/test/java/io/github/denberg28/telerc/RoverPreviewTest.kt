package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test

class RoverPreviewTest {
    @Test fun inPlaceRotationKeepsPreviewAtHomeAndNeverAddsTelemetry() {
        val home = TrackPoint(14.5995, 120.9842, 1000L)
        val origin = RoverPose()
        var pose = origin
        repeat(20) { pose = roverStep(pose, 1f, 0f, .05f) }
        val preview = previewLocation(home, origin, pose)
        assertEquals(home.latitude, preview.latitude, 0.000001)
        assertEquals(home.longitude, preview.longitude, 0.000001)
        assertTrue(preview.headingDegrees > 10.0)
        val session = RouteSession()
        assertTrue(session.rover.isEmpty())
    }
}
