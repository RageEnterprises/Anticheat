package dev.rage.anticheat.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class AimGcdTest {

    /** The pitch step for a sensitivity from 0 to 1, as the vanilla client computes it. */
    private static double step(double sensitivity) {
        double f = sensitivity * 0.6 + 0.2;
        return f * f * f * 8 * 0.15;
    }

    private static double run(AimGcd aim, int samples, java.util.function.IntToDoubleFunction delta) {
        double result = Double.NaN;
        for (int i = 0; i < samples; i++) {
            result = aim.add(delta.applyAsDouble(i));
        }
        return result;
    }

    @Test
    void mouseMovementKeepsTheSensitivityStep() {
        Random random = new Random(1);
        for (double sensitivity : new double[] {0.0, 0.1, 0.5, 1.0}) {
            double step = step(sensitivity);
            // Float rounding of the client's pitch adds a little noise to every change.
            double found = run(new AimGcd(30), 30,
                    i -> (1 + random.nextInt(Math.max(1, (int) (2.5 / step)))) * step + random.nextGaussian() * 2e-6);
            assertTrue(found >= AimGcd.MIN_MOUSE_STEP * 0.9, "sensitivity " + sensitivity + ": " + found);
        }
    }

    @Test
    void computedRotationsHaveNoCommonStep() {
        Random random = new Random(2);
        double found = run(new AimGcd(30), 30, i -> 0.01 + random.nextDouble() * 2);
        assertTrue(found < AimGcd.MIN_MOUSE_STEP / 2, "found " + found);
    }

    @Test
    void ignoresTinyAndHugeChangesAndWaitsForSamples() {
        AimGcd aim = new AimGcd(3);
        assertTrue(Double.isNaN(aim.add(0.001)));
        assertTrue(Double.isNaN(aim.add(45)));
        assertTrue(Double.isNaN(aim.add(0.3)));
        assertTrue(Double.isNaN(aim.add(0.6)));
        assertEquals(0.15, aim.add(0.45), 1e-9);
    }
}
