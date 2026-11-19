package dev.rage.anticheat;

import dev.rage.anticheat.logic.Reach;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.util.BoundingBox;

/** Reach: cancels melee hits on targets farther away than a vanilla player can reach. */
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
        Settings s = plugin.settings();
        if (!s.reachEnabled() || player.getGameMode() == GameMode.CREATIVE
                || player.hasPermission(MovementListener.BYPASS)) {
            return;
        }
        Location eye = player.getEyeLocation();
        BoundingBox box = event.getEntity().getBoundingBox();
        double distance = Reach.distanceToBox(eye.getX(), eye.getY(), eye.getZ(),
                box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX(), box.getMaxY(), box.getMaxZ());
        if (distance > s.reachMaxDistance()) {
            event.setCancelled(true);
            plugin.flag(player, plugin.data(player), "Reach",
                    String.format("distance=%.2f max=%.2f", distance, s.reachMaxDistance()));
        }
    }
}
