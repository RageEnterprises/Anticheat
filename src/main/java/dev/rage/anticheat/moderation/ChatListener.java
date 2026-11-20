package dev.rage.anticheat.moderation;

import dev.rage.anticheat.RageAnticheat;
import dev.rage.anticheat.logic.ChatSpam;
import dev.rage.anticheat.logic.Durations;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Keeps muted players out of chat and out of private-message commands, and blocks chat spam
 * (too many messages, or the same message again), muting players who keep spamming.
 */
public final class ChatListener implements Listener {

    public static final String SPAM_BYPASS = "rageanticheat.chat.bypass";
    private static final long DEFAULT_MUTE_MS = 10 * 60_000L;

    private final RageAnticheat plugin;
    private final Moderation moderation;
    /** Written from the async chat threads and the server thread. */
    private final Map<UUID, ChatSpam> history = new ConcurrentHashMap<>();

    public ChatListener(RageAnticheat plugin, Moderation moderation) {
        this.plugin = plugin;
        this.moderation = moderation;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (blockMuted(player)
                || blockSpam(player, PlainTextComponentSerializer.plainText().serialize(event.message()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String label = event.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        label = label.substring(label.indexOf(':') + 1); // minecraft:msg -> msg
        if (!plugin.getConfig().getStringList("moderation.muted-blocked-commands").contains(label)) {
            return;
        }
        // Private messages count as chat: blocked while muted, and limited like chat.
        if (blockMuted(event.getPlayer()) || blockSpam(event.getPlayer(), event.getMessage())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        history.remove(event.getPlayer().getUniqueId());
    }

    private boolean blockMuted(Player player) {
        Punishment mute = moderation.mute(player.getUniqueId());
        if (mute == null) {
            return false;
        }
        player.sendMessage(plugin.messages().get("muted", moderation.placeholders(mute)));
        return true;
    }

    /** Safe from any thread. Returns true if the message is spam and must be blocked. */
    private boolean blockSpam(Player player, String message) {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("chat.anti-spam");
        if (c == null || !c.getBoolean("enabled", true) || player.hasPermission(SPAM_BYPASS)) {
            return false;
        }
        long now = System.currentTimeMillis();
        ChatSpam spam = history.computeIfAbsent(player.getUniqueId(), id -> new ChatSpam());
        ChatSpam.Verdict verdict = spam.check(message, now, c.getInt("max-messages", 5),
                Math.max(1, c.getLong("window-seconds", 5)) * 1000L,
                Math.max(0, c.getLong("repeat-seconds", 30)) * 1000L);
        if (verdict == ChatSpam.Verdict.OK) {
            return false;
        }
        player.sendMessage(plugin.messages().get(
                verdict == ChatSpam.Verdict.REPEATED ? "chat-repeated" : "chat-too-fast"));

        ConfigurationSection mute = c.getConfigurationSection("auto-mute");
        if (mute == null || !mute.getBoolean("enabled", true)) {
            return true;
        }
        int strikes = spam.strike(now, Math.max(1, mute.getLong("window-seconds", 60)) * 1000L);
        if (strikes >= Math.max(1, mute.getInt("strikes", 5))) {
            spam.clearStrikes();
            long duration = Durations.parse(mute.getString("duration", "10m"));
            String reason = plugin.messages().plain("auto-mute-reason");
            Moderation.Target target = new Moderation.Target(player.getUniqueId(), player.getName());
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (moderation.mute(target.uuid()) == null) {
                    moderation.punish(Punishment.Type.MUTE, target, "RageAnticheat", reason,
                            duration == Durations.INVALID ? DEFAULT_MUTE_MS : duration);
                }
            });
        }
        return true;
    }
}
