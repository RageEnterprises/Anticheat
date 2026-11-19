package dev.rage.anticheat.logic;

/**
 * Vanilla horizontal movement constants (LivingEntity#travel). Each tick the game moves the player by
 * {@code previousMove * friction + acceleration}, so this is enough to predict the fastest legal move.
 */
public final class MovePhysics {

    public static final double AIR_FRICTION = 0.91;
    public static final double DEFAULT_SLIPPERINESS = 0.6;
    public static final double SPRINT_JUMP_BOOST = 0.2;
    public static final double LAVA_FRICTION = 0.5;
    public static final double LIQUID_ACCELERATION = 0.02;
    /** Upper bound for the push flowing water or lava adds per tick. */
    public static final double FLOW_PUSH = 0.014;

    private MovePhysics() {
    }

    public static double groundFriction(double slipperiness) {
        return slipperiness * AIR_FRICTION;
    }

    /** @param movementSpeed the movement_speed attribute (0.1 walking, 0.13 sprinting, more with Speed). */
    public static double groundAcceleration(double movementSpeed, double slipperiness) {
        return movementSpeed * (0.21600002 / (slipperiness * slipperiness * slipperiness));
    }

    public static double airAcceleration(boolean sprinting) {
        return sprinting ? 0.026 : 0.02;
    }

    /** Sneaking scales input to 30%, plus 15% per Swift Sneak level. */
    public static double sneakFactor(int swiftSneakLevel) {
        return Math.min(1, 0.3 + 0.15 * swiftSneakLevel);
    }

    public static double waterFriction(boolean sprinting, boolean dolphinsGrace, int depthStrider, boolean onGround) {
        double friction = dolphinsGrace ? 0.96 : sprinting ? 0.9 : 0.8;
        double strider = depthStriderFactor(depthStrider, onGround);
        return friction + (0.54600006 - friction) * strider;
    }

    public static double waterAcceleration(double movementSpeed, int depthStrider, boolean onGround) {
        double strider = depthStriderFactor(depthStrider, onGround);
        return LIQUID_ACCELERATION + (movementSpeed - LIQUID_ACCELERATION) * strider;
    }

    private static double depthStriderFactor(int level, boolean onGround) {
        double factor = Math.min(level, 3) / 3.0;
        return onGround ? factor : factor / 2;
    }
}
