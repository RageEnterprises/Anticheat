package dev.rage.anticheat.logic;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TimerBalanceTest {

    private static final long MS = 1_000_000L;
    private static final double MAX_AHEAD = 300;
    private static final double MAX_CREDIT = 1000;

    @Test
    void normalClientStaysAtZero() {
        TimerBalance timer = new TimerBalance();
        long t = 0;
        for (int i = 0; i < 2000; i++) {
            assertTrue(timer.onPacket(t, MAX_CREDIT) <= MAX_AHEAD);
            t += 50 * MS;
        }
        assertTrue(timer.balance() <= 0);
    }

    @Test
    void spedUpClientIsFlagged() {
        TimerBalance timer = new TimerBalance();
        long t = 0;
        int packets = 0;
        while (timer.onPacket(t, MAX_CREDIT) <= MAX_AHEAD) {
            t += 40 * MS; // 25 packets per second, a 1.25x timer
            packets++;
        }
        assertTrue(packets < 50, "flagged after " + packets + " packets");
    }

    @Test
    void lagBurstWithinCreditIsAllowed() {
        TimerBalance timer = new TimerBalance();
        timer.onPacket(0, MAX_CREDIT);
        long t = 800 * MS; // 800 ms network hiccup, then 16 queued packets arrive at once
        for (int i = 0; i < 16; i++) {
            assertTrue(timer.onPacket(t, MAX_CREDIT) <= MAX_AHEAD);
        }
    }

    @Test
    void idleCreditIsCapped() {
        TimerBalance timer = new TimerBalance();
        timer.onPacket(0, MAX_CREDIT);
        timer.onPacket(60_000 * MS, MAX_CREDIT); // stood still for a minute
        assertTrue(timer.balance() >= -MAX_CREDIT);
    }

    @Test
    void continuingAfterFlagIsFlaggedAgainQuickly() {
        TimerBalance timer = new TimerBalance();
        timer.afterFlag(MAX_AHEAD);
        long t = 0;
        int packets = 0;
        timer.onPacket(t, MAX_CREDIT);
        while (timer.onPacket(t += 25 * MS, MAX_CREDIT) <= MAX_AHEAD) { // 2x timer
            packets++;
        }
        assertTrue(packets < 10, "flagged again after " + packets + " packets");
    }
}
