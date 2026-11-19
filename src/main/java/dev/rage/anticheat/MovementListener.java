package dev.rage.anticheat;

import dev.rage.anticheat.logic.SpeedLimits;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;

/** Runs the flight and speed checks on every position change. */
final class MovementListener implements Listener {

    static final String BYPASS = "rageanticheat.bypass";

    /** Looked up by key: the field was renamed from JUMP to JUMP_BOOST in 1.20.5. */
    private static final PotionEffectType JUMP_BOOST =
            Objects.requireNonNull(PotionEffectType.getByKey(NamespacedKey.minecraft("jump_boost")));

    /** Slightly under the 0.3 player half-width so touching a wall does not count as ground. */
    private static final double HALF_WIDTH = 0.299;
    private static final double PLAYER_HEIGHT = 1.8;

    private final RageAnticheat plugin;

    MovementListener(RageAnticheat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedPosition()) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        World world = to.getWorld();
        if (from.getWorld() != world) {
            return;
        }
        Player player = event.getPlayer();
        if (player.hasPermission(BYPASS)) {
            return;
        }

        Settings s = plugin.settings();
        PlayerData d = plugin.data(player);
        int now = Bukkit.getCurrentTick();
        boolean grounded = isGrounded(world, to.getX(), to.getY(), to.getZ(), d.scratch);
        boolean inLiquid = player.isInWater() || player.isInLava();
        refreshGrace(player, d, world, to, grounded, inLiquid, now, s.boostGraceTicks());

        double dy = to.getY() - from.getY();
        String check = null;
        String details = null;

        if (s.flightEnabled()) {
            if (grounded || now < d.flightGraceUntil || touchesSlowingBlock(world, to)) {
                d.airborneTicks = 0;
            } else {
                d.airborneTicks++;
                int jumpLevel = d.wasGrounded ? effectLevel(player, JUMP_BOOST) : 0;
                if (s.flight().violatesGravity(dy, d.lastDy, d.wasGrounded, jumpLevel)) {
                    check = "Flight";
                    details = String.format("gravity: dy=%.3f lastDy=%.3f", dy, d.lastDy);
                } else if (s.flight().exceedsAirtime(d.airborneTicks, dy)) {
                    check = "Flight";
                    details = String.format("airtime: %d ticks, dy=%.3f", d.airborneTicks, dy);
                }
                if (check != null && standingOnEntity(player)) {
                    check = null;
                    d.airborneTicks = 0;
                }
            }
        }
        d.lastDy = dy;
        d.wasGrounded = grounded;

        if (s.speedEnabled()) {
            if (now < d.speedGraceUntil) {
                d.distances.clear();
                d.limits.clear();
            } else {
                double dx = to.getX() - from.getX();
                double dz = to.getZ() - from.getZ();
                d.distances.add(Math.sqrt(dx * dx + dz * dz));
                d.limits.add(s.speed().max(inLiquid, player.isSneaking(), player.isSprinting(),
                        effectLevel(player, PotionEffectType.SPEED)));
                if (check == null && SpeedLimits.exceeds(d.distances, d.limits)) {
                    check = "Speed";
                    details = String.format("avg=%.3f max=%.3f blocks/tick",
                            d.distances.average(), d.limits.max());
                }
            }
        }

        if (check != null) {
            setback(event, d, to);
            plugin.flag(player, d, check, details);
        } else if (grounded || inLiquid || player.isClimbing()) {
            d.setSafe(to);
        }
    }

    /** Extends grace periods while the player is in a state that legitimately breaks the limits. */
    private static void refreshGrace(Player p, PlayerData d, World world, Location to,
                                     boolean grounded, boolean inLiquid, int now, int graceTicks) {
        int until = now + graceTicks;
        GameMode mode = p.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR || p.getAllowFlight()
                || p.isGliding() || p.isInsideVehicle() || p.isRiptiding() || p.isDead()) {
            d.flightGraceUntil = Math.max(d.flightGraceUntil, until);
            d.speedGraceUntil = Math.max(d.speedGraceUntil, until);
            return;
        }
        if (inLiquid || p.isClimbing()
                || p.hasPotionEffect(PotionEffectType.LEVITATION)
                || p.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
            d.flightGraceUntil = Math.max(d.flightGraceUntil, until);
        }
        if (inLiquid && (p.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE)
                || bootLevel(p, Enchantment.DEPTH_STRIDER) > 0)) {
            d.speedGraceUntil = Math.max(d.speedGraceUntil, until);
        }
        if (grounded) {
            Material below = world.getType(to.getBlockX(), (int) Math.floor(to.getY() - 0.2), to.getBlockZ());
            if (Tag.ICE.isTagged(below)
                    || (Tag.SOUL_SPEED_BLOCKS.isTagged(below) && bootLevel(p, Enchantment.SOUL_SPEED) > 0)) {
                d.speedGraceUntil = Math.max(d.speedGraceUntil, until);
            }
            if (below == Material.SLIME_BLOCK || Tag.BEDS.isTagged(below)) {
                d.flightGraceUntil = Math.max(d.flightGraceUntil, until);
            }
        }
    }

    /** Server-side ground test against block collision shapes; the client's onGround flag is ignored. */
    private static boolean isGrounded(World world, double x, double y, double z, BoundingBox box) {
        box.resize(x - HALF_WIDTH, y - 0.06, z - HALF_WIDTH, x + HALF_WIDTH, y + 0.01, z + HALF_WIDTH);
        int minX = (int) Math.floor(box.getMinX());
        int maxX = (int) Math.floor(box.getMaxX());
        int minZ = (int) Math.floor(box.getMinZ());
        int maxZ = (int) Math.floor(box.getMaxZ());
        // One level lower too: fences and walls stick 0.5 blocks above their own block.
        int minY = (int) Math.floor(box.getMinY()) - 1;
        int maxY = (int) Math.floor(box.getMaxY());
        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                for (int by = minY; by <= maxY; by++) {
                    if (world.getType(bx, by, bz).isAir()) {
                        continue;
                    }
                    box.shift(-bx, -by, -bz); // collision shapes are relative to the block
                    boolean hit = world.getBlockAt(bx, by, bz).getCollisionShape().overlaps(box);
                    box.shift(bx, by, bz);
                    if (hit) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Blocks that slow falling without being ground: cobwebs, powder snow, berry bushes, honey walls. */
    private static boolean touchesSlowingBlock(World world, Location to) {
        double x = to.getX();
        double y = to.getY();
        double z = to.getZ();
        for (int bx = (int) Math.floor(x - 0.31); bx <= (int) Math.floor(x + 0.31); bx++) {
            for (int bz = (int) Math.floor(z - 0.31); bz <= (int) Math.floor(z + 0.31); bz++) {
                for (int by = (int) Math.floor(y - 0.01); by <= (int) Math.floor(y + PLAYER_HEIGHT); by++) {
                    Material m = world.getType(bx, by, bz);
                    if (m == Material.COBWEB || m == Material.POWDER_SNOW
                            || m == Material.SWEET_BERRY_BUSH || m == Material.HONEY_BLOCK) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Boats and shulkers can be stood on. Only called on the rare violation path. */
    private static boolean standingOnEntity(Player player) {
        for (Entity e : player.getNearbyEntities(1.5, 2.0, 1.5)) {
            if (e instanceof Boat || e instanceof Shulker) {
                return true;
            }
        }
        return false;
    }

    private static int effectLevel(Player p, PotionEffectType type) {
        if (!p.hasPotionEffect(type)) {
            return 0;
        }
        PotionEffect effect = p.getPotionEffect(type);
        return effect == null ? 0 : effect.getAmplifier() + 1;
    }

    private static int bootLevel(Player p, Enchantment enchantment) {
        ItemStack boots = p.getInventory().getBoots();
        return boots == null ? 0 : boots.getEnchantmentLevel(enchantment);
    }

    private static void setback(PlayerMoveEvent event, PlayerData d, Location to) {
        Location safe = d.safeLocation(to);
        if (safe != null) {
            event.setTo(safe);
        } else {
            event.setCancelled(true);
        }
        d.resetMovement();
    }

    // --- Events that legitimately move the player or invalidate movement history ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) {
        knockbackGrace(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        // Explosion knockback does not fire PlayerVelocityEvent, so any damage also grants grace.
        if (event.getEntity() instanceof Player player) {
            knockbackGrace(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        PlayerData d = plugin.data(event.getPlayer());
        d.resetMovement();
        d.setSafe(event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        PlayerData d = plugin.data(event.getPlayer());
        d.resetMovement();
        d.setSafe(event.getRespawnLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        PlayerData d = plugin.data(event.getPlayer());
        d.resetMovement();
        d.setSafe(event.getPlayer().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.forget(event.getPlayer());
    }

    private void knockbackGrace(Player player) {
        PlayerData d = plugin.data(player);
        int until = Bukkit.getCurrentTick() + plugin.settings().knockbackGraceTicks();
        d.flightGraceUntil = Math.max(d.flightGraceUntil, until);
        d.speedGraceUntil = Math.max(d.speedGraceUntil, until);
    }
}
