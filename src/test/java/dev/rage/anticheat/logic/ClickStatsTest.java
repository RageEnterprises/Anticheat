package dev.rage.anticheat.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClickStatsTest {

    @Test
    void countsClicksInTheLastSecond() {
        ClickStats clicks = new ClickStats(30);
        for (int i = 0; i < 15; i++) {
            clicks.click(i * 50L, 500);
        }
        assertEquals(15, clicks.cps(700));
        assertEquals(5, clicks.cps(1450)); // clicks at 500..700
    }

    @Test
    void evenClicksHaveNoDeviation() {
        ClickStats clicks = new ClickStats(30);
        for (int i = 0; i <= 20; i++) {
            clicks.click(i * 100L, 500);
        }
        assertTrue(clicks.full(20));
        assertEquals(100, clicks.meanInterval(20), 1e-9);
        assertEquals(0, clicks.deviation(20), 1e-9);
    }

    @Test
    void humanClicksVary() {
        ClickStats clicks = new ClickStats(30);
        long t = 0;
        long[] gaps = {80, 130, 95, 150, 70, 110, 125, 90, 160, 85};
        clicks.click(t, 500);
        for (int i = 0; i < 20; i++) {
            t += gaps[i % gaps.length];
            clicks.click(t, 500);
        }
        assertEquals(109.5, clicks.meanInterval(20), 1e-9);
        assertTrue(clicks.deviation(20) > 25);
    }

    @Test
    void pauseStartsANewStreak() {
        ClickStats clicks = new ClickStats(30);
        for (int i = 0; i <= 20; i++) {
            clicks.click(i * 100L, 500);
        }
        clicks.click(5000, 500);
        assertFalse(clicks.full(20));
        assertEquals(1, clicks.cps(5000));
    }
}
