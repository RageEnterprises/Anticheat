package dev.rage.anticheat.moderation;

import dev.rage.anticheat.RageAnticheat;
import dev.rage.anticheat.logic.Durations;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * /freeze &lt;player&gt;: holds a player in place for a screenshare or inspection. Frozen players can
 * look around and chat, but cannot move, build, fight, teleport or run most commands. They stay
 * frozen if they rejoin, until a staff member unfreezes them.
 */
public final class Freeze implements Listener, TabExecutor {

    public static final String PERMISSION = "rageanticheat.mod.freeze";

    private final RageAnticheat plugin;
    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();

    public Freeze(RageAnticheat plugin) {
        this.plugin = plugin;
        // Reminder every 5 seconds.
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (UUID uuid : frozen) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    player.sendMessage(plugin.messages().get("frozen-reminder"));
                }
            }
        }, 100L, 100L);
    }

    public boolean isFrozen(UUID uuid) {
        return frozen.contains(uuid);
    }

    /** Toggles the freeze. Returns the new state. */
    public boolean toggle(Player target, String staff) {
        boolean nowFrozen = frozen.add(target.getUniqueId());
        if (!nowFrozen) {
            frozen.remove(target.getUniqueId());
        }
        String key = nowFrozen ? "frozen" : "unfrozen";
        target.sendMessage(plugin.messages().get(key + "-notify", "staff", staff));
        plugin.broadcastStaff(plugin.messages().get("broadcast-" + key, "player", target.getName(), "staff", staff));
        return nowFrozen;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(plugin.messages().get("no-permission"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(plugin.messages().get("usage-freeze"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(plugin.messages().get("player-not-found", "player", args[0]));
            return true;
        }
        if (!(sender instanceof ConsoleCommandSender) && target.hasPermission(Moderation.EXEMPT)
                && !isFrozen(target.getUniqueId())) {
            sender.sendMessage(plugin.messages().get("exempt", "player", target.getName()));
            return true;
        }
        toggle(target, sender.getName());
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

    // --- Enforcement. LOWEST so the freeze applies before anything else sees the action. ---

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (isFrozen(event.getPlayer().getUniqueId()) && event.hasChangedPosition()) {
            Location from = event.getFrom();
            Location to = event.getTo();
            // Keep the new rotation so the player can still look around.
            event.setTo(new Location(from.getWorld(), from.getX(), from.getY(), from.getZ(), to.getYaw(), to.getPitch()));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (isFrozen(event.getPlayer().getUniqueId()) && (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || cause == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!isFrozen(event.getPlayer().getUniqueId())) {
            return;
        }
        String label = event.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        label = label.substring(label.indexOf(':') + 1);
        if (!plugin.getConfig().getStringList("freeze.allowed-commands").contains(label)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.messages().get("frozen-blocked"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        cancelIfFrozen(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        cancelIfFrozen(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        cancelIfFrozen(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        cancelIfFrozen(event.getPlayer(), event);
    }

    /** Frozen players can neither deal nor take damage, so nobody can kill them while they cannot move. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isFrozen(player.getUniqueId())) {
            event.setCancelled(true);
        } else if (event instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof Player attacker && isFrozen(attacker.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private void cancelIfFrozen(Player player, Cancellable event) {
        if (isFrozen(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (isFrozen(event.getPlayer().getUniqueId())) {
            event.getPlayer().sendMessage(plugin.messages().get("frozen-reminder"));
        }
    }

    /** Logging out to avoid a screenshare is an admission; staff are told and the player can be banned. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!isFrozen(player.getUniqueId())) {
            return;
        }
        String[] placeholders = {"player", player.getName()};
        plugin.broadcastStaff(plugin.messages().get("frozen-logout", placeholders));
        plugin.notifyDiscord("punishments", plugin.messages().plain("discord-freeze-title", placeholders),
                plugin.messages().plain("frozen-logout", placeholders), RageAnticheat.COLOR_ALERT);
        if (plugin.getConfig().getBoolean("freeze.ban-on-logout", false)) {
            frozen.remove(player.getUniqueId());
            long duration = Durations.parse(plugin.getConfig().getString("freeze.ban-duration", "perm"));
            plugin.moderation().punish(Punishment.Type.BAN,
                    new Moderation.Target(player.getUniqueId(), player.getName()), "RageAnticheat",
                    plugin.messages().plain("frozen-logout-reason", placeholders),
                    duration == Durations.INVALID ? Durations.PERMANENT : duration);
        }
    }
}
