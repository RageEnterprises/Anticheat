package dev.rage.anticheat;

import dev.rage.anticheat.logic.ClickStats;
import dev.rage.anticheat.logic.EventWindow;
import dev.rage.anticheat.logic.SpeedTracker;
import dev.rage.anticheat.logic.TargetTracker;
import dev.rage.anticheat.logic.TimerBalance;
import dev.rage.anticheat.logic.ViolationCounter;
import dev.rage.anticheat.logic.XrayTracker;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;

/** Mutable per-player movement state. Only touched on the main thread. */
final class PlayerData {

    final ViolationCounter violations = new ViolationCounter();
    final SpeedTracker speed;
    final TimerBalance timer = new TimerBalance();
    final XrayTracker xray = new XrayTracker();
    /** Scratch box reused by collision tests so the hot path does not allocate one. */
    final BoundingBox scratch = new BoundingBox();

    double lastDy;
    boolean wasGrounded = true;
    double lastMovementSpeed;
    int lastMoveTick;
    int airborneTicks;
    int jesusTicks;
    int noFallTicks;
    /** Server tick until which every movement check is skipped (knockback, elytra, vehicles, ...). */
    int exemptUntil;
    /** Server tick until which the vertical checks are skipped (water, ladders, levitation, bounces). */
    int flightGraceUntil;

    private World safeWorld;
    private double safeX;
    private double safeY;
    private double safeZ;

    /** Blocks broken recently, for Nuker. */
    final EventWindow breaks;
    int breakTick = -1;
    int breaksThisTick;

    /** The block being mined, for FastBreak. digNanos is 0 when no block is being mined. */
    long digNanos;
    int digX;
    int digY;
    int digZ;
    double digSpeed;

    PlayerData(Settings s) {
        speed = new SpeedTracker(s.speedSamples());
        breaks = new EventWindow(s.nuker().maxPerSecond() + 1);
        places = new EventWindow(s.scaffold().maxPerSecond() + 1);
        placeMisses = new EventWindow(s.scaffold().maxMisses() + 1);
        attackMisses = new EventWindow(s.killAura().maxMisses() + 1);
        attackTargets = new TargetTracker(s.killAura().maxTargets() + 1);
        crystalBreaks = new EventWindow(s.crystal().maxBreaksPerSecond() + 1);
        crystalPlaces = new EventWindow(s.crystal().maxPlacesPerSecond() + 1);
        clicks = new ClickStats(Math.max(s.autoClicker().samples(), s.autoClicker().maxCps()) + 2);
    }

    /** Recent arm swings that did not hit a block, for AutoClicker. */
    final ClickStats clicks;
    long lastDropNanos = Long.MIN_VALUE / 2;

    /** End crystals broken and placed recently, for CrystalAura. */
    final EventWindow crystalBreaks;
    final EventWindow crystalPlaces;

    /** When the last totem of undying popped, for AutoTotem. */
    long totemPopNanos = Long.MIN_VALUE / 2;

    /** Hits outside the crosshair, and recent attack targets, for KillAura. */
    final EventWindow attackMisses;
    final TargetTracker attackTargets;

    /** Blocks placed recently, and placements against a face the player was not looking at, for Scaffold. */
    final EventWindow places;
    final EventWindow placeMisses;

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

    /** Forget movement history, e.g. after a teleport or setback. Violations and timer balance are kept. */
    void resetMovement() {
        lastDy = 0;
        wasGrounded = true;
        airborneTicks = 0;
        jesusTicks = 0;
        noFallTicks = 0;
        speed.reset();
    }
}
