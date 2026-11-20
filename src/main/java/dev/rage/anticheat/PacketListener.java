package dev.rage.anticheat;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.potion.PotionEffectType;

/**
 * Packets the vanilla client never sends. Cheat clients send them to fake their rotation to the
 * server (silent aim, "derp"), to hit without the swing packet, or to sprint when the client may
 * not.
 */
final class PacketListener implements Listener {

    /** The swing packet comes in the same client tick as the attack, before or after it. */
    private static final long SWING_WINDOW_NANOS = 150_000_000L;
    private static final long SWING_CHECK_TICKS = 3;
    /** Time beyond the ping for the client to learn about a food or effect change. */
    private static final long SPRINT_GRACE_NANOS = 1_000_000_000L;
    private static final int MIN_SPRINT_FOOD = 7;

    private final RageAnticheat plugin;

    PacketListener(RageAnticheat plugin) {
        this.plugin = plugin;
    }

    private static boolean skip(Player player) {
        return player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR
                || player.hasPermission(MovementListener.BYPASS);
    }

    /** The vanilla client keeps its pitch within -90 to 90 degrees; the server clamps it after this event. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        float pitch = event.getTo().getPitch();
        if (!plugin.settings().badPackets().invalidRotation() || Math.abs(pitch) <= 90.0f) {
            return;
        }
        Player player = event.getPlayer();
        if (player.hasPermission(MovementListener.BYPASS)) {
            return;
        }
        // Every move packet carries the rotation, so flag at most once a second.
        PlayerData d = plugin.data(player);
        long now = System.nanoTime();
        if (now - d.rotationFlagNanos >= 1_000_000_000L) {
            d.rotationFlagNanos = now;
            plugin.flag(player, d, "InvalidRotation", String.format("pitch %.1f", pitch));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() == PlayerAnimationType.ARM_SWING) {
            plugin.data(event.getPlayer()).lastSwingNanos = System.nanoTime();
        }
    }

    /** Every vanilla attack sends a swing packet with it; kill auras often leave it out. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getCause() != DamageCause.ENTITY_ATTACK || !(event.getDamager() instanceof Player player)
                || !plugin.settings().badPackets().noSwing() || skip(player)) {
            return;
        }
        PlayerData d = plugin.data(player);
        long attack = System.nanoTime();
        if (attack - d.lastSwingNanos < SWING_WINDOW_NANOS || plugin.serverLagging(attack)) {
            return;
        }
        // The swing may still come later in the same client tick.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || plugin.serverLagging(System.nanoTime())) {
                return;
            }
            PlayerData now = plugin.data(player);
            if (now.lastSwingNanos < attack - SWING_WINDOW_NANOS) {
                plugin.flag(player, now, "NoSwing", "attacked " + event.getEntity().getName() + " without swinging");
            }
        }, SWING_CHECK_TICKS);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            plugin.data(player).sprintStateNanos = System.nanoTime();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEffect(EntityPotionEffectEvent event) {
        if (event.getEntity() instanceof Player player) {
            plugin.data(player).sprintStateNanos = System.nanoTime();
        }
    }

    /** The vanilla client cannot start sprinting while hungry (food 6 or less) or blind. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSprint(PlayerToggleSprintEvent event) {
        Player player = event.getPlayer();
        if (!event.isSprinting() || !plugin.settings().badPackets().impossibleSprint() || skip(player)
                || player.getAllowFlight() || player.isInsideVehicle()) {
            return;
        }
        boolean hungry = player.getFoodLevel() < MIN_SPRINT_FOOD;
        boolean blind = player.hasPotionEffect(PotionEffectType.BLINDNESS);
        if (!hungry && !blind) {
            return;
        }
        PlayerData d = plugin.data(player);
        long now = System.nanoTime();
        if (now - d.sprintStateNanos < SPRINT_GRACE_NANOS + player.getPing() * 1_000_000L || plugin.serverLagging(now)) {
            return;
        }
        event.setCancelled(true);
        plugin.flag(player, d, "ImpossibleSprint", hungry
                ? "started sprinting with food " + player.getFoodLevel()
                : "started sprinting while blind");
    }
}
