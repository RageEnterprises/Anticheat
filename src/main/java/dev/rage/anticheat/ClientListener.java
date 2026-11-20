package dev.rage.anticheat;

import dev.rage.anticheat.logic.Durations;
import dev.rage.anticheat.logic.ModBlocklist;
import dev.rage.anticheat.moderation.Moderation;
import dev.rage.anticheat.moderation.Punishment;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;

/**
 * Refuses clients that announce a blocked mod: through their client brand, or through the plugin
 * channels their mods register. Modern clients send both while connecting, before the join.
 * Cheat clients can hide or fake both, so this only stops clients that identify themselves.
 *
 * <p>Spoofing is caught where it leaves gaps: a "vanilla" brand with channels only mods register
 * (brand changers leave the mod loader's channels), or a brand that changes after joining.
 */
final class ClientListener implements Listener {

    static final String BYPASS = "rageanticheat.mods.bypass";
    /** Some clients send their brand just after joining, so check again a moment later. */
    private static final long RECHECK_TICKS = 40;
    /** Shown as the mod when a client hides what it runs. */
    private static final String SPOOFED = "hidden mods (spoofed client brand)";

    private final RageAnticheat plugin;
    /** Players already reported this session, so several blocked channels give one alert. */
    private final Set<UUID> reported = ConcurrentHashMap.newKeySet();
    /** The first brand each player sent, to catch it changing. */
    private final Map<UUID, String> brands = new ConcurrentHashMap<>();

    ClientListener(RageAnticheat plugin) {
        this.plugin = plugin;
    }

    private ConfigurationSection config() {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("client-mods");
        return c != null ? c : plugin.getConfig().createSection("client-mods");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (check(player)) {
            event.joinMessage(null);
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                check(player);
            }
        }, RECHECK_TICKS);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChannel(PlayerRegisterChannelEvent event) {
        Player player = event.getPlayer();
        ConfigurationSection c = config();
        if (!c.getBoolean("enabled", true) || player.hasPermission(BYPASS)) {
            return;
        }
        String mod = new ModBlocklist(c.getStringList("blocked")).match(event.getChannel());
        if (mod != null) {
            refuse(player, mod, "channel " + event.getChannel());
        } else if (c.getBoolean("detect-spoofing", true) && "vanilla".equalsIgnoreCase(player.getClientBrandName())
                && ModBlocklist.moddedChannel(List.of(event.getChannel())) != null) {
            refuse(player, SPOOFED, "brand vanilla, but registers channel " + event.getChannel());
        }
    }

    /** Checks the brand and channels; returns true if the player was refused. */
    private boolean check(Player player) {
        ConfigurationSection c = config();
        if (!c.getBoolean("enabled", true) || player.hasPermission(BYPASS)) {
            return false;
        }
        String brand = player.getClientBrandName();
        String spoof = c.getBoolean("detect-spoofing", true) ? spoofed(player, brand) : null;
        if (spoof != null) {
            refuse(player, SPOOFED, spoof);
            return true;
        }
        if (c.getBoolean("vanilla-only", false) && brand != null && !brand.equalsIgnoreCase("vanilla")) {
            refuse(player, brand, "modified client (brand " + brand + ")");
            return true;
        }
        ModBlocklist blocked = new ModBlocklist(c.getStringList("blocked"));
        String mod = blocked.match(brand, player.getListeningPluginChannels());
        if (mod == null) {
            return false;
        }
        String found = blocked.match(brand) != null ? "brand " + brand : "a plugin channel";
        refuse(player, mod, found);
        return true;
    }

    /** How the client gave away a fake brand, or null. */
    private String spoofed(Player player, String brand) {
        if (brand == null) {
            return null;
        }
        String first = brands.putIfAbsent(player.getUniqueId(), brand);
        if (first != null && !first.equals(brand)) {
            return "brand changed from " + first + " to " + brand + " after joining";
        }
        String channel = ModBlocklist.moddedChannel(player.getListeningPluginChannels());
        if (brand.equalsIgnoreCase("vanilla") && channel != null) {
            return "brand vanilla, but registers channel " + channel;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        reported.remove(event.getPlayer().getUniqueId());
        brands.remove(event.getPlayer().getUniqueId());
    }

    private void refuse(Player player, String mod, String source) {
        if (!reported.add(player.getUniqueId())) {
            return;
        }
        ConfigurationSection c = config();
        String details = mod + " (" + source + ")";
        String[] placeholders = {"player", player.getName(), "mod", mod, "details", details};
        plugin.getLogger().info(player.getName() + " uses a blocked client mod: " + details);
        plugin.broadcastStaff(plugin.messages().get("mod-alert", placeholders));
        plugin.notifyDiscord("joins", plugin.messages().plain("discord-mod-title", placeholders),
                plugin.messages().plain("mod-alert", placeholders), RageAnticheat.COLOR_ALERT);

        String action = c.getString("action", "kick").toLowerCase(Locale.ROOT);
        if (action.equals("none")) {
            return;
        }
        String reason = plugin.messages().plain("mod-reason", placeholders);
        Moderation.Target target = new Moderation.Target(player.getUniqueId(), player.getName());
        long duration = Durations.parse(c.getString("ban-duration", "perm"));
        // Next tick: the player may still be in the middle of joining.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (action.equals("ban")) {
                plugin.moderation().punish(Punishment.Type.BAN, target, "RageAnticheat", reason,
                        duration == Durations.INVALID ? Durations.PERMANENT : duration);
            } else {
                // A plain kick, not recorded as a punishment: the player did nothing wrong yet.
                player.kick(plugin.messages().screen("mod-screen", placeholders));
            }
        });
    }
}
