package dev.rage.anticheat.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpeedTrackerTest {

    private static final double TOLERANCE = 0.01;
    private static final double MAX_EXCESS = 0.1;
    private static final double SPRINT_SPEED = 0.13; // movement_speed attribute while sprinting

    private static final double GROUND_FRICTION = MovePhysics.groundFriction(MovePhysics.DEFAULT_SLIPPERINESS);
    private static final double GROUND_ACCEL =
            MovePhysics.groundAcceleration(SPRINT_SPEED, MovePhysics.DEFAULT_SLIPPERINESS);

    @Test
    void groundAccelerationOnNormalBlocksEqualsMovementSpeed() {
        assertEquals(SPRINT_SPEED, GROUND_ACCEL, 1e-6);
    }

    @Test
    void vanillaSprintingOnGroundPasses() {
        SpeedTracker tracker = new SpeedTracker(10);
        double move = 0;
        for (int tick = 0; tick < 100; tick++) {
            move = move * GROUND_FRICTION + GROUND_ACCEL; // exactly what the client does
            tracker.observe(move, 1, GROUND_ACCEL, GROUND_FRICTION, false, TOLERANCE);
            assertTrue(tracker.lastMoveLegal(), "tick " + tick);
        }
        assertEquals(0.286, move, 0.001); // vanilla sprint speed, 5.7 blocks/s
        assertFalse(tracker.exceeded(MAX_EXCESS));
    }

    @Test
    void vanillaSprintJumpingPasses() {
        SpeedTracker tracker = new SpeedTracker(10);
        double move = 0;
        double friction = GROUND_FRICTION;
        for (int jump = 0; jump < 10; jump++) {
            // One ground tick with the jump boost, then 11 ticks in the air.
            move = move * friction + GROUND_ACCEL + MovePhysics.SPRINT_JUMP_BOOST;
            tracker.observe(move, 1, GROUND_ACCEL, GROUND_FRICTION, true, TOLERANCE);
            friction = GROUND_FRICTION;
            for (int air = 0; air < 11; air++) {
                double accel = MovePhysics.airAcceleration(true);
                move = move * friction + accel;
                tracker.observe(move, 1, accel, MovePhysics.AIR_FRICTION, false, TOLERANCE);
                friction = MovePhysics.AIR_FRICTION;
                assertTrue(tracker.lastMoveLegal());
            }
        }
        assertFalse(tracker.exceeded(MAX_EXCESS));
    }

    @Test
    void speedHackOnGroundIsFlaggedWithinAFewMoves() {
        SpeedTracker tracker = new SpeedTracker(10);
        int moves = 0;
        while (!tracker.exceeded(MAX_EXCESS)) {
            tracker.observe(0.36, 1, GROUND_ACCEL, GROUND_FRICTION, false, TOLERANCE); // 25% too fast
            moves++;
        }
        assertTrue(moves <= 3, "flagged after " + moves + " moves");
    }

    @Test
    void stayingJustInsideToleranceCannotBuildSpeed() {
        SpeedTracker tracker = new SpeedTracker(10);
        double move = 0;
        for (int tick = 0; tick < 200; tick++) {
            move = tracker.lastAllowed() + TOLERANCE; // previous allowed move plus the free tolerance
            tracker.observe(move, 1, MovePhysics.airAcceleration(true), MovePhysics.AIR_FRICTION, false, TOLERANCE);
        }
        // Without clamping, air friction 0.91 would let this reach (0.026 + 0.01) / 0.09 = 0.4.
        assertTrue(move < 0.32, "converged to " + move);
    }

    @Test
    void graceKeepsRealMomentumForThePrediction() {
        SpeedTracker tracker = new SpeedTracker(10);
        tracker.carry(1.0, MovePhysics.AIR_FRICTION); // knocked back hard
        double next = 1.0 * MovePhysics.AIR_FRICTION + MovePhysics.airAcceleration(false);
        tracker.observe(next, 1, MovePhysics.airAcceleration(false), MovePhysics.AIR_FRICTION, false, TOLERANCE);
        assertTrue(tracker.lastMoveLegal());
    }

    @Test
    void mergedSlowMovesAreComparedAgainstAllTheirTicks() {
        // Sneaking on soul sand moves ~0.03 per tick, below Paper's 1/16-block event threshold,
        // so one event can cover three ticks of movement.
        double accel = MovePhysics.groundAcceleration(0.1, MovePhysics.DEFAULT_SLIPPERINESS) * MovePhysics.sneakFactor(0);
        SpeedTracker tracker = new SpeedTracker(10);
        for (int i = 0; i < 30; i++) {
            tracker.observe(0.08, 3, accel, GROUND_FRICTION, false, TOLERANCE);
            assertTrue(tracker.lastMoveLegal(), "event " + i);
        }
        assertFalse(tracker.exceeded(MAX_EXCESS));
    }

    @Test
    void sprintJumpOnIceIsAllowed() {
        double slip = 0.98;
        double friction = MovePhysics.groundFriction(slip);
        double accel = MovePhysics.groundAcceleration(SPRINT_SPEED, slip);
        SpeedTracker tracker = new SpeedTracker(10);
        double move = 0;
        for (int tick = 0; tick < 60; tick++) {
            // Jumping every tick under a low ceiling: the "ice boost" trick, legal in vanilla.
            move = move * friction + accel + MovePhysics.SPRINT_JUMP_BOOST;
            tracker.observe(move, 1, accel, friction, true, TOLERANCE);
            assertTrue(tracker.lastMoveLegal());
        }
        assertTrue(move > 1.0, "vanilla ice boost is very fast: " + move);
    }
}
