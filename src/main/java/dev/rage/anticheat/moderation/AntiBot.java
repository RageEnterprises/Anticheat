package dev.rage.anticheat.moderation;

import dev.rage.anticheat.RageAnticheat;
import dev.rage.anticheat.logic.JoinFlood;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Anti-bot: limits accounts online per IP, and refuses first-time players during a join flood.
 * Runs before the ban and VPN checks, so a flood cannot use up the VPN lookup quota.
 */
public final class AntiBot implements Listener {

    private final RageAnticheat plugin;
    private final JoinFlood flood = new JoinFlood();
    /** Online players per IP, kept up to date on join and quit so login threads can read it. */
    private final Map<String, Integer> online = new ConcurrentHashMap<>();

    public AntiBot(RageAnticheat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("anti-bot");
        if (c == null || !c.getBoolean("enabled", true)
                || event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        String[] placeholders = {"player", event.getName()};
        String ip = event.getAddress().getHostAddress();
        int maxPerIp = c.getInt("max-accounts-per-ip", 3);
        if (maxPerIp > 0 && online.getOrDefault(ip, 0) >= maxPerIp) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    plugin.messages().screen("bot-ip-screen", placeholders));
            return;
        }

        boolean known;
        try {
            known = plugin.database().call(c2 -> PlayerStore.find(c2, event.getUniqueId())) != null;
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Anti-bot could not check " + event.getName(), e);
            return;
        }
        if (known) {
            return; // returning players are never held back by attack mode
        }
        JoinFlood.Decision decision = flood.newPlayer(System.currentTimeMillis(),
                Math.max(1, c.getInt("max-new-players", 8)),
                Math.max(1, c.getLong("window-seconds", 10)) * 1000L,
                Math.max(1, c.getLong("attack-mode-seconds", 60)) * 1000L);
        if (!decision.blocked()) {
            return;
        }
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                plugin.messages().screen("bot-attack-screen", placeholders));
        if (decision.attackStarted()) {
            plugin.getLogger().warning("Join flood detected: attack mode on, first-time players are refused.");
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.broadcastStaff(plugin.messages().get("bot-attack-alert"));
                plugin.notifyDiscord("joins", plugin.messages().plain("discord-bot-title"),
                        plugin.messages().plain("bot-attack-alert"), RageAnticheat.COLOR_ALERT);
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        online.merge(LoginListener.ip(event.getPlayer()), 1, Integer::sum);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        online.computeIfPresent(LoginListener.ip(event.getPlayer()), (ip, n) -> n > 1 ? n - 1 : null);
    }
}
