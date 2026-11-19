package dev.rage.anticheat.logic;

/** Fixed-size ring buffer of samples. Allocation-free after construction. */
public final class RollingAverage {

    private final double[] samples;
    private int next;
    private int count;

    public RollingAverage(int size) {
        if (size < 1) {
            throw new IllegalArgumentException("size must be at least 1");
        }
        this.samples = new double[size];
    }

    public void add(double value) {
        samples[next] = value;
        next = (next + 1) % samples.length;
        if (count < samples.length) {
            count++;
        }
    }

    /** True once the window holds {@code size} samples. Checks should wait for this. */
    public boolean isFull() {
        return count == samples.length;
    }

    public double average() {
        if (count == 0) {
            return 0;
        }
        double sum = 0;
        for (int i = 0; i < count; i++) {
            sum += samples[i];
        }
        return sum / count;
    }

    public double max() {
        double max = 0;
        for (int i = 0; i < count; i++) {
            max = Math.max(max, samples[i]);
        }
        return max;
    }

    public void clear() {
        next = 0;
        count = 0;
    }
}
