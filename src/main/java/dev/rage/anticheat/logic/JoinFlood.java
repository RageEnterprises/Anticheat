package dev.rage.anticheat.logic;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Detects bot floods: too many first-time players connecting within a short window turns on
 * "attack mode" for a while, during which first-time players are refused. Thread-safe, because
 * login checks run on several threads at once.
 */
public final class JoinFlood {

    /** Whether this connection is refused, and whether it is the one that started attack mode. */
    public record Decision(boolean blocked, boolean attackStarted) {
    }

    private final Deque<Long> recent = new ArrayDeque<>();
    private long attackUntil;

    public synchronized Decision newPlayer(long now, int maxJoins, long windowMillis, long attackMillis) {
        while (!recent.isEmpty() && now - recent.peekFirst() >= windowMillis) {
            recent.pollFirst();
        }
        recent.addLast(now);
        if (now < attackUntil) {
            return new Decision(true, false);
        }
        if (recent.size() > maxJoins) {
            attackUntil = now + attackMillis;
            return new Decision(true, true);
        }
        return new Decision(false, false);
    }

    public synchronized boolean attackActive(long now) {
        return now < attackUntil;
    }
}
