package dev.rage.anticheat.logic;

/**
 * Tracks how far a client's movement packets run ahead of real time. A vanilla client sends one per
 * 50 ms tick, so a growing balance means the game is sped up (a "Timer" cheat).
 */
public final class TimerBalance {

    private static final double TICK_MS = 50;
    /** Forgives 2% of drift so jitter cannot slowly add up to a flag. */
    private static final double FORGIVENESS_MS = 1;

    private long lastNanos = -1;
    private double balanceMs;

    /**
     * Records one movement packet and returns the balance in ms (positive = ahead of real time).
     *
     * @param maxCreditMs how much idle or laggy time may be banked to absorb a burst of delayed packets
     */
    public double onPacket(long nowNanos, double maxCreditMs) {
        if (lastNanos >= 0) {
            double elapsedMs = (nowNanos - lastNanos) / 1_000_000.0;
            balanceMs = Math.max(-maxCreditMs, balanceMs + TICK_MS - FORGIVENESS_MS - elapsedMs);
        }
        lastNanos = nowNanos;
        return balanceMs;
    }

    public double balance() {
        return balanceMs;
    }

    /** After a flag: stay close to the limit so a continuing cheat is flagged again quickly. */
    public void afterFlag(double maxAheadMs) {
        balanceMs = maxAheadMs / 2;
    }

    /** Forget history, e.g. after the server itself lagged and delivered packets in a burst. */
    public void reset() {
        lastNanos = -1;
        balanceMs = 0;
    }
}
