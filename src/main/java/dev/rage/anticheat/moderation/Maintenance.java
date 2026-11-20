package dev.rage.anticheat.moderation;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import dev.rage.anticheat.RageAnticheat;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

/**
 * Maintenance mode: only server operators and players on the maintenance whitelist can join, and
 * the server list shows "Maintenance" in place of the ping bars. State is kept in maintenance.yml
 * so it survives restarts.
 */
public final class Maintenance implements Listener, CommandExecutor, TabCompleter {

    public static final String PERMISSION = "rageanticheat.maintenance";
    /** A protocol version no client has, so the client shows the version text instead of ping bars. */
    private static final int NO_PROTOCOL = -1;

    private final RageAnticheat plugin;
    private final File file;
    private volatile boolean enabled;
    /** Lower-case names; read by the async login thread. */
    private final Set<String> whitelist = ConcurrentHashMap.newKeySet();

    public Maintenance(RageAnticheat plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "maintenance.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        enabled = yaml.getBoolean("enabled", false);
        for (String name : yaml.getStringList("whitelist")) {
            whitelist.add(name.toLowerCase(Locale.ROOT));
        }
    }

    public boolean enabled() {
        return enabled;
    }

    /** Turns maintenance on or off. Turning it on kicks online players who may not stay. */
    public void set(boolean on, String staff) {
        enabled = on;
        save();
        String[] placeholders = {"staff", staff};
        if (on) {
            for (Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
                if (!allowed(player.getUniqueId(), player.getName())) {
                    player.kick(plugin.messages().screen("maintenance-screen", placeholders));
                }
            }
        }
        String key = on ? "maintenance-on" : "maintenance-off";
        plugin.broadcastStaff(plugin.messages().get(key, placeholders));
        plugin.notifyDiscord("joins", plugin.messages().plain("discord-maintenance-title"),
                plugin.messages().plain(key, placeholders), RageAnticheat.COLOR_INFO);
    }

    /** Ops and whitelisted names may join during maintenance. Safe from any thread. */
    private boolean allowed(UUID uuid, String name) {
        if (whitelist.contains(name.toLowerCase(Locale.ROOT))) {
            return true;
        }
        for (OfflinePlayer op : Bukkit.getOperators()) {
            if (uuid.equals(op.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (enabled && event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED
                && !allowed(event.getUniqueId(), event.getName())) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST,
                    plugin.messages().screen("maintenance-screen", "staff", ""));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPing(PaperServerListPingEvent event) {
        if (!enabled) {
            return;
        }
        // The version text only supports the 16 legacy colors; hex colors are rounded to the nearest one.
        event.setVersion(LegacyComponentSerializer.legacySection().serialize(
                plugin.messages().screen("maintenance-version")));
        event.setProtocolVersion(NO_PROTOCOL);
        event.motd(plugin.messages().screen("maintenance-motd"));
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("enabled", enabled);
        List<String> names = new ArrayList<>(whitelist);
        names.sort(null);
        yaml.set("whitelist", names);
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save maintenance.yml: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(plugin.messages().get("no-permission"));
            return true;
        }
        String staff = sender.getName();
        String action = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "on", "off" -> {
                boolean on = action.equals("on");
                if (on == enabled) {
                    sender.sendMessage(plugin.messages().get(on ? "maintenance-already-on" : "maintenance-already-off"));
                } else {
                    set(on, staff);
                }
            }
            case "add", "remove" -> {
                if (args.length < 2) {
                    sender.sendMessage(plugin.messages().get("usage-maintenance"));
                    return true;
                }
                String name = args[1];
                boolean changed = action.equals("add")
                        ? whitelist.add(name.toLowerCase(Locale.ROOT))
                        : whitelist.remove(name.toLowerCase(Locale.ROOT));
                save();
                sender.sendMessage(plugin.messages().get(action.equals("add")
                        ? (changed ? "maintenance-added" : "maintenance-already-added")
                        : (changed ? "maintenance-removed" : "maintenance-not-listed"), "player", name));
            }
            case "list" -> {
                List<String> names = new ArrayList<>(whitelist);
                names.sort(null);
                sender.sendMessage(plugin.messages().get("maintenance-list",
                        "status", enabled ? "on" : "off", "count", String.valueOf(names.size()),
                        "players", names.isEmpty() ? "-" : String.join(", ", names)));
            }
            default -> sender.sendMessage(plugin.messages().get("usage-maintenance"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("on", "off", "add", "remove", "list"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                options.add(player.getName());
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            options.addAll(whitelist);
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(prefix));
        return options;
    }
}
