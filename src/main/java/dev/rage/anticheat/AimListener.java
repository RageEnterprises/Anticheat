package dev.rage.anticheat;

import dev.rage.anticheat.logic.AimGcd;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Aim and Bot. Mouse input turns the camera in whole steps of the player's sensitivity (see
 * {@link AimGcd}); rotations an aimbot or a bot such as Baritone computes are not. When a
 * player's pitch changes have no common step, it is reported as Aim if they attacked in the last
 * few seconds and as Bot otherwise. Cinematic camera and some zoom mods smooth the camera too, so
 * by default this only alerts staff.
 */
final class AimListener implements Listener {

    private static final long COMBAT_NANOS = 3_000_000_000L;
    /** At most one report per player in this time. */
    private static final long ALERT_INTERVAL_NANOS = 30_000_000_000L;
    /** Near straight up or down the pitch is clamped, which breaks the steps. */
    private static final float MAX_PITCH = 89.5f;

    private final RageAnticheat plugin;

    AimListener(RageAnticheat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            plugin.data(player).lastAttackNanos = System.nanoTime();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Settings.Aim a = plugin.settings().aim();
        Player player = event.getPlayer();
        float from = event.getFrom().getPitch();
        float to = event.getTo().getPitch();
        if (!a.enabled() || from == to || Math.abs(from) > MAX_PITCH || Math.abs(to) > MAX_PITCH
                || player.getGameMode() == GameMode.SPECTATOR || player.hasPermission(MovementListener.BYPASS)) {
            return;
        }
        PlayerData d = plugin.data(player);
        double step = d.aim.add(to - from);
        if (Double.isNaN(step) || step >= AimGcd.MIN_MOUSE_STEP / 2) {
            return;
        }
        long now = System.nanoTime();
        if (now - d.aimAlertNanos < ALERT_INTERVAL_NANOS) {
            return;
        }
        d.aimAlertNanos = now;
        boolean combat = now - d.lastAttackNanos < COMBAT_NANOS;
        String check = combat ? "Aim" : "Bot";
        String details = String.format("%d camera turns with no mouse sensitivity step (%s)", a.samples(),
                combat ? "aimbot or aim assist" : "automated movement such as Baritone");
        if (a.punish()) {
            plugin.flag(player, d, check, details);
        } else {
            plugin.getLogger().info(player.getName() + " suspected of " + check + " (" + details + ")");
            plugin.alert(player, check, details, "?");
        }
    }
}
