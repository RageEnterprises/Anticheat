package dev.rage.anticheat.logic;

/** Vanilla gravity model and airtime thresholds for the flight check. */
public record FlightLimits(int maxAirborneTicks, double minFallSpeed, double gravityTolerance) {

    public static final double GRAVITY = 0.08;
    public static final double DRAG = 0.98;
    public static final double JUMP_VELOCITY = 0.42;
    public static final double JUMP_BOOST_PER_LEVEL = 0.1;

    /** Vertical movement vanilla physics produces on the tick after moving {@code lastDy}. */
    public static double expectedDy(double lastDy) {
        return (lastDy - GRAVITY) * DRAG;
    }

    /** Upward velocity of a jump, with Jump Boost level (amplifier + 1) or 0. */
    public static double jumpVelocity(int jumpBoostLevel) {
        return JUMP_VELOCITY + JUMP_BOOST_PER_LEVEL * jumpBoostLevel;
    }

    /**
     * True when the player rose or fell slower than gravity allows.
     *
     * @param leftGround true if the player was on the ground on the previous move (a jump is allowed).
     */
    public boolean violatesGravity(double dy, double lastDy, boolean leftGround, int jumpBoostLevel) {
        double allowed = leftGround ? jumpVelocity(jumpBoostLevel) : expectedDy(lastDy);
        return dy > allowed + gravityTolerance;
    }

    /** True when the player has been airborne too long without falling at a real speed. */
    public boolean exceedsAirtime(int airborneTicks, double dy) {
        return airborneTicks > maxAirborneTicks && dy > -minFallSpeed;
    }
}
