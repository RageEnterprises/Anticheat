package dev.rage.anticheat.moderation;

import dev.rage.anticheat.RageAnticheat;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Locale;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/** Keeps muted players out of chat and out of private-message commands. */
public final class ChatListener implements Listener {

    private final RageAnticheat plugin;
    private final Moderation moderation;

    public ChatListener(RageAnticheat plugin, Moderation moderation) {
        this.plugin = plugin;
        this.moderation = moderation;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (blockMuted(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String label = event.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        label = label.substring(label.indexOf(':') + 1); // minecraft:msg -> msg
        if (plugin.getConfig().getStringList("moderation.muted-blocked-commands").contains(label)
                && blockMuted(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    private boolean blockMuted(Player player) {
        Punishment mute = moderation.mute(player.getUniqueId());
        if (mute == null) {
            return false;
        }
        player.sendMessage(plugin.messages().get("muted", moderation.placeholders(mute)));
        return true;
    }
}
