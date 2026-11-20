package dev.rage.anticheat;

import dev.rage.anticheat.discord.DiscordWebhook;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
    private DiscordWebhook webhook;
    private final Set<String> webhookEvents = new HashSet<>();

    /** Embed colors for Discord, matching the red theme. */
    public static final int COLOR_ALERT = 0xFF2A2A;
    public static final int COLOR_PUNISH = 0x7A0000;
    public static final int COLOR_INFO = 0xFF8A8A;

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
        getServer().getPluginManager().registerEvents(new BlockListener(this), this);

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
        if (webhook != null) {
            webhook.close();
        }
    }

    /** Re-reads config.yml and messages.yml. Player state is dropped because buffer sizes may change. */
    void reload() {
        reloadConfig();
        settings = Settings.from(getConfig());
        messages.load(this);
        players.clear();

        if (webhook != null) {
            webhook.close();
            webhook = null;
        }
        webhookEvents.clear();
        if (getConfig().getBoolean("discord.webhook.enabled")) {
            webhook = DiscordWebhook.create(getConfig().getString("discord.webhook.url"),
                    getConfig().getString("discord.webhook.username", "RageAnticheat"),
                    getConfig().getInt("discord.webhook.max-per-minute", 20), getLogger());
            webhookEvents.addAll(getConfig().getStringList("discord.webhook.events"));
        }
    }

    /**
     * Posts to Discord if the webhook is enabled for {@code event}.
     *
     * @param event one of alerts, punishments, joins, exploits
     */
    public void notifyDiscord(String event, String title, String description, int color) {
        if (webhook != null && webhookEvents.contains(event)) {
            webhook.send(title, description, color);
        }
    }

    /** Sends {@code message} to every online player with rageanticheat.alerts, and to the console. */
    public void broadcastStaff(Component message) {
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(ALERTS)) {
                staff.sendMessage(message);
            }
        }
        Bukkit.getConsoleSender().sendMessage(message);
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
            d = new PlayerData(settings);
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

        String[] placeholders = {"player", player.getName(), "check", check, "count", String.valueOf(count),
                "details", details};
        if (settings.alertsEnabled()) {
            Component alert = messages.get("alert", placeholders);
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission(ALERTS)) {
                    staff.sendMessage(alert);
                }
            }
        }
        notifyDiscord("alerts", messages.plain("discord-alert-title", placeholders),
                messages.plain("discord-alert", placeholders), COLOR_ALERT);

        if (settings.kickEnabled() && d.violations.reached(settings.kickThreshold())) {
            d.violations.reset();
            Component reason = messages.get("kick", "player", player.getName(), "check", check, "count", String.valueOf(count));
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
