package dev.rage.anticheat;

import dev.rage.anticheat.logic.Reach;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/** Melee checks: Reach and KillAura. Flagged hits are cancelled. */
final class CombatListener implements Listener {

    private final RageAnticheat plugin;

    CombatListener(RageAnticheat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getCause() != DamageCause.ENTITY_ATTACK || !(event.getDamager() instanceof Player player)) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.hasPermission(MovementListener.BYPASS)) {
            return;
        }
        Settings s = plugin.settings();
        PlayerData d = plugin.data(player);
        Entity target = event.getEntity();
        Location eye = player.getEyeLocation();
        BoundingBox box = target.getBoundingBox();

        String check = null;
        String details = null;
        if (s.reachEnabled()) {
            double distance = Reach.distanceToBox(eye.getX(), eye.getY(), eye.getZ(),
                    box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX(), box.getMaxY(), box.getMaxZ());
            if (distance > s.reachMaxDistance()) {
                check = "Reach";
                details = String.format("distance=%.2f max=%.2f", distance, s.reachMaxDistance());
            }
        }
        if (check == null && s.killAura().enabled()) {
            details = killAura(d, s.killAura(), eye, box, target);
            check = details == null ? null : "KillAura";
        }
        if (check != null) {
            event.setCancelled(true);
            plugin.flag(player, d, check, details);
        }
    }

    /**
     * Two signs of an aura: hitting targets that are not under the crosshair, and hitting many
     * different targets in a short time. Single misses happen with lag, so misses are counted.
     */
    private static String killAura(PlayerData d, Settings.KillAura k, Location eye, BoundingBox targetBox,
                                   Entity target) {
        int now = Bukkit.getCurrentTick();
        int targets = d.attackTargets.record(target.getEntityId(), now, k.switchWindowTicks());
        if (targets > k.maxTargets()) {
            d.attackTargets.clear();
            return String.format("hit %d different targets within %d ticks", targets, k.switchWindowTicks());
        }

        Vector start = eye.toVector();
        Vector direction = eye.getDirection();
        BoundingBox expanded = targetBox.clone().expand(k.hitboxExpand());
        if (expanded.contains(start) || expanded.rayTrace(start, direction, 8.0) != null) {
            return null;
        }
        int misses = d.attackMisses.record(now, k.missWindowTicks());
        if (misses < k.maxMisses()) {
            return null;
        }
        d.attackMisses.clear();
        return String.format("%d hits on targets outside the crosshair within %d ticks", misses, k.missWindowTicks());
    }
}
