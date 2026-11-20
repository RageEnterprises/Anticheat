package dev.rage.anticheat;

import dev.rage.anticheat.logic.FlightLimits;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/** Immutable snapshot of config.yml. Rebuilt on /rageanticheat reload. */
record Settings(
        boolean flightEnabled,
        boolean spiderEnabled,
        FlightLimits flight,
        boolean speedEnabled,
        int speedSamples,
        double speedTolerance,
        double speedMaxExcess,
        boolean jesusEnabled,
        int jesusMaxTicks,
        boolean stepEnabled,
        double stepMaxHeight,
        boolean noFallEnabled,
        int noFallMaxTicks,
        boolean timerEnabled,
        double timerMaxAheadMs,
        double timerMaxCreditMs,
        boolean reachEnabled,
        double reachMaxDistance,
        int knockbackGraceTicks,
        int boostGraceTicks,
        double kickThreshold,
        double decayPerSecond,
        boolean kickEnabled,
        boolean alertsEnabled,
        Nuker nuker,
        Scaffold scaffold,
        KillAura killAura,
        Xray xray) {

    /** Breaking too many blocks, too far away, or outside the player's view. */
    record Nuker(boolean enabled, int maxPerTick, int maxPerSecond, double maxDistance, double maxAngle) {
        static Nuker from(ConfigurationSection c) {
            return new Nuker(c.getBoolean("enabled", true),
                    Math.max(1, c.getInt("max-per-tick", 3)),
                    Math.max(1, c.getInt("max-per-second", 22)),
                    c.getDouble("max-distance", 6.0),
                    c.getDouble("max-angle", 75));
        }
    }

    /** Placing blocks against a face the player is not looking at, or too quickly. */
    record Scaffold(boolean enabled, int maxMisses, int missWindowTicks, int maxPerSecond) {
        static Scaffold from(ConfigurationSection c) {
            return new Scaffold(c.getBoolean("enabled", true),
                    Math.max(1, c.getInt("max-misses", 3)),
                    Math.max(1, c.getInt("miss-window-ticks", 60)),
                    Math.max(1, c.getInt("max-per-second", 20)));
        }
    }

    /** Hitting targets outside the crosshair, or many different targets in a short time. */
    record KillAura(boolean enabled, double hitboxExpand, int maxMisses, int missWindowTicks,
                    int maxTargets, int switchWindowTicks) {
        static KillAura from(ConfigurationSection c) {
            return new KillAura(c.getBoolean("enabled", true),
                    Math.max(0, c.getDouble("hitbox-expand", 0.5)),
                    Math.max(1, c.getInt("max-misses", 4)),
                    Math.max(1, c.getInt("miss-window-ticks", 100)),
                    Math.max(1, c.getInt("max-targets", 3)),
                    Math.max(1, c.getInt("switch-window-ticks", 10)));
        }
    }

    /** Mining far more hidden ores per stone than a normal player. */
    record Xray(boolean enabled, boolean punish, int minOres, double maxOresPerStone,
                Set<Material> ores, Set<Material> stone) {
        static Xray from(ConfigurationSection c, Logger logger) {
            return new Xray(c.getBoolean("enabled", true),
                    c.getBoolean("punish", false),
                    Math.max(1, c.getInt("min-ores", 4)),
                    Math.max(0, c.getDouble("max-ores-per-stone", 0.02)),
                    materials(c.getStringList("ores"), logger),
                    materials(c.getStringList("stone"), logger));
        }
    }

    private static Set<Material> materials(List<String> names, Logger logger) {
        Set<Material> set = new HashSet<>();
        for (String name : names) {
            Material m = Material.matchMaterial(name);
            if (m == null) {
                logger.warning("Unknown block in config: " + name);
            } else {
                set.add(m);
            }
        }
        return set;
    }

    private static ConfigurationSection section(ConfigurationSection c, String path) {
        ConfigurationSection section = c.getConfigurationSection(path);
        return section != null ? section : c.createSection(path);
    }

    static Settings from(ConfigurationSection c, Logger logger) {
        return new Settings(
                c.getBoolean("checks.flight.enabled", true),
                c.getBoolean("checks.spider.enabled", true),
                new FlightLimits(
                        Math.max(1, c.getInt("checks.flight.max-airborne-ticks", 40)),
                        Math.max(0, c.getDouble("checks.flight.min-fall-speed", 0.5)),
                        Math.max(0, c.getDouble("checks.flight.gravity-tolerance", 0.05))),
                c.getBoolean("checks.speed.enabled", true),
                Math.max(1, c.getInt("checks.speed.samples", 10)),
                Math.max(0, c.getDouble("checks.speed.tolerance", 0.01)),
                Math.max(0, c.getDouble("checks.speed.max-excess", 0.1)),
                c.getBoolean("checks.jesus.enabled", true),
                Math.max(1, c.getInt("checks.jesus.max-ticks", 10)),
                c.getBoolean("checks.step.enabled", true),
                Math.max(0, c.getDouble("checks.step.max-height", 0.6)),
                c.getBoolean("checks.nofall.enabled", true),
                Math.max(1, c.getInt("checks.nofall.max-ticks", 3)),
                c.getBoolean("checks.timer.enabled", true),
                Math.max(50, c.getDouble("checks.timer.max-ahead-ms", 300)),
                Math.max(0, c.getDouble("checks.timer.max-lag-credit-ms", 1000)),
                c.getBoolean("checks.reach.enabled", true),
                Math.max(0, c.getDouble("checks.reach.max-distance", 3.5)),
                Math.max(0, c.getInt("grace.knockback-ticks", 30)),
                Math.max(0, c.getInt("grace.boost-ticks", 20)),
                Math.max(1, c.getDouble("violations.kick-threshold", 20)),
                Math.max(0, c.getDouble("violations.decay-per-second", 0.5)),
                c.getBoolean("violations.kick-enabled", true),
                c.getBoolean("alerts.enabled", true),
                Nuker.from(section(c, "checks.nuker")),
                Scaffold.from(section(c, "checks.scaffold")),
                KillAura.from(section(c, "checks.killaura")),
                Xray.from(section(c, "checks.xray"), logger));
    }

    /** Check name to enabled state, in display order. */
    Map<String, Boolean> checks() {
        Map<String, Boolean> checks = new LinkedHashMap<>();
        checks.put("Flight", flightEnabled);
        checks.put("Spider", spiderEnabled);
        checks.put("Speed", speedEnabled);
        checks.put("Jesus", jesusEnabled);
        checks.put("Step", stepEnabled);
        checks.put("NoFall", noFallEnabled);
        checks.put("Timer", timerEnabled);
        checks.put("Reach", reachEnabled);
        checks.put("Nuker", nuker.enabled());
        checks.put("Scaffold", scaffold.enabled());
        checks.put("KillAura", killAura.enabled());
        checks.put("Xray", xray.enabled());
        return checks;
    }
}
