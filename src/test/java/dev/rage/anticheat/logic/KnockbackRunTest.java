package dev.rage.anticheat.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KnockbackRunTest {

    @Test
    void fullKnockbackInTheAirPasses() {
        KnockbackRun run = new KnockbackRun();
        run.start(0.4, 0);
        double v = 0.4;
        for (int tick = 0; tick < 6; tick++) {
            run.move(v, 0);
            v *= 0.91;
        }
        assertTrue(run.ratio() > 1, "ratio " + run.ratio());
    }

    @Test
    void walkingTowardTheAttackerFirstDoesNotCount() {
        KnockbackRun run = new KnockbackRun();
        run.start(0, -0.4);
        run.move(0, 0.28);
        run.move(0, 0.28);
        run.move(0, -0.4);
        run.move(0, -0.22);
        run.move(0, -0.12);
        assertEquals(0.74, run.distance(), 1e-9);
        assertTrue(run.ratio() > 1);
    }

    @Test
    void ignoringKnockbackFails() {
        KnockbackRun run = new KnockbackRun();
        run.start(0.3, 0.3);
        run.move(0.02, 0);
        run.move(-0.1, 0.1);
        assertTrue(run.ratio() < 0.2, "ratio " + run.ratio());
    }

    @Test
    void noVelocityIsNotMeasured() {
        KnockbackRun run = new KnockbackRun();
        run.start(0, 0);
        assertFalse(run.active());
    }
}
