package dev.rage.anticheat;

import dev.rage.anticheat.logic.RollingAverage;
import dev.rage.anticheat.logic.ViolationCounter;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;

/** Mutable per-player movement state. Only touched on the main thread. */
final class PlayerData {

    final ViolationCounter violations = new ViolationCounter();
    final RollingAverage distances;
    final RollingAverage limits;
    /** Scratch box reused by the ground check so the hot path does not allocate one. */
    final BoundingBox scratch = new BoundingBox();

    double lastDy;
    boolean wasGrounded = true;
    int airborneTicks;
    int flightGraceUntil;
    int speedGraceUntil;

    private World safeWorld;
    private double safeX;
    private double safeY;
    private double safeZ;

    PlayerData(int speedSamples) {
        distances = new RollingAverage(speedSamples);
        limits = new RollingAverage(speedSamples);
    }

    void setSafe(Location loc) {
        safeWorld = loc.getWorld();
        safeX = loc.getX();
        safeY = loc.getY();
        safeZ = loc.getZ();
    }

    /** Last valid location in {@code current}'s world, keeping its rotation; null if none is known. */
    Location safeLocation(Location current) {
        if (safeWorld == null || safeWorld != current.getWorld()) {
            return null;
        }
        return new Location(safeWorld, safeX, safeY, safeZ, current.getYaw(), current.getPitch());
    }

    /** Forget movement history, e.g. after a teleport or setback. Violations are kept. */
    void resetMovement() {
        lastDy = 0;
        wasGrounded = true;
        airborneTicks = 0;
        distances.clear();
        limits.clear();
    }
}
