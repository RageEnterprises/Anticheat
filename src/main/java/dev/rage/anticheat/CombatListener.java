package dev.rage.anticheat;

import dev.rage.anticheat.logic.Reach;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/** Melee checks: Reach, KillAura and CrystalAura (flagged hits are cancelled) and AutoClicker. */
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
        if (check == null && target instanceof EnderCrystal crystal && s.crystal().enabled()) {
            details = crystalBreak(player, d, s, crystal, eye, box);
            check = details == null ? null : "CrystalAura";
        } else if (check == null && s.killAura().enabled()) {
            details = killAura(d, s.killAura(), eye, box, target);
            check = details == null ? null : "KillAura";
        }
        if (check != null) {
            event.setCancelled(true);
            plugin.flag(player, d, check, details);
        }
    }

    /**
     * A crystal's spawn packet goes out on the server tick after it is placed and takes half the
     * ping to arrive; the hit takes the other half to come back. A hit sooner than that is aimed
     * at a predicted entity ID, which only cheats do. Fast clicking is limited by rate instead.
     */
    private static String crystalBreak(Player player, PlayerData d, Settings s, EnderCrystal crystal,
                                       Location eye, BoundingBox box) {
        int age = crystal.getTicksLived();
        int ping = player.getPing();
        if (age == 0 || age * 50 + 50 < ping) {
            return String.format("hit a crystal %d ticks after it spawned, before the client could see it (ping %d ms)",
                    age, ping);
        }
        int now = Bukkit.getCurrentTick();
        int perSecond = d.crystalBreaks.record(now, 20);
        if (perSecond > s.crystal().maxBreaksPerSecond()) {
            return perSecond + " crystals broken in one second";
        }
        if (!s.killAura().enabled() || inCrosshair(eye, box, s.killAura().hitboxExpand())) {
            return null;
        }
        int misses = d.attackMisses.record(now, s.killAura().missWindowTicks());
        if (misses < s.killAura().maxMisses()) {
            return null;
        }
        d.attackMisses.clear();
        return String.format("%d crystal hits outside the crosshair within %d ticks", misses,
                s.killAura().missWindowTicks());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(EntityPlaceEvent event) {
        Player player = event.getPlayer();
        Settings s = plugin.settings();
        if (!(event.getEntity() instanceof EnderCrystal) || player == null || !s.crystal().enabled()
                || player.getGameMode() == GameMode.CREATIVE || player.hasPermission(MovementListener.BYPASS)) {
            return;
        }
        PlayerData d = plugin.data(player);
        int perSecond = d.crystalPlaces.record(Bukkit.getCurrentTick(), 20);
        if (perSecond > s.crystal().maxPlacesPerSecond()) {
            event.setCancelled(true);
            plugin.flag(player, d, "CrystalAura", perSecond + " crystals placed in one second");
        }
    }

    private static boolean inCrosshair(Location eye, BoundingBox box, double expand) {
        Vector start = eye.toVector();
        BoundingBox expanded = box.clone().expand(expand);
        return expanded.contains(start) || expanded.rayTrace(start, eye.getDirection(), 8.0) != null;
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

        if (inCrosshair(eye, targetBox, k.hitboxExpand())) {
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
