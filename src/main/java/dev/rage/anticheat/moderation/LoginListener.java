package dev.rage.anticheat.moderation;

import dev.rage.anticheat.RageAnticheat;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.logging.Level;
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

    public LoginListener(RageAnticheat plugin, Moderation moderation) {
        this.plugin = plugin;
        this.moderation = moderation;
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
            moderation.cacheMute(uuid,
                    plugin.database().call(c -> PunishmentStore.active(c, uuid, Punishment.Type.MUTE, now)));
        } catch (RuntimeException e) {
            // Keep the server joinable if the database fails; the error is already logged.
            plugin.getLogger().log(Level.WARNING, "Could not check bans for " + event.getName(), e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String ip = ip(player);
        long now = System.currentTimeMillis();
        plugin.database().submit(c -> PlayerStore.recordJoin(c, player.getUniqueId(), player.getName(), ip, now));
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
