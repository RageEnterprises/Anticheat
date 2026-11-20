package dev.rage.anticheat.moderation;

import dev.rage.anticheat.RageAnticheat;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/**
 * Alt detection: accounts are linked when they have joined from the same IP. Staff are told when a
 * player with known alts joins, {@code /alts <player>} lists them, and joins can be refused when an
 * alt is banned (ban evasion).
 */
public final class Alts implements TabExecutor {

    public static final String PERMISSION = "rageanticheat.mod.alts";

    /** An alt and whether it is banned right now. */
    public record Alt(PlayerRecord player, boolean banned) {
    }

    private final RageAnticheat plugin;

    public Alts(RageAnticheat plugin) {
        this.plugin = plugin;
    }

    static List<Alt> find(Connection c, UUID uuid, long now) throws SQLException {
        List<Alt> result = new ArrayList<>();
        for (PlayerRecord alt : PlayerStore.alts(c, uuid)) {
            result.add(new Alt(alt, PunishmentStore.active(c, alt.uuid(), Punishment.Type.BAN, now) != null));
        }
        return result;
    }

    /** A banned account that has joined from {@code ip}, or null. Runs on the login thread. */
    PlayerRecord bannedAccountOnIp(String ip, UUID joining) {
        long now = System.currentTimeMillis();
        return plugin.database().call(c -> {
            for (PlayerRecord other : PlayerStore.accountsOnIp(c, ip, joining)) {
                if (PunishmentStore.active(c, other.uuid(), Punishment.Type.BAN, now) != null) {
                    return other;
                }
            }
            return null;
        });
    }

    /** After a join: tell staff if the player shares an IP with other accounts. */
    void notifyStaff(Player player) {
        if (!plugin.getConfig().getBoolean("alts.notify-staff", true)) {
            return;
        }
        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        plugin.database().submit(c -> find(c, uuid, now)).thenAcceptAsync(alts -> {
            if (alts.isEmpty()) {
                return;
            }
            String list = describe(alts);
            String[] placeholders = {"player", name, "count", String.valueOf(alts.size()), "alts", list};
            plugin.broadcastStaff(plugin.messages().get("alts-join", placeholders));
            plugin.notifyDiscord("joins", plugin.messages().plain("discord-alts-title", placeholders),
                    plugin.messages().plain("alts-join", placeholders), RageAnticheat.COLOR_INFO);
        }, plugin.moderation().main());
    }

    private static String describe(List<Alt> alts) {
        StringBuilder out = new StringBuilder();
        for (Alt alt : alts) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(alt.player().name());
            if (alt.banned()) {
                out.append(" (banned)");
            }
        }
        return out.toString();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(plugin.messages().get("no-permission"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(plugin.messages().get("usage-alts"));
            return true;
        }
        long now = System.currentTimeMillis();
        plugin.moderation().resolve(args[0]).thenAccept(target -> {
            if (target == null) {
                sender.sendMessage(plugin.messages().get("never-joined", "player", args[0]));
                return;
            }
            plugin.database().submit(c -> find(c, target.uuid(), now)).thenAcceptAsync(alts -> {
                if (alts.isEmpty()) {
                    sender.sendMessage(plugin.messages().get("alts-none", "player", target.name()));
                } else {
                    sender.sendMessage(plugin.messages().get("alts-list", "player", target.name(),
                            "count", String.valueOf(alts.size()), "alts", describe(alts)));
                }
            }, plugin.moderation().main());
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1 && sender.hasPermission(PERMISSION)) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    result.add(p.getName());
                }
            }
        }
        return result;
    }
}
