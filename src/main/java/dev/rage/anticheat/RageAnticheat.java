package dev.rage.anticheat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class RageAnticheat extends JavaPlugin {

    static final String ALERTS = "rageanticheat.alerts";

    private final Map<UUID, PlayerData> players = new HashMap<>();
    private final Messages messages = new Messages();
    private Settings settings;

    private static final long LAG_TICK_NANOS = 100_000_000L;
    private static final long LAG_RECOVERY_NANOS = 2_000_000_000L;
    private long lastTickNanos;
    private long lastLagNanos = Long.MIN_VALUE / 2;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reload();
        getServer().getPluginManager().registerEvents(new MovementListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);

        // Server lag detection for the Timer check: a slow tick delivers queued packets in a burst.
        lastTickNanos = System.nanoTime();
        getServer().getScheduler().runTaskTimer(this, () -> {
            long now = System.nanoTime();
            if (now - lastTickNanos > LAG_TICK_NANOS) {
                lastLagNanos = now;
            }
            lastTickNanos = now;
        }, 1L, 1L);

        PluginCommand command = getCommand("rageanticheat");
        if (command != null) {
            RageCommand executor = new RageCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        // Violation decay, once per second.
        getServer().getScheduler().runTaskTimer(this, () -> {
            double decay = settings.decayPerSecond();
            for (PlayerData d : players.values()) {
                d.violations.decay(decay);
            }
        }, 20L, 20L);
    }

    @Override
    public void onDisable() {
        players.clear();
    }

    /** Re-reads config.yml and messages.yml. Player state is dropped because buffer sizes may change. */
    void reload() {
        reloadConfig();
        settings = Settings.from(getConfig());
        messages.load(this);
        players.clear();
    }

    Settings settings() {
        return settings;
    }

    Messages messages() {
        return messages;
    }

    /** True while the current tick runs long, or within 2 seconds after a slow tick. */
    boolean serverLagging(long nowNanos) {
        return nowNanos - lastTickNanos > LAG_TICK_NANOS || nowNanos - lastLagNanos < LAG_RECOVERY_NANOS;
    }

    Map<UUID, PlayerData> players() {
        return players;
    }

    PlayerData data(Player player) {
        PlayerData d = players.get(player.getUniqueId());
        if (d == null) {
            d = new PlayerData(settings.speedSamples());
            players.put(player.getUniqueId(), d);
        }
        return d;
    }

    void forget(Player player) {
        players.remove(player.getUniqueId());
    }

    /** Records a violation: log, alert staff, and kick once the limit is reached. The setback is already done. */
    void flag(Player player, PlayerData d, String check, String details) {
        double level = d.violations.increment();
        int count = d.violations.count();
        getLogger().info(String.format("%s failed %s (%s) vl=%.1f", player.getName(), check, details, level));

        if (settings.alertsEnabled()) {
            Component alert = messages.get("alert", player.getName(), check, count);
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission(ALERTS)) {
                    staff.sendMessage(alert);
                }
            }
        }

        if (settings.kickEnabled() && d.violations.reached(settings.kickThreshold())) {
            d.violations.reset();
            Component reason = messages.get("kick", player.getName(), check, count);
            getLogger().info(player.getName() + " kicked for " + check + " violations");
            // Kick on the next tick rather than in the middle of handling the move packet.
            getServer().getScheduler().runTask(this, () -> {
                if (player.isOnline()) {
                    player.kick(reason);
                }
            });
        }
    }
}
