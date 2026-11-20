package dev.rage.anticheat;

import dev.rage.anticheat.logic.KnockbackRun;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * Velocity (anti-knockback): after the server knocks a player back, their moves should carry them
 * along the knockback. The knockback reaches the client a ping later, so the moves are measured
 * until the ping plus a few ticks have passed. Players against a wall, in liquid, cobwebs,
 * vehicles or gliding are skipped, as walls and those stop knockback legitimately.
 */
final class VelocityListener implements Listener {

    /** Ticks of movement measured after the knockback should have arrived. */
    private static final int MEASURE_TICKS = 6;
    private static final int MAX_PING_MS = 1000;

    private final RageAnticheat plugin;

    VelocityListener(RageAnticheat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) {
        Player p = event.getPlayer();
        Settings.Velocity v = plugin.settings().velocity();
        Vector velocity = event.getVelocity();
        double horizontal = Math.hypot(velocity.getX(), velocity.getZ());
        PlayerData d = plugin.data(p);
        int id = ++d.knockbackId;
        if (!v.enabled() || horizontal < v.minKnockback() || skip(p)) {
            d.knockback.stop();
            return;
        }
        d.knockback.start(velocity.getX(), velocity.getZ());
        long delay = Math.min(p.getPing(), MAX_PING_MS) / 50 + MEASURE_TICKS;
        long started = System.nanoTime();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> judge(p, id, started), delay);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        PlayerData d = plugin.data(event.getPlayer());
        if (d.knockback.active()) {
            d.knockback.move(event.getTo().getX() - event.getFrom().getX(), event.getTo().getZ() - event.getFrom().getZ());
        }
    }

    private void judge(Player p, int id, long started) {
        if (!p.isOnline()) {
            return;
        }
        PlayerData d = plugin.data(p);
        KnockbackRun run = d.knockback;
        if (d.knockbackId != id || !run.active()) {
            return; // newer knockback, or a teleport or setback reset it
        }
        run.stop();
        Settings.Velocity v = plugin.settings().velocity();
        if (plugin.serverLagging(started) || plugin.serverLagging(System.nanoTime()) || skip(p) || blocked(p)) {
            return;
        }
        if (run.ratio() >= v.minRatio()) {
            d.velocityFails = 0;
            return;
        }
        if (++d.velocityFails < v.failsToFlag()) {
            return;
        }
        d.velocityFails = 0;
        plugin.flag(p, d, "Velocity", String.format("moved %.2f of %.2f blocks of knockback (%.0f%%, min %.0f%%)",
                run.distance(), run.expected(), run.ratio() * 100, v.minRatio() * 100));
    }

    private static boolean skip(Player p) {
        GameMode mode = p.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR || p.getAllowFlight() || p.isGliding()
                || p.isInsideVehicle() || p.isInWater() || p.isInLava() || p.isClimbing()
                || p.hasPermission(MovementListener.BYPASS)) {
            return true;
        }
        Location at = p.getLocation();
        World world = at.getWorld();
        Material feet = world.getType(at.getBlockX(), at.getBlockY(), at.getBlockZ());
        return feet == Material.COBWEB || feet == Material.POWDER_SNOW || feet == Material.SCAFFOLDING;
    }

    /** True if a wall is next to the player on any side; it may have stopped the knockback. */
    private static boolean blocked(Player p) {
        Location at = p.getLocation();
        BoundingBox box = new BoundingBox();
        for (double[] dir : new double[][] {{0.4, 0}, {-0.4, 0}, {0, 0.4}, {0, -0.4}}) {
            if (MovementListener.collides(at.getWorld(), box, at.getX() + dir[0], at.getY() + 0.1, at.getZ() + dir[1],
                    0.3, 1.6)) {
                return true;
            }
        }
        return false;
    }
}
