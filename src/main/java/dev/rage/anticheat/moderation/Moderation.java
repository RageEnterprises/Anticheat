package dev.rage.anticheat.moderation;

import dev.rage.anticheat.Messages;
import dev.rage.anticheat.RageAnticheat;
import dev.rage.anticheat.logic.Durations;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Bans, mutes, kicks and warnings. Used by the in-game commands, the anticheat and the Discord bot.
 *
 * <p>Call these methods on the server thread. Database work runs in the background and the
 * returned futures complete back on the server thread, so callers can touch Bukkit directly.
 */
public final class Moderation {

    public static final String EXEMPT = "rageanticheat.mod.exempt";
    public static final String CLEARCHAT_BYPASS = "rageanticheat.mod.clearchat.bypass";
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault());

    /** A player to punish: online, or known from an earlier join. */
    public record Target(UUID uuid, String name) {
    }

    private final RageAnticheat plugin;
    private final Executor main;
    /** Mutes of online players, read by the async chat thread. */
    private final Map<UUID, Punishment> mutes = new ConcurrentHashMap<>();

    public Moderation(RageAnticheat plugin) {
        this.plugin = plugin;
        this.main = task -> plugin.getServer().getScheduler().runTask(plugin, task);
    }

    /** Runs follow-up work on the server thread. */
    public Executor main() {
        return main;
    }

    /** Completes with the online player or the last account that joined with this name, or null. */
    public CompletableFuture<Target> resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return CompletableFuture.completedFuture(new Target(online.getUniqueId(), online.getName()));
        }
        return plugin.database().submit(c -> {
            PlayerRecord record = PlayerStore.findByName(c, name);
            return record == null ? null : new Target(record.uuid(), record.name());
        }).thenApplyAsync(t -> t, main);
    }

    /** Online players with rageanticheat.mod.exempt cannot be punished by other players. */
    public boolean isExempt(Target target) {
        Player online = Bukkit.getPlayer(target.uuid());
        return online != null && online.hasPermission(EXEMPT);
    }

    /**
     * Records and applies a punishment, then announces it.
     *
     * @param duration milliseconds, or {@link Durations#PERMANENT}; ignored for kicks and warnings
     */
    public CompletableFuture<Punishment> punish(Punishment.Type type, Target target, String staff, String reason,
                                                long duration) {
        long now = System.currentTimeMillis();
        long expires = type.lasting() && duration > 0 ? saturatedAdd(now, duration) : 0;
        return plugin.database()
                .submit(c -> PunishmentStore.insert(c, target.uuid(), target.name(), type, reason, staff, now, expires))
                .thenApplyAsync(p -> {
                    apply(p);
                    announce("broadcast-" + type.name().toLowerCase(Locale.ROOT), p);
                    return p;
                }, main);
    }

    /** Lifts an active ban or mute. Completes with false if there was none. */
    public CompletableFuture<Boolean> pardon(Punishment.Type type, Target target, String staff) {
        long now = System.currentTimeMillis();
        return plugin.database()
                .submit(c -> PunishmentStore.deactivate(c, target.uuid(), type, staff, now) > 0)
                .thenApplyAsync(lifted -> {
                    if (lifted) {
                        if (type == Punishment.Type.MUTE) {
                            mutes.remove(target.uuid());
                            Player online = Bukkit.getPlayer(target.uuid());
                            if (online != null) {
                                online.sendMessage(plugin.messages().get("unmute-notify", "staff", staff));
                            }
                        }
                        Punishment removal = new Punishment(0, target.uuid(), target.name(), type, "", staff,
                                now, 0, false, staff, now);
                        announce(type == Punishment.Type.BAN ? "broadcast-unban" : "broadcast-unmute", removal);
                    }
                    return lifted;
                }, main);
    }

    public CompletableFuture<List<Punishment>> history(Target target, int limit) {
        return plugin.database().submit(c -> PunishmentStore.history(c, target.uuid(), limit))
                .thenApplyAsync(h -> h, main);
    }

    /** Scrolls the chat away for everyone without the bypass permission. */
    public void clearChat(String staff) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission(CLEARCHAT_BYPASS)) {
                for (int i = 0; i < 100; i++) {
                    player.sendMessage(Component.empty());
                }
            }
        }
        Bukkit.broadcast(plugin.messages().get("chat-cleared", "staff", staff));
    }

    // --- Mutes, checked from the async chat thread ---

    /** The mute in force for this player, or null. */
    public Punishment mute(UUID uuid) {
        Punishment mute = mutes.get(uuid);
        if (mute != null && !mute.inForce(System.currentTimeMillis())) {
            mutes.remove(uuid);
            return null;
        }
        return mute;
    }

    /** Called while the player logs in, from the async login thread. */
    void cacheMute(UUID uuid, Punishment mute) {
        if (mute == null) {
            mutes.remove(uuid);
        } else {
            mutes.put(uuid, mute);
        }
    }

    void forgetMute(UUID uuid) {
        mutes.remove(uuid);
    }

    // --- Rendering ---

    public Component banScreen(Punishment ban) {
        return plugin.messages().screen("ban-screen", placeholders(ban));
    }

    /** Placeholders for messages about a punishment. */
    public String[] placeholders(Punishment p) {
        return new String[] {
                "id", String.valueOf(p.id()),
                "player", p.name(),
                "staff", p.staff(),
                "reason", p.reason(),
                "type", p.type().name().toLowerCase(Locale.ROOT),
                "duration", p.permanent() ? "permanent" : Durations.format(p.expires() - p.created()),
                "expires", expires(p),
                "date", DATE.format(Instant.ofEpochMilli(p.created()))};
    }

    private String expires(Punishment p) {
        if (!p.type().lasting() || p.permanent()) {
            return "never";
        }
        long left = p.expires() - System.currentTimeMillis();
        return DATE.format(Instant.ofEpochMilli(p.expires())) + (left > 0 ? " (in " + Durations.format(left) + ")" : "");
    }

    private void apply(Punishment p) {
        Player online = Bukkit.getPlayer(p.uuid());
        Messages messages = plugin.messages();
        switch (p.type()) {
            case BAN -> {
                if (online != null) {
                    online.kick(banScreen(p));
                }
            }
            case KICK -> {
                if (online != null) {
                    online.kick(messages.screen("kick-screen", placeholders(p)));
                }
            }
            case MUTE -> {
                mutes.put(p.uuid(), p);
                if (online != null) {
                    online.sendMessage(messages.get("mute-notify", placeholders(p)));
                }
            }
            case WARN -> {
                if (online != null) {
                    online.sendMessage(messages.get("warn-notify", placeholders(p)));
                }
            }
        }
    }

    private void announce(String key, Punishment p) {
        String[] placeholders = placeholders(p);
        Component message = plugin.messages().get(key, placeholders);
        if (plugin.getConfig().getBoolean("moderation.broadcast", true)) {
            Bukkit.broadcast(message);
        } else {
            plugin.broadcastStaff(message);
        }
        plugin.notifyDiscord("punishments", plugin.messages().plain("discord-punishment-title", placeholders),
                plugin.messages().plain(key, placeholders), RageAnticheat.COLOR_PUNISH);
    }

    private static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return sum < a ? Long.MAX_VALUE : sum;
    }
}
