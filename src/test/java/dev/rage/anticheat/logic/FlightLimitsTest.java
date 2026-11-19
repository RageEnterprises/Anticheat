package dev.rage.anticheat.logic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FlightLimitsTest {

    private final FlightLimits limits = new FlightLimits(40, 0.5, 0.05);

    @Test
    void vanillaJumpArcPasses() {
        double dy = FlightLimits.JUMP_VELOCITY;
        assertFalse(limits.violatesGravity(dy, 0, true, 0), "jump off the ground");
        for (int tick = 0; tick < 30; tick++) {
            double next = FlightLimits.expectedDy(dy);
            assertFalse(limits.violatesGravity(next, dy, false, 0), "tick " + tick);
            dy = next;
        }
    }

    @Test
    void jumpBoostAllowsHigherJump() {
        assertTrue(limits.violatesGravity(0.6, 0, true, 0));
        assertFalse(limits.violatesGravity(0.6, 0, true, 2));
    }

    @Test
    void hoveringInAirIsFlagged() {
        // Falling one tick, then holding still: gravity says it should be falling faster.
        assertTrue(limits.violatesGravity(0, -0.0784, false, 0));
    }

    @Test
    void risingWithoutGroundIsFlagged() {
        assertTrue(limits.violatesGravity(0.42, -0.2, false, 0), "mid-air jump");
    }

    @Test
    void airtimeBelowThresholdIsAllowed() {
        assertFalse(limits.exceedsAirtime(40, 0));
    }

    @Test
    void airtimeAboveThresholdWithoutFallingIsFlagged() {
        assertTrue(limits.exceedsAirtime(41, 0));
        assertTrue(limits.exceedsAirtime(41, -0.2));
    }

    @Test
    void longRealFallIsAllowed() {
        assertFalse(limits.exceedsAirtime(60, -2.5));
    }
}
