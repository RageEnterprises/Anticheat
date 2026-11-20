package dev.rage.anticheat.logic;

/**
 * Measures how far a player moves along the direction of the knockback they were given. The run
 * is the longest stretch of movement in that direction, so walking toward the attacker before the
 * knockback arrives does not count against the player.
 */
public final class KnockbackRun {

    /**
     * Distance a knocked-back player covers in the first few ticks, as a multiple of the starting
     * speed: 1 + 0.546 + 0.3 with ground friction, more in the air. The ground value is used.
     */
    public static final double EXPECTED_TICKS = 1.8;

    private double dirX;
    private double dirZ;
    private double expected;
    private double position;
    private double lowest;
    private double best;
    private int moves;
    private boolean active;

    /** Starts measuring knockback with horizontal velocity (vx, vz) in blocks per tick. */
    public void start(double vx, double vz) {
        double speed = Math.sqrt(vx * vx + vz * vz);
        active = speed > 0;
        if (!active) {
            return;
        }
        dirX = vx / speed;
        dirZ = vz / speed;
        expected = speed * EXPECTED_TICKS;
        position = 0;
        lowest = 0;
        best = 0;
        moves = 0;
    }

    public void move(double dx, double dz) {
        if (!active) {
            return;
        }
        position += dx * dirX + dz * dirZ;
        lowest = Math.min(lowest, position);
        best = Math.max(best, position - lowest);
        moves++;
    }

    public boolean active() {
        return active;
    }

    public int moves() {
        return moves;
    }

    /** The distance moved along the knockback, as a share of the distance expected. */
    public double ratio() {
        return expected <= 0 ? 1 : best / expected;
    }

    public double distance() {
        return best;
    }

    public double expected() {
        return expected;
    }

    public void stop() {
        active = false;
    }
}
