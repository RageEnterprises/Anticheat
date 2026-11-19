package dev.rage.anticheat;

import dev.rage.anticheat.logic.FlightLimits;
import dev.rage.anticheat.logic.SpeedLimits;
import org.bukkit.configuration.ConfigurationSection;

/** Immutable snapshot of config.yml. Rebuilt on /rageanticheat reload. */
record Settings(
        boolean flightEnabled,
        FlightLimits flight,
        boolean speedEnabled,
        int speedSamples,
        SpeedLimits speed,
        int knockbackGraceTicks,
        int boostGraceTicks,
        double kickThreshold,
        double decayPerSecond,
        boolean kickEnabled,
        boolean alertsEnabled) {

    static Settings from(ConfigurationSection c) {
        return new Settings(
                c.getBoolean("checks.flight.enabled", true),
                new FlightLimits(
                        Math.max(1, c.getInt("checks.flight.max-airborne-ticks", 40)),
                        Math.max(0, c.getDouble("checks.flight.min-fall-speed", 0.5)),
                        Math.max(0, c.getDouble("checks.flight.gravity-tolerance", 0.05))),
                c.getBoolean("checks.speed.enabled", true),
                Math.max(1, c.getInt("checks.speed.samples", 10)),
                new SpeedLimits(
                        c.getDouble("checks.speed.max.walk", 0.30),
                        c.getDouble("checks.speed.max.sprint", 0.45),
                        c.getDouble("checks.speed.max.sneak", 0.20),
                        c.getDouble("checks.speed.max.swim", 0.35),
                        Math.max(0, c.getDouble("checks.speed.speed-potion-bonus", 0.20))),
                Math.max(0, c.getInt("grace.knockback-ticks", 30)),
                Math.max(0, c.getInt("grace.boost-ticks", 20)),
                Math.max(1, c.getDouble("violations.kick-threshold", 20)),
                Math.max(0, c.getDouble("violations.decay-per-second", 0.5)),
                c.getBoolean("violations.kick-enabled", true),
                c.getBoolean("alerts.enabled", true));
    }
}
