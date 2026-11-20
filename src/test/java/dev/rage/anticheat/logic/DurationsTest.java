package dev.rage.anticheat.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DurationsTest {

    private static final long MINUTE = 60_000;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    @Test
    void parsesSingleAndCombinedUnits() {
        assertEquals(30 * MINUTE, Durations.parse("30m"));
        assertEquals(DAY + 12 * HOUR, Durations.parse("1d12h"));
        assertEquals(60 * DAY, Durations.parse("2mo"));
        assertEquals(14 * DAY, Durations.parse("2W"));
    }

    @Test
    void permanentWords() {
        assertEquals(Durations.PERMANENT, Durations.parse("perm"));
        assertEquals(Durations.PERMANENT, Durations.parse("permanent"));
    }

    @Test
    void rejectsReasonsAndBareNumbers() {
        assertEquals(Durations.INVALID, Durations.parse("hacking"));
        assertEquals(Durations.INVALID, Durations.parse("10"));
        assertEquals(Durations.INVALID, Durations.parse("0d"));
        assertEquals(Durations.INVALID, Durations.parse("5x"));
        assertEquals(Durations.INVALID, Durations.parse("99999999999d"));
        assertEquals(Durations.INVALID, Durations.parse("999999999y"), "overflows a long");
    }

    @Test
    void formatsTwoLargestUnits() {
        assertEquals("1d 12h", Durations.format(DAY + 12 * HOUR + 5 * MINUTE));
        assertEquals("30m", Durations.format(30 * MINUTE));
        assertEquals("permanent", Durations.format(Durations.PERMANENT));
    }
}
