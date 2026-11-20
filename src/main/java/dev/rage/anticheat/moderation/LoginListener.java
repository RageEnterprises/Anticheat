package dev.rage.anticheat.moderation;

import dev.rage.anticheat.RageAnticheat;
import java.net.InetSocketAddress;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Records every join (name, first/last seen, IP history) for profiles and alt detection. */
public final class LoginListener implements Listener {

    private final RageAnticheat plugin;

    public LoginListener(RageAnticheat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String ip = ip(player);
        long now = System.currentTimeMillis();
        plugin.database().submit(c -> PlayerStore.recordJoin(c, player.getUniqueId(), player.getName(), ip, now));
    }

    static String ip(Player player) {
        InetSocketAddress address = player.getAddress();
        return address == null || address.getAddress() == null ? "unknown" : address.getAddress().getHostAddress();
    }
}
