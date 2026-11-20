package dev.rage.anticheat.logic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JoinFloodTest {

    private static final int MAX = 8;
    private static final long WINDOW = 10_000;
    private static final long ATTACK = 60_000;

    @Test
    void normalTrafficIsAllowed() {
        JoinFlood flood = new JoinFlood();
        for (int i = 0; i < 100; i++) {
            assertFalse(flood.newPlayer(i * 2_000L, MAX, WINDOW, ATTACK).blocked(), "one new player every 2s");
        }
    }

    @Test
    void floodStartsAttackModeOnce() {
        JoinFlood flood = new JoinFlood();
        for (int i = 0; i < MAX; i++) {
            assertFalse(flood.newPlayer(i * 100L, MAX, WINDOW, ATTACK).blocked());
        }
        JoinFlood.Decision first = flood.newPlayer(900, MAX, WINDOW, ATTACK);
        assertTrue(first.blocked());
        assertTrue(first.attackStarted());
        JoinFlood.Decision next = flood.newPlayer(1_000, MAX, WINDOW, ATTACK);
        assertTrue(next.blocked());
        assertFalse(next.attackStarted(), "staff are alerted only once");
    }

    @Test
    void attackModeEnds() {
        JoinFlood flood = new JoinFlood();
        for (int i = 0; i <= MAX; i++) {
            flood.newPlayer(0, MAX, WINDOW, ATTACK);
        }
        assertTrue(flood.attackActive(59_999));
        assertFalse(flood.attackActive(60_000));
        assertFalse(flood.newPlayer(120_000, MAX, WINDOW, ATTACK).blocked());
    }
}
