package dev.rage.anticheat.logic;

/**
 * Counts events within a sliding window of server ticks. Remembers only the last {@code capacity}
 * events, so set capacity to the limit + 1: that is enough to tell when the limit is exceeded.
 */
public final class EventWindow {

    private final int[] ticks;
    private int next;
    private int size;

    public EventWindow(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }
        this.ticks = new int[capacity];
    }

    /** Records an event at {@code now} and returns how many recorded events fall within the window. */
    public int record(int now, int windowTicks) {
        ticks[next] = now;
        next = (next + 1) % ticks.length;
        if (size < ticks.length) {
            size++;
        }
        return count(now, windowTicks);
    }

    public int count(int now, int windowTicks) {
        int count = 0;
        for (int i = 0; i < size; i++) {
            if (now - ticks[i] < windowTicks) {
                count++;
            }
        }
        return count;
    }

    public void clear() {
        next = 0;
        size = 0;
    }
}
