package dev.rage.anticheat.logic;

/**
 * Predicts the largest legal horizontal move from the previous one and sums the excess over a rolling window.
 *
 * <p>The prediction continues from the smaller of the real and the allowed previous move. This stops a cheat
 * from building speed by staying just inside the tolerance every tick.
 */
public final class SpeedTracker {

    private final RollingAverage excess;
    private double previousMove;
    private double previousFriction;
    private double lastAllowed;
    private double lastOver;

    public SpeedTracker(int samples) {
        this.excess = new RollingAverage(samples);
    }

    /**
     * Records one move and returns how far it went past the allowed distance (0 if legal).
     *
     * @param friction friction this tick applies to the move afterwards; used to predict the next move
     * @param ticks game ticks covered by this move; usually 1, more when the server merged tiny moves
     */
    public double observe(double move, int ticks, double acceleration, double friction, boolean sprintJump,
                          double tolerance) {
        double tick = previousMove * previousFriction + acceleration
                + (sprintJump ? MovePhysics.SPRINT_JUMP_BOOST : 0);
        double allowed = tick;
        for (int i = 1; i < ticks; i++) {
            tick = tick * friction + acceleration;
            allowed += tick;
        }
        double over = Math.max(0, move - allowed - tolerance * ticks);
        excess.add(over);
        lastOver = over;
        lastAllowed = allowed;
        previousMove = Math.min(move, tick);
        previousFriction = friction;
        return over;
    }

    /** During a grace period: keep the real momentum so the prediction continues correctly afterwards. */
    public void carry(double move, double friction) {
        excess.clear();
        lastOver = 0;
        previousMove = move;
        previousFriction = friction;
    }

    /** True when the summed excess over the window is above {@code maxExcess} blocks. */
    public boolean exceeded(double maxExcess) {
        return excess.sum() > maxExcess;
    }

    /** False if the last move went past the allowed distance; such positions are not used for setbacks. */
    public boolean lastMoveLegal() {
        return lastOver == 0;
    }

    public double excess() {
        return excess.sum();
    }

    public double lastAllowed() {
        return lastAllowed;
    }

    public void reset() {
        excess.clear();
        lastOver = 0;
        previousMove = 0;
        previousFriction = 0;
    }
}
