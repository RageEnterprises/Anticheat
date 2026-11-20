package dev.rage.anticheat.logic;

/** Vector angle helpers for look-direction checks. */
public final class Angles {

    private Angles() {
    }

    /** Angle in degrees between two vectors; 0 if either has no length. */
    public static double between(double ax, double ay, double az, double bx, double by, double bz) {
        double lengths = Math.sqrt((ax * ax + ay * ay + az * az) * (bx * bx + by * by + bz * bz));
        if (lengths == 0) {
            return 0;
        }
        double cos = (ax * bx + ay * by + az * bz) / lengths;
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos))));
    }
}
