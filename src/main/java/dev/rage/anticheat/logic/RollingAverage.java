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

    public boolean isFull() {
        return count == samples.length;
    }

    public double sum() {
        double sum = 0;
        for (int i = 0; i < count; i++) {
            sum += samples[i];
        }
        return sum;
    }

    public double average() {
        return count == 0 ? 0 : sum() / count;
    }

    public void clear() {
        next = 0;
        count = 0;
    }
}
