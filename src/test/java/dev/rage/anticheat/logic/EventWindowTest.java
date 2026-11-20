package dev.rage.anticheat.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EventWindowTest {

    @Test
    void countsOnlyEventsInsideTheWindow() {
        EventWindow window = new EventWindow(10);
        window.record(0, 20);
        window.record(5, 20);
        assertEquals(3, window.record(19, 20));
        assertEquals(2, window.record(25, 20), "ticks 0 and 5 dropped out of the window");
    }

    @Test
    void capacityPlusOneIsEnoughToSeeTheLimitExceeded() {
        int limit = 20;
        EventWindow window = new EventWindow(limit + 1);
        int count = 0;
        for (int i = 0; i < 100; i++) {
            count = window.record(0, 20); // nuker: everything in the same tick
        }
        assertEquals(limit + 1, count);
    }

    @Test
    void vanillaInstamineStaysAtTheLimit() {
        EventWindow window = new EventWindow(23);
        int max = 0;
        for (int tick = 0; tick < 200; tick++) {
            max = Math.max(max, window.record(tick, 20)); // one block per tick
        }
        assertEquals(20, max);
    }
}
