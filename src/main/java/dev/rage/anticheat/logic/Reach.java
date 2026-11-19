package dev.rage.anticheat.logic;

/** Distance from an attacker's eyes to the closest point of the target's hitbox. */
public final class Reach {

    private Reach() {
    }

    public static double distanceToBox(double x, double y, double z,
                                       double minX, double minY, double minZ,
                                       double maxX, double maxY, double maxZ) {
        double dx = x - clamp(x, minX, maxX);
        double dy = y - clamp(y, minY, maxY);
        double dz = z - clamp(z, minZ, maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
