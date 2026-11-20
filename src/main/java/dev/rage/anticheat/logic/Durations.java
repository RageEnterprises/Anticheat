package dev.rage.anticheat.logic;

import java.util.Locale;

/** Parses and formats punishment durations such as {@code 30m}, {@code 1d12h} or {@code perm}. */
public final class Durations {

    /** Returned by {@link #parse} for "permanent". */
    public static final long PERMANENT = 0;
    /** Returned by {@link #parse} when the text is not a duration. */
    public static final long INVALID = -1;

    private static final long SECOND = 1000;
    private static final long MINUTE = 60 * SECOND;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;
    private static final long WEEK = 7 * DAY;
    private static final long MONTH = 30 * DAY;
    private static final long YEAR = 365 * DAY;

    private Durations() {
    }

    /** Milliseconds, {@link #PERMANENT}, or {@link #INVALID}. Units: s, m, h, d, w, mo, y. */
    public static long parse(String text) {
        String s = text.toLowerCase(Locale.ROOT).trim();
        if (s.equals("perm") || s.equals("permanent") || s.equals("forever")) {
            return PERMANENT;
        }
        long total = 0;
        int i = 0;
        while (i < s.length()) {
            int start = i;
            while (i < s.length() && Character.isDigit(s.charAt(i))) {
                i++;
            }
            if (i == start || i - start > 9) {
                return INVALID;
            }
            long amount = Long.parseLong(s.substring(start, i));
            long unit;
            if (s.startsWith("mo", i)) {
                unit = MONTH;
                i += 2;
            } else if (i < s.length()) {
                unit = switch (s.charAt(i)) {
                    case 's' -> SECOND;
                    case 'm' -> MINUTE;
                    case 'h' -> HOUR;
                    case 'd' -> DAY;
                    case 'w' -> WEEK;
                    case 'y' -> YEAR;
                    default -> INVALID;
                };
                i++;
            } else {
                return INVALID; // a number without a unit
            }
            if (unit == INVALID) {
                return INVALID;
            }
            try {
                total = Math.addExact(total, Math.multiplyExact(amount, unit));
            } catch (ArithmeticException tooLong) {
                return INVALID;
            }
        }
        return total > 0 ? total : INVALID;
    }

    /** Up to the two largest units, e.g. "1d 12h", or "permanent" for {@link #PERMANENT}. */
    public static String format(long millis) {
        if (millis == PERMANENT) {
            return "permanent";
        }
        long[] units = {YEAR, MONTH, WEEK, DAY, HOUR, MINUTE, SECOND};
        String[] names = {"y", "mo", "w", "d", "h", "m", "s"};
        StringBuilder out = new StringBuilder();
        int parts = 0;
        long rest = millis;
        for (int u = 0; u < units.length && parts < 2; u++) {
            long n = rest / units[u];
            if (n > 0) {
                if (out.length() > 0) {
                    out.append(' ');
                }
                out.append(n).append(names[u]);
                rest -= n * units[u];
                parts++;
            }
        }
        return out.length() == 0 ? "0s" : out.toString();
    }
}
