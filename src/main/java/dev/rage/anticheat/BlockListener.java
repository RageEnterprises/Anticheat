package dev.rage.anticheat;

import dev.rage.anticheat.logic.Angles;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** Block checks: Nuker and Scaffold (flagged actions are cancelled) and Xray (alerts). */
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
            return;
        }
        if (s.xray().enabled()) {
            xray(player, d, s.xray(), event.getBlock());
        }
    }

    /** X-ray is a pattern over many blocks, so it alerts staff (or adds a violation) but never cancels. */
    private void xray(Player player, PlayerData d, Settings.Xray x, Block block) {
        Material type = block.getType();
        if (x.stone().contains(type)) {
            d.xray.mineStone();
            return;
        }
        if (!x.ores().contains(type) || exposed(block)) {
            return;
        }
        if (!d.xray.mineHiddenOre(x.minOres(), x.maxOresPerStone())) {
            return;
        }
        String details = String.format("%d hidden ores for %d stone (%.3f per stone, max %.3f)",
                d.xray.hiddenOres(), d.xray.stone(), d.xray.ratio(), x.maxOresPerStone());
        d.xray.reset();
        if (x.punish()) {
            plugin.flag(player, d, "Xray", details);
        } else {
            plugin.getLogger().info(player.getName() + " suspected of Xray (" + details + ")");
            plugin.alert(player, "Xray", details, "?");
        }
    }

    /** True if any side of the block touches air or liquid, so the player could have seen it. */
    private static boolean exposed(Block block) {
        for (BlockFace face : SIDES) {
            Material m = block.getRelative(face).getType();
            if (m.isAir() || m == Material.WATER || m == Material.LAVA) {
                return true;
            }
        }
        return false;
    }

    private static final BlockFace[] SIDES = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Settings s = plugin.settings();
        if (!s.scaffold().enabled() || player.getGameMode() == GameMode.CREATIVE
                || player.hasPermission(MovementListener.BYPASS)) {
            return;
        }
        PlayerData d = plugin.data(player);
        String details = scaffold(player, d, s.scaffold(), event.getBlockPlaced(), event.getBlockAgainst());
        if (details != null) {
            event.setCancelled(true);
            plugin.flag(player, d, "Scaffold", details);
        }
    }

    /**
     * A vanilla client places a block against the face under its crosshair. Scaffold cheats place
     * blocks under the player while looking elsewhere. Single misses happen when the player turns
     * just as they click, so only repeated misses are flagged.
     */
    private static String scaffold(Player player, PlayerData d, Settings.Scaffold sc, Block placed, Block against) {
        int now = Bukkit.getCurrentTick();
        int perSecond = d.places.record(now, 20);
        if (perSecond > sc.maxPerSecond()) {
            return perSecond + " blocks placed in one second";
        }
        BlockFace face = against.getFace(placed);
        if (face == null || placed.equals(against)) {
            return null; // replaced a block such as tall grass; no face to compare
        }
        RayTraceResult hit = player.rayTraceBlocks(6.0);
        boolean looking = hit != null && against.equals(hit.getHitBlock()) && face == hit.getHitBlockFace();
        if (looking) {
            return null;
        }
        int misses = d.placeMisses.record(now, sc.missWindowTicks());
        if (misses < sc.maxMisses()) {
            return null;
        }
        d.placeMisses.clear();
        return String.format("%d blocks placed against a face the player was not looking at (looking at %s)",
                misses, hit == null || hit.getHitBlock() == null ? "nothing" : hit.getHitBlockFace());
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
