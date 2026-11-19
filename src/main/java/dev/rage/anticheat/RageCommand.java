package dev.rage.anticheat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/** /rageanticheat reload | status | reset &lt;player&gt; */
final class RageCommand implements TabExecutor {

    static final String ADMIN = "rageanticheat.admin";
    private static final List<String> SUBCOMMANDS = List.of("reload", "status", "reset");

    private final RageAnticheat plugin;

    RageCommand(RageAnticheat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages messages = plugin.messages();
        if (!sender.hasPermission(ADMIN)) {
            sender.sendMessage(messages.get("no-permission"));
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase();
        switch (sub) {
            case "reload" -> {
                plugin.reload();
                sender.sendMessage(plugin.messages().get("reload"));
            }
            case "status" -> status(sender, messages);
            case "reset" -> {
                if (args.length < 2) {
                    sender.sendMessage(messages.get("usage"));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(messages.get("player-not-found", args[1], null, -1));
                    return true;
                }
                plugin.forget(target);
                sender.sendMessage(messages.get("reset", target.getName(), null, -1));
            }
            default -> sender.sendMessage(messages.get("usage"));
        }
        return true;
    }

    private void status(CommandSender sender, Messages messages) {
        Settings s = plugin.settings();
        sender.sendMessage(messages.get(s.flightEnabled() ? "status-enabled" : "status-disabled", null, "Flight", -1));
        sender.sendMessage(messages.get(s.speedEnabled() ? "status-enabled" : "status-disabled", null, "Speed", -1));
        boolean any = false;
        for (Map.Entry<UUID, PlayerData> entry : plugin.players().entrySet()) {
            int count = entry.getValue().violations.count();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (count > 0 && player != null) {
                sender.sendMessage(messages.get("status-player", player.getName(), null, count));
                any = true;
            }
        }
        if (!any) {
            sender.sendMessage(messages.get("status-none"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (!sender.hasPermission(ADMIN)) {
            return result;
        }
        if (args.length == 1) {
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    result.add(sub);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    result.add(p.getName());
                }
            }
        }
        return result;
    }
}
