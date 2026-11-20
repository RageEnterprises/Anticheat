package dev.rage.anticheat.logic;

/**
 * Recent clicks of one player, for AutoClicker: clicks per second, and how evenly spaced the
 * clicks are. People click with tens of milliseconds of natural variation; a macro or cheat clicks
 * at a near-constant rate. A pause ends a streak, so spacing is only judged within one streak.
 */
public final class ClickStats {

    private final long[] times;
    private int next;
    private int size;

    /** @param capacity clicks to remember; at least the sample size and the CPS limit + 1 */
    public ClickStats(int capacity) {
        times = new long[Math.max(2, capacity)];
    }

    /**
     * Records a click at {@code nowMs}. A gap longer than {@code maxGapMs} since the last click
     * starts a new streak.
     */
    public void click(long nowMs, long maxGapMs) {
        if (size > 0 && nowMs - times[index(1)] > maxGapMs) {
            size = 0;
        }
        times[next] = nowMs;
        next = (next + 1) % times.length;
        size = Math.min(size + 1, times.length);
    }

    /** Clicks within the last second, including the one at {@code nowMs}. */
    public int cps(long nowMs) {
        int count = 0;
        for (int i = 1; i <= size && nowMs - times[index(i)] < 1000; i++) {
            count++;
        }
        return count;
    }

    /** True once the current streak holds {@code samples} intervals. */
    public boolean full(int samples) {
        return size > samples && samples < times.length;
    }

    /** Mean of the last {@code samples} intervals, in milliseconds. Call only when {@link #full}. */
    public double meanInterval(int samples) {
        return (double) (times[index(1)] - times[index(samples + 1)]) / samples;
    }

    /** Standard deviation of the last {@code samples} intervals, in milliseconds. Call only when {@link #full}. */
    public double deviation(int samples) {
        double mean = meanInterval(samples);
        double sum = 0;
        for (int i = 1; i <= samples; i++) {
            double d = (times[index(i)] - times[index(i + 1)]) - mean;
            sum += d * d;
        }
        return Math.sqrt(sum / samples);
    }

    public void reset() {
        size = 0;
    }

    /** Index of the i-th most recent click (1 = newest). */
    private int index(int i) {
        return (next - i + times.length) % times.length;
    }
}
