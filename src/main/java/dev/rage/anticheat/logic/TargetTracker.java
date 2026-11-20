package dev.rage.anticheat.logic;

/** Remembers the last few attack targets to count how many different ones were hit recently. */
public final class TargetTracker {

    private final int[] ids;
    private final int[] ticks;
    private int next;
    private int size;

    public TargetTracker(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }
        ids = new int[capacity];
        ticks = new int[capacity];
    }

    /** Records a hit on {@code targetId} and returns how many distinct targets were hit within the window. */
    public int record(int targetId, int now, int windowTicks) {
        ids[next] = targetId;
        ticks[next] = now;
        next = (next + 1) % ids.length;
        if (size < ids.length) {
            size++;
        }
        int distinct = 0;
        for (int i = 0; i < size; i++) {
            if (now - ticks[i] >= windowTicks) {
                continue;
            }
            boolean seen = false;
            for (int j = 0; j < i; j++) {
                if (ids[j] == ids[i] && now - ticks[j] < windowTicks) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                distinct++;
            }
        }
        return distinct;
    }

    public void clear() {
        next = 0;
        size = 0;
    }
}
