package dev.rage.anticheat.moderation;

import dev.rage.anticheat.Messages;
import dev.rage.anticheat.RageAnticheat;
import dev.rage.anticheat.logic.Durations;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/**
 * /ban, /mute, /kick, /warn, /unban, /unmute, /clearchat and /history. Each needs
 * rageanticheat.mod.&lt;command&gt;.
 */
public final class ModerationCommand implements TabExecutor {

    public static final List<String> COMMANDS =
            List.of("ban", "mute", "kick", "warn", "unban", "unmute", "clearchat", "history");
    private static final List<String> DURATION_HINTS = List.of("30m", "1h", "1d", "7d", "30d", "perm");

    private final RageAnticheat plugin;
    private final Moderation moderation;

    public ModerationCommand(RageAnticheat plugin, Moderation moderation) {
        this.plugin = plugin;
        this.moderation = moderation;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        Messages messages = plugin.messages();
        if (!sender.hasPermission("rageanticheat.mod." + name)) {
            sender.sendMessage(messages.get("no-permission"));
            return true;
        }
        if (name.equals("clearchat")) {
            moderation.clearChat(sender.getName());
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(messages.get("usage-" + name));
            return true;
        }
        String staff = sender.getName();
        moderation.resolve(args[0]).thenAccept(target -> {
            if (target == null) {
                sender.sendMessage(messages.get("never-joined", "player", args[0]));
                return;
            }
            switch (name) {
                case "ban" -> lasting(sender, Punishment.Type.BAN, target, args);
                case "mute" -> lasting(sender, Punishment.Type.MUTE, target, args);
                case "kick" -> {
                    if (Bukkit.getPlayer(target.uuid()) == null) {
                        sender.sendMessage(messages.get("player-not-found", "player", target.name()));
                    } else if (allowed(sender, target)) {
                        moderation.punish(Punishment.Type.KICK, target, staff, reason(args, 1), 0);
                    }
                }
                case "warn" -> {
                    if (allowed(sender, target)) {
                        moderation.punish(Punishment.Type.WARN, target, staff, reason(args, 1), 0);
                    }
                }
                case "unban" -> pardon(sender, Punishment.Type.BAN, target);
                case "unmute" -> pardon(sender, Punishment.Type.MUTE, target);
                case "history" -> history(sender, target);
                default -> sender.sendMessage(messages.get("usage-" + name));
            }
        });
        return true;
    }

    /** /ban and /mute: {@code <player> [duration] [reason...]}. Without a duration it is permanent. */
    private void lasting(CommandSender sender, Punishment.Type type, Moderation.Target target, String[] args) {
        if (!allowed(sender, target)) {
            return;
        }
        long duration = args.length > 1 ? Durations.parse(args[1]) : Durations.PERMANENT;
        int reasonStart = 2;
        if (duration == Durations.INVALID) {
            duration = Durations.PERMANENT;
            reasonStart = 1; // the second word is part of the reason, not a duration
        }
        moderation.punish(type, target, sender.getName(), reason(args, reasonStart), duration);
    }

    private void pardon(CommandSender sender, Punishment.Type type, Moderation.Target target) {
        moderation.pardon(type, target, sender.getName()).thenAccept(lifted -> {
            if (!lifted) {
                String key = type == Punishment.Type.BAN ? "not-banned" : "not-muted";
                sender.sendMessage(plugin.messages().get(key, "player", target.name()));
            }
        });
    }

    private void history(CommandSender sender, Moderation.Target target) {
        Messages messages = plugin.messages();
        moderation.history(target, 15).thenAccept(history -> {
            sender.sendMessage(messages.get("history-header", "player", target.name(),
                    "count", String.valueOf(history.size())));
            if (history.isEmpty()) {
                sender.sendMessage(messages.get("history-none"));
            }
            long now = System.currentTimeMillis();
            for (Punishment p : history) {
                String status = !p.type().lasting() ? "" : p.inForce(now) ? "active"
                        : p.removedBy() != null ? "removed by " + p.removedBy() : "expired";
                String[] placeholders = Arrays.copyOf(moderation.placeholders(p), 18);
                placeholders[16] = "status";
                placeholders[17] = status;
                sender.sendMessage(messages.get("history-entry", placeholders));
            }
        });
    }

    /** Players cannot punish exempt staff; the console can. */
    private boolean allowed(CommandSender sender, Moderation.Target target) {
        if (!(sender instanceof ConsoleCommandSender) && moderation.isExempt(target)) {
            sender.sendMessage(plugin.messages().get("exempt", "player", target.name()));
            return false;
        }
        return true;
    }

    private String reason(String[] args, int start) {
        if (args.length <= start) {
            return plugin.getConfig().getString("moderation.default-reason", "No reason given");
        }
        return String.join(" ", Arrays.copyOfRange(args, start, args.length));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (!sender.hasPermission("rageanticheat.mod." + name) || name.equals("clearchat")) {
            return result;
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    result.add(p.getName());
                }
            }
        } else if (args.length == 2 && (name.equals("ban") || name.equals("mute"))) {
            for (String hint : DURATION_HINTS) {
                if (hint.startsWith(prefix)) {
                    result.add(hint);
                }
            }
        }
        return result;
    }
}
