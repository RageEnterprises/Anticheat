package dev.rage.anticheat.discord;

import dev.rage.anticheat.Messages;
import dev.rage.anticheat.RageAnticheat;
import dev.rage.anticheat.logic.Durations;
import dev.rage.anticheat.moderation.Moderation;
import dev.rage.anticheat.moderation.Punishment;
import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * A Discord bot for moderating the server with slash commands, and for posting alerts to a channel.
 *
 * <p>Only members with the configured staff role can use it; /console additionally needs the admin
 * role and {@code allow-console: true}. Discord events arrive on JDA threads, so every action hops
 * to the server thread before touching Bukkit.
 */
public final class DiscordBot extends ListenerAdapter implements AutoCloseable {

    private final RageAnticheat plugin;
    private final String guildId;
    private final String staffRoleId;
    private final String adminRoleId;
    private final String alertChannelId;
    private final boolean allowConsole;
    private final Set<String> events;
    private final JDA jda;

    private DiscordBot(RageAnticheat plugin, ConfigurationSection c) {
        this.plugin = plugin;
        this.guildId = c.getString("guild-id", "");
        this.staffRoleId = c.getString("staff-role-id", "");
        this.adminRoleId = c.getString("admin-role-id", "");
        this.alertChannelId = c.getString("alert-channel-id", "");
        this.allowConsole = c.getBoolean("allow-console", false);
        this.events = new HashSet<>(c.getStringList("events"));
        // No gateway intents: slash commands and sending messages do not need any.
        this.jda = JDABuilder.createLight(c.getString("token", ""), EnumSet.noneOf(GatewayIntent.class))
                .addEventListeners(this)
                .build();
    }

    /** Starts the bot, or returns null (with a warning) when it is not configured. */
    public static DiscordBot start(RageAnticheat plugin, ConfigurationSection c) {
        if (c.getString("token", "").isBlank() || c.getString("guild-id", "").isBlank()
                || c.getString("staff-role-id", "").isBlank()) {
            plugin.getLogger().warning("discord.bot needs token, guild-id and staff-role-id; the bot is disabled.");
            return null;
        }
        try {
            return new DiscordBot(plugin, c);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Could not start the Discord bot", e);
            return null;
        }
    }

    @Override
    public void onReady(ReadyEvent event) {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            plugin.getLogger().warning("The Discord bot is not in guild " + guildId + "; invite it with the "
                    + "bot and applications.commands scopes.");
            return;
        }
        guild.updateCommands().addCommands(commands()).queue(
                ok -> plugin.getLogger().info("Discord bot ready in " + guild.getName()),
                error -> plugin.getLogger().warning("Could not register Discord commands: " + error.getMessage()));
    }

    private List<SlashCommandData> commands() {
        List<SlashCommandData> list = new ArrayList<>();
        list.add(Commands.slash("ban", "Ban a player")
                .addOption(OptionType.STRING, "player", "Player name", true)
                .addOption(OptionType.STRING, "duration", "e.g. 30m, 7d, perm (default perm)", false)
                .addOption(OptionType.STRING, "reason", "Reason", false));
        list.add(Commands.slash("mute", "Mute a player")
                .addOption(OptionType.STRING, "player", "Player name", true)
                .addOption(OptionType.STRING, "duration", "e.g. 30m, 7d, perm (default perm)", false)
                .addOption(OptionType.STRING, "reason", "Reason", false));
        list.add(Commands.slash("kick", "Kick an online player")
                .addOption(OptionType.STRING, "player", "Player name", true)
                .addOption(OptionType.STRING, "reason", "Reason", false));
        list.add(Commands.slash("warn", "Warn a player")
                .addOption(OptionType.STRING, "player", "Player name", true)
                .addOption(OptionType.STRING, "reason", "Reason", false));
        list.add(Commands.slash("unban", "Lift a ban").addOption(OptionType.STRING, "player", "Player name", true));
        list.add(Commands.slash("unmute", "Lift a mute").addOption(OptionType.STRING, "player", "Player name", true));
        list.add(Commands.slash("freeze", "Freeze or unfreeze an online player")
                .addOption(OptionType.STRING, "player", "Player name", true));
        list.add(Commands.slash("clearchat", "Clear the in-game chat"));
        list.add(Commands.slash("profile", "Show a player's profile")
                .addOption(OptionType.STRING, "player", "Player name", true));
        list.add(Commands.slash("history", "Show a player's punishment history")
                .addOption(OptionType.STRING, "player", "Player name", true));
        list.add(Commands.slash("players", "List online players"));
        list.add(Commands.slash("maintenance", "Turn maintenance mode on or off")
                .addOptions(new OptionData(OptionType.STRING, "state", "on or off", true)
                        .addChoice("on", "on").addChoice("off", "off")));
        if (allowConsole) {
            list.add(Commands.slash("console", "Run a server console command (admin role only)")
                    .addOption(OptionType.STRING, "command", "Command without the leading /", true));
        }
        return list;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (event.getGuild() == null || !event.getGuild().getId().equals(guildId) || member == null) {
            event.reply("This bot only works in the server's Discord.").setEphemeral(true).queue();
            return;
        }
        boolean admin = hasRole(member, adminRoleId);
        if (!admin && !hasRole(member, staffRoleId)) {
            event.reply("You need the staff role to use this.").setEphemeral(true).queue();
            return;
        }
        String name = event.getName();
        if (name.equals("console") && !(allowConsole && admin)) {
            event.reply("Only the admin role can use /console.").setEphemeral(true).queue();
            return;
        }
        String staff = "Discord:" + event.getUser().getName();
        event.deferReply().queue(hook -> Bukkit.getScheduler().runTask(plugin, () -> handle(event, name, staff, hook)));
    }

    /** Runs on the server thread. */
    private void handle(SlashCommandInteractionEvent event, String name, String staff, InteractionHook hook) {
        Messages messages = plugin.messages();
        Moderation moderation = plugin.moderation();
        Consumer<String> reply = text -> hook.sendMessage(text.isEmpty() ? "Done." : truncate(text))
                .setAllowedMentions(Collections.emptyList()).queue();
        switch (name) {
            case "clearchat" -> {
                moderation.clearChat(staff);
                reply.accept(messages.plain("chat-cleared", "staff", staff));
                return;
            }
            case "players" -> {
                List<String> names = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    names.add(p.getName());
                }
                reply.accept(names.size() + " online: " + (names.isEmpty() ? "nobody" : String.join(", ", names)));
                return;
            }
            case "maintenance" -> {
                boolean on = string(event, "state", "on").equals("on");
                if (on == plugin.maintenance().enabled()) {
                    reply.accept(messages.plain(on ? "maintenance-already-on" : "maintenance-already-off"));
                } else {
                    plugin.maintenance().set(on, staff);
                    reply.accept(messages.plain(on ? "maintenance-on" : "maintenance-off", "staff", staff));
                }
                return;
            }
            case "console" -> {
                String command = string(event, "command", "");
                plugin.getLogger().info(staff + " ran console command from Discord: " + command);
                boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                reply.accept(ok ? "Ran `" + command + "`." : "Unknown command `" + command + "`.");
                return;
            }
            default -> {
            }
        }

        String playerName = string(event, "player", "");
        moderation.resolve(playerName).thenAccept(target -> {
            if (target == null) {
                reply.accept(messages.plain("never-joined", "player", playerName));
                return;
            }
            boolean punishing = name.equals("ban") || name.equals("mute") || name.equals("kick")
                    || name.equals("warn") || name.equals("freeze");
            if (punishing && moderation.isExempt(target)) {
                reply.accept(messages.plain("exempt", "player", target.name()));
                return;
            }
            String reason = string(event, "reason",
                    plugin.getConfig().getString("moderation.default-reason", "No reason given"));
            switch (name) {
                case "ban", "mute" -> {
                    long duration = Durations.parse(string(event, "duration", "perm"));
                    if (duration == Durations.INVALID) {
                        reply.accept("Invalid duration. Use something like 30m, 1d, 7d or perm.");
                        return;
                    }
                    Punishment.Type type = name.equals("ban") ? Punishment.Type.BAN : Punishment.Type.MUTE;
                    moderation.punish(type, target, staff, reason, duration).thenAccept(p ->
                            reply.accept(messages.plain("broadcast-" + name, moderation.placeholders(p))));
                }
                case "kick", "warn" -> {
                    if (name.equals("kick") && Bukkit.getPlayer(target.uuid()) == null) {
                        reply.accept(messages.plain("player-not-found", "player", target.name()));
                        return;
                    }
                    Punishment.Type type = name.equals("kick") ? Punishment.Type.KICK : Punishment.Type.WARN;
                    moderation.punish(type, target, staff, reason, 0).thenAccept(p ->
                            reply.accept(messages.plain("broadcast-" + name, moderation.placeholders(p))));
                }
                case "unban", "unmute" -> {
                    Punishment.Type type = name.equals("unban") ? Punishment.Type.BAN : Punishment.Type.MUTE;
                    moderation.pardon(type, target, staff).thenAccept(lifted -> reply.accept(lifted
                            ? messages.plain("broadcast-" + name, "player", target.name(), "staff", staff)
                            : messages.plain(name.equals("unban") ? "not-banned" : "not-muted", "player", target.name())));
                }
                case "freeze" -> {
                    Player online = Bukkit.getPlayer(target.uuid());
                    if (online == null) {
                        reply.accept(messages.plain("player-not-found", "player", target.name()));
                        return;
                    }
                    boolean frozen = plugin.freeze().toggle(online, staff);
                    reply.accept(messages.plain(frozen ? "broadcast-frozen" : "broadcast-unfrozen",
                            "player", target.name(), "staff", staff));
                }
                case "profile" -> plugin.profiles().load(target).thenAccept(profile -> {
                    if (profile == null) {
                        reply.accept(messages.plain("never-joined", "player", target.name()));
                        return;
                    }
                    StringBuilder text = new StringBuilder();
                    for (String[] line : plugin.profiles().lines(profile)) {
                        String[] placeholders = new String[line.length - 1];
                        System.arraycopy(line, 1, placeholders, 0, placeholders.length);
                        text.append(messages.plain(line[0], placeholders)).append('\n');
                    }
                    reply.accept(text.toString());
                });
                case "history" -> moderation.history(target, 15).thenAccept(history -> {
                    StringBuilder text = new StringBuilder(messages.plain("history-header",
                            "player", target.name(), "count", String.valueOf(history.size()))).append('\n');
                    if (history.isEmpty()) {
                        text.append(messages.plain("history-none"));
                    }
                    long now = System.currentTimeMillis();
                    for (Punishment p : history) {
                        String status = !p.type().lasting() ? "" : p.inForce(now) ? "active"
                                : p.removedBy() != null ? "removed by " + p.removedBy() : "expired";
                        String[] base = moderation.placeholders(p);
                        String[] placeholders = Arrays.copyOf(base, base.length + 2);
                        placeholders[base.length] = "status";
                        placeholders[base.length + 1] = status;
                        text.append(messages.plain("history-entry", placeholders)).append('\n');
                    }
                    reply.accept(text.toString());
                });
                default -> reply.accept("Unknown command.");
            }
        });
    }

    /** Posts an embed to the alert channel if the bot is enabled for {@code event}. Safe from any thread. */
    public void post(String event, String title, String description, int color) {
        if (alertChannelId.isBlank() || !events.contains(event)) {
            return;
        }
        TextChannel channel = jda.getTextChannelById(alertChannelId);
        if (channel == null) {
            return;
        }
        channel.sendMessageEmbeds(new EmbedBuilder()
                        .setTitle(truncate(title, 256))
                        .setDescription(truncate(description, 4000))
                        .setColor(new Color(color))
                        .setTimestamp(Instant.now())
                        .build())
                .setAllowedMentions(Collections.emptyList())
                .queue(null, error -> plugin.getLogger().warning("Discord bot could not post: " + error.getMessage()));
    }

    private static boolean hasRole(Member member, String roleId) {
        if (roleId == null || roleId.isBlank()) {
            return false;
        }
        for (Role role : member.getRoles()) {
            if (role.getId().equals(roleId)) {
                return true;
            }
        }
        return false;
    }

    private static String string(SlashCommandInteractionEvent event, String option, String fallback) {
        OptionMapping mapping = event.getOption(option);
        return mapping == null ? fallback : mapping.getAsString();
    }

    private static String truncate(String text) {
        return truncate(text, 2000);
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    @Override
    public void close() {
        jda.shutdown();
    }
}
