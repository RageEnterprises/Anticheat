package dev.rage.anticheat.logic;

/**
 * Mouse input turns the camera in whole steps: each pitch change is a whole number of
 * {@code (sensitivity * 0.6 + 0.2)^3 * 8 * 0.15} degrees, at least 0.0096 at the lowest
 * sensitivity. The greatest common divisor of a player's pitch changes is that step. Rotations
 * computed by aimbots and bots are not whole steps, so their common divisor shrinks toward zero.
 * Cinematic camera and some zoom mods smooth the camera too, so this is a hint, not proof.
 */
public final class AimGcd {

    /** The smallest step any sensitivity produces, in degrees. */
    public static final double MIN_MOUSE_STEP = 0.0096;
    /** Pitch changes outside this range are skipped: tiny ones are rounding, big ones snaps or teleports. */
    private static final double MIN_DELTA = 0.005;
    private static final double MAX_DELTA = 3.0;
    /** Float rounding on the client; remainders below this count as zero. */
    private static final double EPSILON = 5e-4;

    private final double[] deltas;
    private int size;

    public AimGcd(int samples) {
        if (samples < 2) {
            throw new IllegalArgumentException("samples must be at least 2");
        }
        deltas = new double[samples];
    }

    /**
     * Adds a pitch change in degrees. Once enough have been collected, returns their common step
     * and starts over; otherwise returns NaN.
     */
    public double add(double pitchDelta) {
        double delta = Math.abs(pitchDelta);
        if (delta < MIN_DELTA || delta > MAX_DELTA) {
            return Double.NaN;
        }
        deltas[size++] = delta;
        if (size < deltas.length) {
            return Double.NaN;
        }
        size = 0;
        return step(deltas.clone());
    }

    public void reset() {
        size = 0;
    }

    /** Share of changes that must be whole steps for the rotations to count as mouse input. */
    private static final double MIN_MATCHED = 0.9;
    /** How far from a whole number of steps a change may be. */
    private static final double TOLERANCE = 0.2;

    /**
     * The common step of the changes, or 0 if they have none. The step is first estimated from the
     * three smallest changes, which have the least rounding error, then refined with each larger
     * change that is a whole number of steps.
     */
    static double step(double[] deltas) {
        java.util.Arrays.sort(deltas);
        double step = gcd(gcd(deltas[0], deltas[1]), deltas[Math.min(2, deltas.length - 1)]);
        if (step < MIN_DELTA / 4) {
            return step;
        }
        double sum = 0;
        long steps = 0;
        int matched = 0;
        for (double delta : deltas) {
            double ratio = delta / step;
            long whole = Math.round(ratio);
            if (whole > 0 && Math.abs(ratio - whole) < TOLERANCE) {
                matched++;
                sum += delta;
                steps += whole;
                step = sum / steps;
            }
        }
        return matched >= deltas.length * MIN_MATCHED ? step : 0;
    }

    static double gcd(double a, double b) {
        while (b > EPSILON) {
            double r = a % b;
            a = b;
            b = r;
        }
        return a;
    }
}
