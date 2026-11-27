package io.github.denberg28.telerc

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class StepPlanTest {
    @Test fun offsetMetersProjectsOneMeterAlongCardinalAndRotatedHeadings() {
        val origin = PlanOrigin(13.6218, 123.1948, headingDegrees = 0.0, fromPhoneGps = true)
        val north = StepGeometry.projectWaypoints(origin, listOf(StepCommand(StepDirection.FORWARD, 1.0))).single()
        assertTrue(north.latitude > origin.latitude)
        assertEquals(origin.longitude, north.longitude, 1e-9)
        // 1 meter of latitude is ~8.993e-6 degrees.
        assertEquals(1.0 / 111_195.0, north.latitude - origin.latitude, 1e-7)

        val eastOrigin = PlanOrigin(13.6218, 123.1948, headingDegrees = 90.0, fromPhoneGps = true)
        val east = StepGeometry.projectWaypoints(eastOrigin, listOf(StepCommand(StepDirection.FORWARD, 1.0))).single()
        assertEquals(eastOrigin.latitude, east.latitude, 1e-9)
        assertTrue(east.longitude > eastOrigin.longitude)

        // Box path: FWD 1m -> RIGHT 1m -> BACK 1m -> LEFT 1m returns to origin.
        val box = StepGeometry.projectWaypoints(
            PlanOrigin(13.6218, 123.1948, headingDegrees = 37.0, fromPhoneGps = true),
            listOf(
                StepCommand(StepDirection.FORWARD, 1.0),
                StepCommand(StepDirection.RIGHT, 1.0),
                StepCommand(StepDirection.BACK, 1.0),
                StepCommand(StepDirection.LEFT, 1.0)
            )
        )
        assertEquals(4, box.size)
        assertEquals(13.6218, box.last().latitude, 1e-7)
        assertEquals(123.1948, box.last().longitude, 1e-7)
    }

    @Test fun calibrationClampsUnsafePowerAndTimingInputs() {
        val bad = StepCalibration(metersPerSecond = 99.0, drivePercent = 100, steerPercent = 5, turn90Ms = 99_999L, pauseMs = 10L)
        assertEquals(2.0, bad.safeSpeedMps, 1e-9)
        assertEquals(70, bad.safeDrivePercent)
        assertEquals(25, bad.safeSteerPercent)
        assertEquals(3000L, bad.safeTurn90Ms)
        assertEquals(200L, bad.safePauseMs)
        assertTrue(bad.driveCommandUs(false) in 1501..1900)
        assertTrue(bad.driveCommandUs(true) in 1100..1499)
        assertEquals(3000 - bad.driveCommandUs(false), bad.driveCommandUs(true))
        assertEquals(3000 - bad.steerCommandUs(true), bad.steerCommandUs(false))
    }

    @Test fun stepRunnerEnforcesNeutralDwellAndReversesWithoutSpinning() {
        val cal = StepCalibration(metersPerSecond = 1.0, drivePercent = 40, steerPercent = 50, turn90Ms = 500L, pauseMs = 300L)
        val runner = StepRunner()
        runner.begin(listOf(StepCommand(StepDirection.FORWARD, 1.0), StepCommand(StepDirection.BACK, 1.0)), cal, 1000L)
        assertTrue(runner.active)
        // Initial 300 ms neutral dwell
        assertEquals(StepRunner.Output(1500, 1500), runner.poll(1000L))
        assertEquals(StepRunner.Output(1500, 1500), runner.poll(1299L))
        // Forward 1m (1000 ms)
        val fwd = runner.poll(1300L)
        assertEquals(1500, fwd.steer); assertTrue(fwd.drive > 1500)
        // Neutral dwell between FWD and BACK (300 ms)
        assertEquals(StepRunner.Output(1500, 1500), runner.poll(2300L))
        // Back 1m drives reverse directly (steer stays 1500)
        val rev = runner.poll(2600L)
        assertEquals(1500, rev.steer); assertTrue(rev.drive < 1500)
        // Final neutral dwell
        assertEquals(StepRunner.Output(1500, 1500), runner.poll(3600L))
        // Completed -> neutral and inactive
        assertEquals(StepRunner.Output(1500, 1500), runner.poll(3950L))
        assertFalse(runner.active)
    }

    @Test fun stepRunnerTurnsOnceForRepeatedLeftStepsAndStopsImmediatelyOnCancel() {
        val cal = StepCalibration(metersPerSecond = 1.0, drivePercent = 40, steerPercent = 50, turn90Ms = 500L, pauseMs = 300L)
        val runner = StepRunner()
        runner.begin(listOf(StepCommand(StepDirection.LEFT, 1.0), StepCommand(StepDirection.LEFT, 1.0)), cal, 0L)
        // 0..299 pre-neutral, 300..799 left turn, 800..1099 pause, 1100..2099 drive #1, 2100..2399 pause, 2400..3399 drive #2 (no 2nd turn!)
        val turn = runner.poll(350L)
        assertTrue(turn.steer < 1500); assertEquals(1500, turn.drive)
        assertEquals(StepRunner.Output(1500, 1500), runner.poll(850L))
        val firstDrive = runner.poll(1200L)
        assertEquals(1500, firstDrive.steer); assertTrue(firstDrive.drive > 1500)
        // Second LEFT step drives straight forward along already-turned heading (no double turn!)
        val secondDrive = runner.poll(2450L)
        assertEquals(1500, secondDrive.steer); assertTrue(secondDrive.drive > 1500)
        // Immediate cancel returns neutral immediately
        runner.cancel("Stopped by operator")
        assertFalse(runner.active)
        assertEquals(StepRunner.Output(1500, 1500), runner.poll(2500L))
    }

    @Test fun stepRunnerCancelsOnClockRollbackOrDeadline() {
        val cal = StepCalibration(metersPerSecond = 1.0, pauseMs = 300L)
        val runner = StepRunner()
        runner.begin(listOf(StepCommand(StepDirection.FORWARD, 1.0)), cal, 5000L)
        assertEquals(StepRunner.Output(1500, 1500), runner.poll(4999L))
        assertFalse(runner.active)

        runner.begin(listOf(StepCommand(StepDirection.FORWARD, 1.0)), cal, 5000L)
        assertEquals(StepRunner.Output(1500, 1500), runner.poll(5000L + StepRunner.MAX_TOTAL_DURATION_MS))
        assertFalse(runner.active)
    }

    @Test fun goStepGateRequiresArmedPermittedForegroundAndBlocksDuringUpload() {
        assertTrue(GoStepGate.ready(true, true, true, true, true, false, true, false))
        assertFalse(GoStepGate.ready(false, true, true, true, true, false, true, false))
        assertFalse(GoStepGate.ready(true, false, true, true, true, false, true, false))
        assertFalse(GoStepGate.ready(true, true, false, true, true, false, true, false))
        assertFalse(GoStepGate.ready(true, true, true, false, true, false, true, false))
        assertFalse(GoStepGate.ready(true, true, true, true, false, false, true, false))
        assertFalse(GoStepGate.ready(true, true, true, true, true, true, true, false))
        assertFalse(GoStepGate.ready(true, true, true, true, true, false, false, false))
        assertFalse(GoStepGate.ready(true, true, true, true, true, false, true, true))
    }

    @Test fun compassLabelFormatsCardinalHeadings() {
        assertEquals("0° N", StepGeometry.compassLabel(0.0))
        assertEquals("90° E", StepGeometry.compassLabel(90.0))
        assertEquals("180° S", StepGeometry.compassLabel(180.0))
        assertEquals("270° W", StepGeometry.compassLabel(270.0))
        assertEquals("0° N", StepGeometry.compassLabel(359.8))
        assertTrue(abs(PlanOrigin(0.0, 0.0, -90.0, false).normalizedHeading - 270.0) < 1e-9)
    }
}
