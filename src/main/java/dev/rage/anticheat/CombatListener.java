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
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/** Melee checks: Reach and KillAura (flagged hits are cancelled) and AutoClicker. */
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

    /** Survival block reach; a swing at a block this close is mining or placing, not a click. */
    private static final double BLOCK_REACH = 4.5;
    /** A pause this long starts a new click streak. */
    private static final long MAX_CLICK_GAP_MS = 400;

    /** Dropping an item swings the arm right after the drop packet. */
    private static final long DROP_SWING_NANOS = 100_000_000L;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrop(PlayerDropItemEvent event) {
        plugin.data(event.getPlayer()).lastDropNanos = System.nanoTime();
    }

    /**
     * Arm swings arrive with real-time precision because the server handles packets between ticks.
     * Swings are counted as clicks only when no block is under the crosshair: holding the button on
     * a block swings every tick.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwing(PlayerAnimationEvent event) {
        Settings.AutoClicker a = plugin.settings().autoClicker();
        Player player = event.getPlayer();
        if (!a.enabled() || event.getAnimationType() != PlayerAnimationType.ARM_SWING
                || player.getGameMode() == GameMode.CREATIVE || player.hasPermission(MovementListener.BYPASS)) {
            return;
        }
        PlayerData d = plugin.data(player);
        long nanos = System.nanoTime();
        if (plugin.serverLagging(nanos) || nanos - d.lastDropNanos < DROP_SWING_NANOS
                || player.rayTraceBlocks(BLOCK_REACH) != null) {
            // A lag spike delivers clicks in a burst; mining, and holding the drop key, are not clicking.
            d.clicks.reset();
            return;
        }
        long now = nanos / 1_000_000L;
        d.clicks.click(now, MAX_CLICK_GAP_MS);
        int cps = d.clicks.cps(now);
        String details = null;
        if (cps > a.maxCps()) {
            details = String.format("%d clicks per second, max %d", cps, a.maxCps());
        } else if (d.clicks.full(a.samples())) {
            double mean = d.clicks.meanInterval(a.samples());
            double deviation = d.clicks.deviation(a.samples());
            if (mean <= 1000.0 / a.minCps() && deviation < a.minDeviationMs()) {
                details = String.format("%d clicks at %.1f CPS spaced within %.1f ms (min %.1f)",
                        a.samples(), 1000 / mean, deviation, a.minDeviationMs());
            }
        }
        if (details != null) {
            d.clicks.reset();
            plugin.flag(player, d, "AutoClicker", details);
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
