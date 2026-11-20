package dev.rage.anticheat.logic;

import java.util.Locale;

/**
 * Chat history of one player, for spam detection: message rate, repeated messages, and strikes
 * (blocked messages) that lead to an automatic mute. Chat arrives on async threads, so every
 * method is synchronized.
 */
public final class ChatSpam {

    public enum Verdict { OK, TOO_FAST, REPEATED }

    /** Upper bound for the configured message limit. */
    public static final int MAX_MESSAGES = 63;

    private final long[] times = new long[MAX_MESSAGES + 1];
    private int next;
    private int size;
    private String last = "";
    private long lastTime = Long.MIN_VALUE / 2;

    private final long[] strikes = new long[MAX_MESSAGES + 1];
    private int nextStrike;
    private int strikeCount;

    /**
     * Judges a message sent at {@code now}. Only allowed messages count toward the rate and
     * become the message that later repeats are compared with.
     *
     * @param maxMessages messages allowed within {@code windowMs}
     * @param repeatMs    how long the same message may not be sent again; 0 allows repeats
     */
    public synchronized Verdict check(String message, long now, int maxMessages, long windowMs, long repeatMs) {
        int limit = Math.max(1, Math.min(MAX_MESSAGES, maxMessages));
        String normalized = normalize(message);
        if (repeatMs > 0 && now - lastTime < repeatMs && normalized.equals(last)) {
            return Verdict.REPEATED;
        }
        if (countWithin(times, size, next, now, windowMs) >= limit) {
            return Verdict.TOO_FAST;
        }
        times[next] = now;
        next = (next + 1) % times.length;
        size = Math.min(size + 1, times.length);
        last = normalized;
        lastTime = now;
        return Verdict.OK;
    }

    /** Records a blocked message and returns how many were blocked within {@code windowMs}. */
    public synchronized int strike(long now, long windowMs) {
        strikes[nextStrike] = now;
        nextStrike = (nextStrike + 1) % strikes.length;
        strikeCount = Math.min(strikeCount + 1, strikes.length);
        return countWithin(strikes, strikeCount, nextStrike, now, windowMs);
    }

    public synchronized void clearStrikes() {
        strikeCount = 0;
    }

    /**
     * Lower case letters and digits only, so "Hello!!", "hello" and "H e l l o" count as the same
     * message. Messages without letters or digits ("!!!") are compared as typed.
     */
    static String normalize(String message) {
        StringBuilder sb = new StringBuilder(message.length());
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.isEmpty() ? message.trim().toLowerCase(Locale.ROOT) : sb.toString();
    }

    private static int countWithin(long[] ring, int size, int next, long now, long windowMs) {
        int count = 0;
        for (int i = 1; i <= size; i++) {
            long t = ring[(next - i + ring.length) % ring.length];
            if (now - t >= windowMs) {
                break; // older entries are older still
            }
            count++;
        }
        return count;
    }
}
