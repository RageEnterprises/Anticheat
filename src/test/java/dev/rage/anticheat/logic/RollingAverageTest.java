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
        assertEquals(4, avg.sum(), 1e-9);
    }

    @Test
    void oldestSampleIsDroppedOnceFull() {
        RollingAverage avg = new RollingAverage(3);
        avg.add(9);
        avg.add(1);
        avg.add(1);
        assertTrue(avg.isFull());
        assertEquals(11, avg.sum(), 1e-9);
        avg.add(1); // pushes out the 9
        assertEquals(3, avg.sum(), 1e-9);
        assertEquals(1, avg.average(), 1e-9);
    }

    @Test
    void clearResets() {
        RollingAverage avg = new RollingAverage(2);
        avg.add(5);
        avg.add(5);
        avg.clear();
        assertFalse(avg.isFull());
        assertEquals(0, avg.sum());
    }

    @Test
    void rejectsEmptyWindow() {
        assertThrows(IllegalArgumentException.class, () -> new RollingAverage(0));
    }
}
