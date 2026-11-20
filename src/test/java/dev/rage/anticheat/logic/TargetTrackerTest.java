package dev.rage.anticheat.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TargetTrackerTest {

    @Test
    void hittingOneTargetRepeatedlyCountsOnce() {
        TargetTracker tracker = new TargetTracker(8);
        int distinct = 0;
        for (int tick = 0; tick < 10; tick++) {
            distinct = tracker.record(42, tick, 10);
        }
        assertEquals(1, distinct);
    }

    @Test
    void auraHittingManyTargetsAtOnceIsCounted() {
        TargetTracker tracker = new TargetTracker(8);
        tracker.record(1, 0, 10);
        tracker.record(2, 1, 10);
        tracker.record(3, 2, 10);
        assertEquals(4, tracker.record(4, 3, 10));
    }

    @Test
    void oldTargetsLeaveTheWindow() {
        TargetTracker tracker = new TargetTracker(8);
        tracker.record(1, 0, 10);
        tracker.record(2, 5, 10);
        assertEquals(2, tracker.record(3, 12, 10), "target 1 was hit 12 ticks ago");
    }
}
