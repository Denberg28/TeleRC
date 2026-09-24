package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test

class RoverKinematicsTest {
    @Test fun gameMovesBothDirectionsWithoutThrottle() {
        assertTrue(gameX(.5f, -1f, .05f) < .5f)
        assertTrue(gameX(.5f, 1f, .05f) > .5f)
        assertEquals(.5f, gameX(.5f, 0f, .05f), 0f)
    }
    @Test fun steeringAtRestPivotsWithoutTranslation() {
        val right = roverStep(RoverPose(), 1f, 0f, .05f)
        val left = roverStep(RoverPose(), -1f, 0f, .05f)
        assertTrue(right.heading > 0f && left.heading < 0f)
        assertEquals(.5f, right.x, .0001f)
        assertEquals(.76f, right.y, .0001f)
        assertTrue(right.leftTrack > 0f && right.rightTrack < 0f)
        assertTrue(left.leftTrack < 0f && left.rightTrack > 0f)
    }
    @Test fun driveAndSteeringTurnWhileMoving() {
        val forward = roverStep(RoverPose(), 1f, 1f, .05f)
        assertTrue(forward.heading > 0f)
        assertTrue(forward.x > .5f)
        assertTrue(forward.y < .76f)
        val reverse = roverStep(RoverPose(), 1f, -1f, .05f)
        assertTrue(reverse.heading > 0f)
        assertTrue(reverse.y > .76f)
    }
    @Test fun releasedDriveDecelerates() {
        val moving = RoverPose(speed = .35f, leftTrack = .5f, rightTrack = .5f)
        val next = roverStep(moving, 0f, 0f, .05f)
        assertTrue(next.speed in 0f..moving.speed)
        assertTrue(next.speed < .1f)
    }
    @Test fun leftAndRightTurnOppositeDirections() {
        val left = roverStep(RoverPose(), -1f, 1f, .05f)
        val right = roverStep(RoverPose(), 1f, 1f, .05f)
        assertTrue(left.x < .5f && left.heading < 0f)
        assertTrue(right.x > .5f && right.heading > 0f)
    }
    @Test fun pureDriveDoesNotRotateAndInputsStayWithinTrackLimits() {
        val straight = roverStep(RoverPose(), 0f, 1f, .05f)
        assertEquals(0f, straight.heading, 0f)
        assertEquals(straight.leftTrack, straight.rightTrack, 0f)
        val combined = roverStep(RoverPose(), 1f, 1f, .05f)
        assertTrue(combined.leftTrack <= 1f && combined.rightTrack >= -1f)
        assertTrue(combined.leftTrack > combined.rightTrack)
    }
    @Test fun pivotCanPassPreviousHeadingLimit() {
        var pose = RoverPose()
        repeat(25) { pose = roverStep(pose, 1f, 0f, .05f) }
        assertTrue(kotlin.math.abs(pose.heading) > 1.3f)
        assertEquals(.5f, pose.x, .0001f)
    }
    @Test fun releasingPivotBrakesBothTracks() {
        val turning = roverStep(RoverPose(), 1f, 0f, .05f)
        val released = roverStep(turning, 0f, 0f, .05f)
        assertTrue(kotlin.math.abs(released.leftTrack) < kotlin.math.abs(turning.leftTrack))
        assertTrue(kotlin.math.abs(released.rightTrack) < kotlin.math.abs(turning.rightTrack))
    }
}
