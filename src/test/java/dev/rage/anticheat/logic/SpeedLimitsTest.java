package dev.rage.anticheat.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpeedLimitsTest {

    private final SpeedLimits limits = new SpeedLimits(0.30, 0.45, 0.20, 0.35, 0.20);

    @Test
    void picksLimitByState() {
        assertEquals(0.30, limits.max(false, false, false, 0), 1e-9);
        assertEquals(0.45, limits.max(false, false, true, 0), 1e-9);
        assertEquals(0.20, limits.max(false, true, false, 0), 1e-9);
        assertEquals(0.35, limits.max(true, false, true, 0), 1e-9, "liquid wins over sprinting");
    }

    @Test
    void speedEffectRaisesLimit() {
        assertEquals(0.45 * 1.4, limits.max(false, false, true, 2), 1e-9);
    }

    @Test
    void waitsForFullWindow() {
        RollingAverage distances = new RollingAverage(3);
        RollingAverage maxes = new RollingAverage(3);
        distances.add(5);
        maxes.add(0.3);
        assertFalse(SpeedLimits.exceeds(distances, maxes));
    }

    @Test
    void flagsSustainedSpeedAboveLimit() {
        RollingAverage distances = new RollingAverage(3);
        RollingAverage maxes = new RollingAverage(3);
        for (int i = 0; i < 3; i++) {
            distances.add(0.6);
            maxes.add(0.45);
        }
        assertTrue(SpeedLimits.exceeds(distances, maxes));
    }

    @Test
    void usesHighestLimitInWindowSoStoppingSprintIsNotFlagged() {
        RollingAverage distances = new RollingAverage(3);
        RollingAverage maxes = new RollingAverage(3);
        distances.add(0.40);
        maxes.add(0.45); // sprinting
        distances.add(0.38);
        maxes.add(0.20); // started sneaking, momentum carries
        distances.add(0.35);
        maxes.add(0.20);
        assertFalse(SpeedLimits.exceeds(distances, maxes));
    }
}
