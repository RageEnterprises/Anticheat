package dev.rage.anticheat;

import dev.rage.anticheat.logic.Angles;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.util.Vector;

/** Block checks: Nuker. Flagged actions are cancelled. */
final class BlockListener implements Listener {

    private final RageAnticheat plugin;

    BlockListener(RageAnticheat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.hasPermission(MovementListener.BYPASS)) {
            return;
        }
        Settings s = plugin.settings();
        PlayerData d = plugin.data(player);
        String details = s.nuker().enabled() ? nuker(player, d, s.nuker(), event.getBlock()) : null;
        if (details != null) {
            event.setCancelled(true);
            plugin.flag(player, d, "Nuker", details);
        }
    }

    /** Too many blocks per tick or second, or a block the player cannot be looking at. */
    private static String nuker(Player player, PlayerData d, Settings.Nuker n, Block block) {
        int now = Bukkit.getCurrentTick();
        if (d.breakTick != now) {
            d.breakTick = now;
            d.breaksThisTick = 0;
        }
        int perTick = ++d.breaksThisTick;
        int perSecond = d.breaks.record(now, 20);
        if (perTick > n.maxPerTick()) {
            return perTick + " blocks in one tick";
        }
        if (perSecond > n.maxPerSecond()) {
            return perSecond + " blocks in one second";
        }

        Location eye = player.getEyeLocation();
        double dx = block.getX() + 0.5 - eye.getX();
        double dy = block.getY() + 0.5 - eye.getY();
        double dz = block.getZ() + 0.5 - eye.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance > n.maxDistance()) {
            return String.format("block %.2f blocks away, max %.2f", distance, n.maxDistance());
        }
        // Very close blocks can sit at a wide angle to the crosshair, so only check from 1.5 blocks out.
        if (distance > 1.5) {
            Vector look = eye.getDirection();
            double angle = Angles.between(look.getX(), look.getY(), look.getZ(), dx, dy, dz);
            if (angle > n.maxAngle()) {
                return String.format("block %.0f° away from where the player looks, max %.0f°", angle, n.maxAngle());
            }
        }
        return null;
    }
}
