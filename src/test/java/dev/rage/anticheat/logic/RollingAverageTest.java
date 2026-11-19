package dev.rage.anticheat.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RollingAverageTest {

    @Test
    void averagesOnlyRecordedSamplesUntilFull() {
        RollingAverage avg = new RollingAverage(4);
        assertEquals(0, avg.average());
        avg.add(1);
        avg.add(3);
        assertFalse(avg.isFull());
        assertEquals(2, avg.average(), 1e-9);
    }

    @Test
    void oldestSampleIsDroppedOnceFull() {
        RollingAverage avg = new RollingAverage(3);
        avg.add(9);
        avg.add(1);
        avg.add(1);
        assertTrue(avg.isFull());
        assertEquals(11.0 / 3, avg.average(), 1e-9);
        avg.add(1); // pushes out the 9
        assertEquals(1, avg.average(), 1e-9);
        assertEquals(1, avg.max(), 1e-9);
    }

    @Test
    void singleLagSpikeIsSmoothedOut() {
        RollingAverage avg = new RollingAverage(10);
        for (int i = 0; i < 9; i++) {
            avg.add(0.28); // normal sprinting
        }
        avg.add(0.9); // one bundled packet after a lag spike
        assertTrue(avg.average() < 0.45, "spike should stay under the sprint limit");
    }

    @Test
    void clearResets() {
        RollingAverage avg = new RollingAverage(2);
        avg.add(5);
        avg.add(5);
        avg.clear();
        assertFalse(avg.isFull());
        assertEquals(0, avg.average());
        assertEquals(0, avg.max());
    }

    @Test
    void rejectsEmptyWindow() {
        assertThrows(IllegalArgumentException.class, () -> new RollingAverage(0));
    }
}
