package dev.rage.anticheat.logic;

/** Maximum horizontal distance per tick, in blocks, for each movement state. */
public record SpeedLimits(double walk, double sprint, double sneak, double swim, double potionBonusPerLevel) {

    /**
     * @param speedLevel Speed effect level (amplifier + 1), or 0 without the effect.
     */
    public double max(boolean inLiquid, boolean sneaking, boolean sprinting, int speedLevel) {
        double base;
        if (inLiquid) {
            base = swim;
        } else if (sneaking) {
            base = sneak;
        } else if (sprinting) {
            base = sprint;
        } else {
            base = walk;
        }
        return base * (1 + potionBonusPerLevel * speedLevel);
    }

    /** True when the window is full and its average distance exceeds the highest limit seen in it. */
    public static boolean exceeds(RollingAverage distances, RollingAverage limits) {
        return distances.isFull() && distances.average() > limits.max();
    }
}
