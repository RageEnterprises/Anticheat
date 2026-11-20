package dev.rage.anticheat.moderation;

import dev.rage.anticheat.Messages;
import dev.rage.anticheat.RageAnticheat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/** /profile &lt;player&gt;: one overview of a player's account, punishments and anticheat flags. */
public final class Profiles implements TabExecutor {

    public static final String PERMISSION = "rageanticheat.mod.profile";
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault());

    /** Everything the profile shows, loaded in one database round trip. */
    public record Profile(PlayerRecord player, int ips, int alts, Map<Punishment.Type, Integer> punishments,
                          Punishment ban, Punishment mute, Map<String, Integer> flags) {
    }

    private final RageAnticheat plugin;

    public Profiles(RageAnticheat plugin) {
        this.plugin = plugin;
    }

    /** Loads a profile; completes on the server thread, with null if the player never joined. */
    public CompletableFuture<Profile> load(Moderation.Target target) {
        long now = System.currentTimeMillis();
        return plugin.database().submit(c -> {
            PlayerRecord record = PlayerStore.find(c, target.uuid());
            if (record == null) {
                return null;
            }
            return new Profile(record, PlayerStore.countIps(c, target.uuid()),
                    PlayerStore.alts(c, target.uuid()).size(), PunishmentStore.counts(c, target.uuid()),
                    PunishmentStore.active(c, target.uuid(), Punishment.Type.BAN, now),
                    PunishmentStore.active(c, target.uuid(), Punishment.Type.MUTE, now),
                    PlayerStore.flagCounts(c, target.uuid()));
        }).thenApplyAsync(p -> p, plugin.moderation().main());
    }

    /** The profile as message lines, shared by the command and the Discord bot. */
    public List<String[]> lines(Profile p) {
        List<String[]> lines = new ArrayList<>();
        PlayerRecord r = p.player();
        boolean online = Bukkit.getPlayer(r.uuid()) != null;
        lines.add(new String[] {"profile-header", "player", r.name()});
        lines.add(new String[] {"profile-account", "uuid", r.uuid().toString(),
                "status", online ? "online" : "offline"});
        lines.add(new String[] {"profile-dates", "first", DATE.format(Instant.ofEpochMilli(r.firstJoin())),
                "last", online ? "now" : DATE.format(Instant.ofEpochMilli(r.lastJoin()))});
        lines.add(new String[] {"profile-network", "ips", String.valueOf(p.ips()), "alts", String.valueOf(p.alts())});
        lines.add(new String[] {"profile-punishments",
                "bans", count(p, Punishment.Type.BAN), "mutes", count(p, Punishment.Type.MUTE),
                "kicks", count(p, Punishment.Type.KICK), "warns", count(p, Punishment.Type.WARN)});
        Moderation moderation = plugin.moderation();
        if (p.ban() != null) {
            lines.add(prefixed("profile-active-ban", moderation.placeholders(p.ban())));
        }
        if (p.mute() != null) {
            lines.add(prefixed("profile-active-mute", moderation.placeholders(p.mute())));
        }
        StringBuilder flags = new StringBuilder();
        for (Map.Entry<String, Integer> flag : p.flags().entrySet()) {
            if (flags.length() > 0) {
                flags.append(", ");
            }
            flags.append(flag.getKey()).append(' ').append(flag.getValue());
        }
        lines.add(new String[] {"profile-flags", "flags", flags.length() == 0 ? "none" : flags.toString()});
        return lines;
    }

    private static String count(Profile p, Punishment.Type type) {
        return String.valueOf(p.punishments().getOrDefault(type, 0));
    }

    private static String[] prefixed(String key, String[] placeholders) {
        String[] line = new String[placeholders.length + 1];
        line[0] = key;
        System.arraycopy(placeholders, 0, line, 1, placeholders.length);
        return line;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages messages = plugin.messages();
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(messages.get("no-permission"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(messages.get("usage-profile"));
            return true;
        }
        plugin.moderation().resolve(args[0])
                .thenCompose(target -> target == null ? CompletableFuture.completedFuture(null) : load(target))
                .thenAccept(profile -> {
                    if (profile == null) {
                        sender.sendMessage(messages.get("never-joined", "player", args[0]));
                        return;
                    }
                    for (String[] line : lines(profile)) {
                        String[] placeholders = new String[line.length - 1];
                        System.arraycopy(line, 1, placeholders, 0, placeholders.length);
                        sender.sendMessage(messages.get(line[0], placeholders));
                    }
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
