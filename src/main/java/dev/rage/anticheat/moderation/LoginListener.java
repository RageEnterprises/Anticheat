package dev.rage.anticheat.moderation;

import dev.rage.anticheat.RageAnticheat;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Login checks (bans) and join tracking (name, first/last seen, IP history) for profiles and alts.
 * The pre-login event runs on its own thread, so it can wait for the database.
 */
public final class LoginListener implements Listener {

    private final RageAnticheat plugin;
    private final Moderation moderation;
    private final Alts alts;

    public LoginListener(RageAnticheat plugin, Moderation moderation, Alts alts) {
        this.plugin = plugin;
        this.moderation = moderation;
        this.alts = alts;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        UUID uuid = event.getUniqueId();
        long now = System.currentTimeMillis();
        try {
            Punishment ban = plugin.database().call(c -> PunishmentStore.active(c, uuid, Punishment.Type.BAN, now));
            if (ban != null) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, moderation.banScreen(ban));
                return;
            }
            if (blockedVpn(event)) {
                return;
            }
            if (plugin.getConfig().getBoolean("alts.enabled", true)
                    && plugin.getConfig().getBoolean("alts.block-banned-alts", false)) {
                PlayerRecord banned = alts.bannedAccountOnIp(event.getAddress().getHostAddress(), uuid);
                if (banned != null) {
                    String[] placeholders = {"player", event.getName(), "alts", banned.name()};
                    event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                            plugin.messages().screen("alt-banned-screen", placeholders));
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                            plugin.broadcastStaff(plugin.messages().get("alt-banned-alert", placeholders)));
                    return;
                }
            }
            moderation.cacheMute(uuid,
                    plugin.database().call(c -> PunishmentStore.active(c, uuid, Punishment.Type.MUTE, now)));
        } catch (RuntimeException e) {
            // Keep the server joinable if the database fails; the error is already logged.
            plugin.getLogger().log(Level.WARNING, "Could not check bans for " + event.getName(), e);
        }
    }

    /** Refuses VPN and proxy connections when anti-vpn is enabled. Returns true if the join was refused. */
    private boolean blockedVpn(AsyncPlayerPreLoginEvent event) {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("anti-vpn");
        if (c == null || !c.getBoolean("enabled", false)) {
            return false;
        }
        for (String allowed : c.getStringList("allowed-players")) {
            if (allowed.equalsIgnoreCase(event.getName()) || allowed.equalsIgnoreCase(event.getUniqueId().toString())) {
                return false;
            }
        }
        String[] placeholders = {"player", event.getName()};
        try {
            if (!plugin.vpnCheck().isVpn(event.getAddress())) {
                return false;
            }
        } catch (VpnCheck.LookupException e) {
            plugin.getLogger().warning("VPN check for " + event.getName() + " failed: " + e.getMessage());
            if (c.getBoolean("allow-on-error", true)) {
                return false;
            }
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    plugin.messages().screen("vpn-error-screen", placeholders));
            return true;
        }
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, plugin.messages().screen("vpn-screen", placeholders));
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            plugin.broadcastStaff(plugin.messages().get("vpn-alert", placeholders));
            plugin.notifyDiscord("joins", plugin.messages().plain("discord-vpn-title", placeholders),
                    plugin.messages().plain("vpn-alert", placeholders), RageAnticheat.COLOR_INFO);
        });
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String ip = ip(player);
        long now = System.currentTimeMillis();
        plugin.database().submit(c -> PlayerStore.recordJoin(c, player.getUniqueId(), player.getName(), ip, now));
        if (plugin.getConfig().getBoolean("alts.enabled", true)) {
            alts.notifyStaff(player); // queued after the join record, so this IP is included
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        moderation.forgetMute(event.getPlayer().getUniqueId());
    }

    static String ip(Player player) {
        InetSocketAddress address = player.getAddress();
        return address == null || address.getAddress() == null ? "unknown" : address.getAddress().getHostAddress();
    }
}
