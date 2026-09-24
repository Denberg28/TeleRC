package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test

class RoverKinematicsTest {
    @Test fun gameMovesBothDirectionsWithoutThrottle() {
        assertTrue(gameX(.5f, -1f, .05f) < .5f)
        assertTrue(gameX(.5f, 1f, .05f) > .5f)
        assertEquals(.5f, gameX(.5f, 0f, .05f), 0f)
    }
    @Test fun steeringAtRestDoesNotMove() {
        assertEquals(RoverPose(), roverStep(RoverPose(), 1f, 0f, .05f))
    }
    @Test fun driveAndSteeringTurnWhileMoving() {
        val forward = roverStep(RoverPose(), 1f, 1f, .05f)
        assertTrue(forward.heading > 0f)
        assertTrue(forward.x > .5f)
        assertTrue(forward.y < .76f)
        val reverse = roverStep(RoverPose(), 1f, -1f, .05f)
        assertTrue(reverse.heading < 0f)
        assertTrue(reverse.y > .76f)
    }
    @Test fun releasedDriveDecelerates() {
        val moving = RoverPose(speed = .5f)
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
}
