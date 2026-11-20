package dev.rage.anticheat.logic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class XrayTrackerTest {

    private static final int MIN_ORES = 4;
    private static final double MAX_RATIO = 0.02;

    @Test
    void stripMinerIsNotSuspicious() {
        XrayTracker tracker = new XrayTracker();
        boolean suspicious = false;
        // Branch mining finds roughly one hidden diamond vein per few hundred stone.
        for (int vein = 0; vein < 10; vein++) {
            for (int i = 0; i < 300; i++) {
                tracker.mineStone();
            }
            suspicious |= tracker.mineHiddenOre(MIN_ORES, MAX_RATIO);
        }
        assertFalse(suspicious);
    }

    @Test
    void tunnellingStraightToOresIsSuspicious() {
        XrayTracker tracker = new XrayTracker();
        boolean suspicious = false;
        for (int vein = 0; vein < 4; vein++) {
            for (int i = 0; i < 25; i++) {
                tracker.mineStone();
            }
            suspicious = tracker.mineHiddenOre(MIN_ORES, MAX_RATIO);
        }
        assertTrue(suspicious);
    }

    @Test
    void oneLuckyFindIsNotEnough() {
        XrayTracker tracker = new XrayTracker();
        assertFalse(tracker.mineHiddenOre(MIN_ORES, MAX_RATIO), "a single ore with no stone mined");
    }
}
