package dev.rage.anticheat.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ViolationCounterTest {

    @Test
    void reachesThreshold() {
        ViolationCounter vl = new ViolationCounter();
        for (int i = 0; i < 4; i++) {
            vl.increment();
        }
        assertFalse(vl.reached(5));
        vl.increment();
        assertTrue(vl.reached(5));
    }

    @Test
    void decayNeverGoesNegative() {
        ViolationCounter vl = new ViolationCounter();
        vl.increment();
        vl.decay(0.5);
        assertEquals(0.5, vl.level(), 1e-9);
        assertEquals(1, vl.count(), "count rounds up");
        vl.decay(10);
        assertEquals(0, vl.level());
    }

    @Test
    void decayKeepsOccasionalFlagsBelowThreshold() {
        ViolationCounter vl = new ViolationCounter();
        // One flag every 3 seconds with 0.5 decay per second never accumulates.
        for (int second = 0; second < 300; second++) {
            if (second % 3 == 0) {
                vl.increment();
            }
            vl.decay(0.5);
        }
        assertFalse(vl.reached(2));
    }
}
