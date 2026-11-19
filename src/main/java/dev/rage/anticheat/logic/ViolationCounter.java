package dev.rage.anticheat.logic;

/** Per-player violation level that decays over time. */
public final class ViolationCounter {

    private double level;

    /** Adds one violation and returns the new level. */
    public double increment() {
        return ++level;
    }

    public void decay(double amount) {
        level = Math.max(0, level - amount);
    }

    public boolean reached(double limit) {
        return level >= limit;
    }

    public double level() {
        return level;
    }

    /** Level rounded up, for display in {count}. */
    public int count() {
        return (int) Math.ceil(level);
    }

    public void reset() {
        level = 0;
    }
}
