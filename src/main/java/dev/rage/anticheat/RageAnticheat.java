package dev.rage.anticheat;

import dev.rage.anticheat.discord.DiscordWebhook;
import dev.rage.anticheat.moderation.Database;
import dev.rage.anticheat.logic.Durations;
import dev.rage.anticheat.moderation.Alts;
import dev.rage.anticheat.moderation.ChatListener;
import dev.rage.anticheat.moderation.Freeze;
import dev.rage.anticheat.moderation.LoginListener;
import dev.rage.anticheat.moderation.Moderation;
import dev.rage.anticheat.moderation.ModerationCommand;
import dev.rage.anticheat.moderation.Profiles;
import dev.rage.anticheat.moderation.Punishment;
import dev.rage.anticheat.moderation.VpnCheck;
import dev.rage.anticheat.moderation.PlayerStore;
import java.io.File;
import java.sql.SQLException;
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
    private Database database;
    private Moderation moderation;
    private Freeze freeze;
    private Profiles profiles;
    private VpnCheck vpnCheck;
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
        try {
            database = Database.open(new File(getDataFolder(), "data.db"), getLogger());
        } catch (SQLException e) {
            // Bans and mutes must not silently stop working, so refuse to run without storage.
            getLogger().severe("Could not open the database, disabling RageAnticheat: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        reload();
        moderation = new Moderation(this);
        Alts alts = new Alts(this);
        profiles = new Profiles(this);
        PluginCommand profileCommand = getCommand("profile");
        if (profileCommand != null) {
            profileCommand.setExecutor(profiles);
            profileCommand.setTabCompleter(profiles);
        }
        PluginCommand altsCommand = getCommand("alts");
        if (altsCommand != null) {
            altsCommand.setExecutor(alts);
            altsCommand.setTabCompleter(alts);
        }
        getServer().getPluginManager().registerEvents(new LoginListener(this, moderation, alts), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this, moderation), this);
        freeze = new Freeze(this);
        getServer().getPluginManager().registerEvents(freeze, this);
        PluginCommand freezeCommand = getCommand("freeze");
        if (freezeCommand != null) {
            freezeCommand.setExecutor(freeze);
            freezeCommand.setTabCompleter(freeze);
        }
        ModerationCommand moderationCommand = new ModerationCommand(this, moderation);
        for (String name : ModerationCommand.COMMANDS) {
            PluginCommand modCommand = getCommand(name);
            if (modCommand != null) {
                modCommand.setExecutor(moderationCommand);
                modCommand.setTabCompleter(moderationCommand);
            }
        }
        getServer().getPluginManager().registerEvents(new MovementListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockListener(this), this);
        getServer().getPluginManager().registerEvents(new ExploitListener(this), this);

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
        if (database != null) {
            database.close();
        }
    }

    public Moderation moderation() {
        return moderation;
    }

    public Freeze freeze() {
        return freeze;
    }

    public Profiles profiles() {
        return profiles;
    }

    public VpnCheck vpnCheck() {
        return vpnCheck;
    }

    public Database database() {
        return database;
    }

    /** Re-reads config.yml and messages.yml. Player state is dropped because buffer sizes may change. */
    void reload() {
        reloadConfig();
        settings = Settings.from(getConfig(), getLogger());
        messages.load(this);
        players.clear();

        if (webhook != null) {
            webhook.close();
            webhook = null;
        }
        webhookEvents.clear();
        vpnCheck = new VpnCheck(getConfig().getString("anti-vpn.api-key", ""),
                Math.max(1, getConfig().getLong("anti-vpn.cache-hours", 24)) * 3_600_000L);
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

    public Messages messages() {
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
    /** Tells staff and Discord about a check result without adding a violation. */
    void alert(Player player, String check, String details, String count) {
        String[] placeholders = {"player", player.getName(), "check", check, "count", count, "details", details};
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
    }

    void flag(Player player, PlayerData d, String check, String details) {
        double level = d.violations.increment();
        int count = d.violations.count();
        getLogger().info(String.format("%s failed %s (%s) vl=%.1f", player.getName(), check, details, level));
        alert(player, check, details, String.valueOf(count));
        long now = System.currentTimeMillis();
        database.submit(c -> {
            PlayerStore.recordFlag(c, player.getUniqueId(), check, details, now);
            return null;
        });

        if (settings.kickEnabled() && d.violations.reached(settings.kickThreshold())) {
            d.violations.reset();
            if ("ban".equalsIgnoreCase(getConfig().getString("violations.action", "kick"))) {
                long duration = Durations.parse(getConfig().getString("violations.ban-duration", "7d"));
                String banReason = messages.plain("anticheat-ban-reason", "check", check);
                getServer().getScheduler().runTask(this, () -> moderation.punish(Punishment.Type.BAN,
                        new Moderation.Target(player.getUniqueId(), player.getName()), "RageAnticheat", banReason,
                        duration == Durations.INVALID ? Durations.PERMANENT : duration));
                return;
            }
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
