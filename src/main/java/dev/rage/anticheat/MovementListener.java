package dev.rage.anticheat;

import dev.rage.anticheat.logic.FlightLimits;
import dev.rage.anticheat.logic.MovePhysics;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
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

/** Runs the movement checks on every position change. */
final class MovementListener implements Listener {

    static final String BYPASS = "rageanticheat.bypass";

    /** Looked up by key: the field was renamed from JUMP to JUMP_BOOST in 1.20.5. */
    private static final PotionEffectType JUMP_BOOST =
            Objects.requireNonNull(PotionEffectType.getByKey(NamespacedKey.minecraft("jump_boost")));
    /** Looked up by key: renamed from generic.movement_speed to movement_speed in 1.21.3. */
    private static final Attribute MOVEMENT_SPEED = attribute("movement_speed", "generic.movement_speed");

    /** Slightly under the 0.3 player half-width so touching a wall does not count as ground. */
    private static final double HALF_WIDTH = 0.299;
    private static final double PLAYER_HEIGHT = 1.8;
    private static final double GROUND_EPSILON = 0.01;
    private static final double EXPLOSION_GRACE_RADIUS = 8;
    private static final int MAX_MERGED_TICKS = 20;
    private static final int MIN_USE_TICKS = 3;

    private record Flag(String check, String details) {
    }

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
        double dy = to.getY() - from.getY();
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        boolean fromGrounded = d.wasGrounded;
        boolean grounded = collides(world, d.scratch, to.getX(), to.getY() - GROUND_EPSILON, to.getZ(),
                HALF_WIDTH, GROUND_EPSILON * 2);
        boolean inLiquid = player.isInWater() || player.isInLava();
        refreshGrace(player, d, world, to, grounded, inLiquid, now, s.boostGraceTicks());
        boolean exempt = now < d.exemptUntil;
        boolean slowed = !grounded && touchesSlowingBlock(world, to);

        // Every check runs so its counters stay current; the first flag found is reported.
        Flag flag = checkTimer(d, s, exempt);
        // Paper skips events for moves under 1/16 block, so one event can cover several ticks.
        int ticks = Math.max(1, Math.min(MAX_MERGED_TICKS, now - d.lastMoveTick));
        d.lastMoveTick = now;
        Flag speed = checkSpeed(player, d, s, world, from, to, horizontal, ticks, dy, fromGrounded, grounded,
                inLiquid, exempt);
        if (exempt) {
            d.airborneTicks = 0;
            d.jesusTicks = 0;
            d.noFallTicks = 0;
        } else {
            Flag vertical = checkVertical(player, d, s, world, to, dy, fromGrounded, grounded, slowed, now);
            Flag jesus = checkJesus(player, d, s, world, to, dy, grounded);
            Flag step = checkStep(player, s, dy, fromGrounded, grounded);
            Flag noFall = checkNoFall(player, d, s, dy, grounded, inLiquid, slowed, now);
            flag = flag != null ? flag : vertical != null ? vertical : jesus != null ? jesus : step != null ? step : noFall;
        }
        if (flag == null) {
            flag = speed;
        }
        d.lastDy = dy;
        d.wasGrounded = grounded;

        if (flag != null) {
            setback(event, d, to);
            plugin.flag(player, d, flag.check(), flag.details());
        } else if ((grounded || inLiquid || player.isClimbing())
                && d.speed.lastMoveLegal()
                && d.timer.balance() < s.timerMaxAheadMs() / 2) {
            d.setSafe(to);
        }
    }

    // --- Checks. Each returns a Flag on violation, or null. ---

    /** Flight (rising, hovering, too long in the air) and Spider (the same while climbing a wall). */
    private static Flag checkVertical(Player p, PlayerData d, Settings s, World world, Location to, double dy,
                                      boolean fromGrounded, boolean grounded, boolean slowed, int now) {
        if (!s.flightEnabled() && !s.spiderEnabled()) {
            return null;
        }
        if (grounded || slowed || now < d.flightGraceUntil) {
            d.airborneTicks = 0;
            return null;
        }
        d.airborneTicks++;
        int jumpLevel = fromGrounded ? effectLevel(p, JUMP_BOOST) : 0;
        Flag flag = null;
        if (s.flight().violatesGravity(dy, d.lastDy, fromGrounded, jumpLevel)) {
            boolean wall = dy > 0 && collides(world, d.scratch, to.getX(), to.getY() + 0.1, to.getZ(),
                    HALF_WIDTH + 0.02, PLAYER_HEIGHT - 0.2);
            if (wall ? s.spiderEnabled() : s.flightEnabled()) {
                flag = new Flag(wall ? "Spider" : "Flight",
                        String.format("gravity: dy=%.3f lastDy=%.3f", dy, d.lastDy));
            }
        } else if (s.flightEnabled() && s.flight().exceedsAirtime(d.airborneTicks, dy)) {
            flag = new Flag("Flight", String.format("airtime: %d moves, dy=%.3f", d.airborneTicks, dy));
        }
        if (flag != null && standingOnEntity(p)) {
            d.airborneTicks = 0;
            return null;
        }
        return flag;
    }

    /** Predicts the fastest legal horizontal move from vanilla friction and acceleration. */
    private static Flag checkSpeed(Player p, PlayerData d, Settings s, World world, Location from, Location to,
                                   double horizontal, int ticks, double dy, boolean fromGrounded, boolean grounded,
                                   boolean inLiquid, boolean exempt) {
        if (!s.speedEnabled()) {
            return null;
        }
        // Use the higher of this and the last value: the client may still move faster for a tick
        // after the server removes a Speed effect or stops a sprint.
        double attribute = movementSpeed(p);
        double movementSpeed = Math.max(attribute, d.lastMovementSpeed);
        d.lastMovementSpeed = attribute;
        boolean sprinting = p.isSprinting();

        double friction;
        double acceleration;
        // The server's in-water state lags a tick behind, so water physics also need liquid at the feet.
        boolean liquidPhysics = inLiquid && isLiquid(world.getType(from.getBlockX(), from.getBlockY(), from.getBlockZ()));
        if (liquidPhysics && p.isInLava()) {
            friction = MovePhysics.LAVA_FRICTION;
            acceleration = MovePhysics.LIQUID_ACCELERATION + MovePhysics.FLOW_PUSH;
        } else if (liquidPhysics) {
            int strider = armorLevel(p.getInventory().getBoots(), Enchantment.DEPTH_STRIDER);
            boolean dolphin = p.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE);
            friction = MovePhysics.waterFriction(sprinting, dolphin, strider, fromGrounded);
            acceleration = MovePhysics.waterAcceleration(movementSpeed, strider, fromGrounded) + MovePhysics.FLOW_PUSH;
        } else if (fromGrounded) {
            friction = MovePhysics.groundFriction(slipperinessBelow(world, from, true));
            acceleration = MovePhysics.groundAcceleration(movementSpeed, slipperinessBelow(world, from, false));
        } else {
            friction = MovePhysics.AIR_FRICTION;
            acceleration = MovePhysics.airAcceleration(sprinting);
        }
        if (p.isSneaking() && p.getPose() == Pose.SNEAKING) {
            acceleration *= MovePhysics.sneakFactor(armorLevel(p.getInventory().getLeggings(), Enchantment.SWIFT_SNEAK));
        }
        boolean slowedByItem = !liquidPhysics && s.noSlowEnabled() && usingItem(p);
        if (slowedByItem) {
            acceleration *= MovePhysics.USE_ITEM_FACTOR;
        }

        if (exempt) {
            d.speed.carry(horizontal, friction);
            return null;
        }
        boolean sprintJump = fromGrounded && sprinting && dy >= 0
                && ((dy > 1e-4 && !grounded) || collides(world, d.scratch, to.getX(), to.getY() + PLAYER_HEIGHT,
                        to.getZ(), HALF_WIDTH, GROUND_EPSILON));
        d.speed.observe(horizontal, ticks, acceleration, friction, sprintJump, s.speedTolerance());
        if (!d.speed.exceeded(s.speedMaxExcess()) || nearPushingEntity(p)) {
            return null;
        }
        return new Flag(slowedByItem ? "NoSlow" : "Speed", String.format("moved=%.3f allowed=%.3f excess=%.3f blocks%s",
                horizontal, d.speed.lastAllowed(), d.speed.excess(), slowedByItem ? " while using an item" : ""));
    }

    /**
     * Eating, drinking, blocking with a shield, drawing a bow and other item use slows a vanilla
     * client to a fifth of its speed. Item use starts on the client before the server knows of it,
     * which only makes the check more lenient; it is skipped in the last ticks of eating, which
     * client and server may finish a tick apart.
     */
    private static boolean usingItem(Player p) {
        return p.isHandRaised() && p.getHandRaisedTime() >= MIN_USE_TICKS
                && p.getActiveItemRemainingTime() > MIN_USE_TICKS;
    }

    /** Standing on a liquid surface without sinking, as if it were solid. */
    private static Flag checkJesus(Player p, PlayerData d, Settings s, World world, Location to, double dy,
                                   boolean grounded) {
        if (!s.jesusEnabled() || grounded || Math.abs(dy) > 0.005 || !onLiquidSurface(world, to)) {
            d.jesusTicks = 0;
            return null;
        }
        if (++d.jesusTicks <= s.jesusMaxTicks() || standingOnEntity(p)) {
            return null;
        }
        return new Flag("Jesus", String.format("%d moves level on a liquid surface", d.jesusTicks));
    }

    /** Rising higher in one ground-to-ground move than a step or jump allows. */
    private static Flag checkStep(Player p, Settings s, double dy, boolean fromGrounded, boolean grounded) {
        if (!s.stepEnabled() || !fromGrounded || !grounded || dy <= s.stepMaxHeight() + GROUND_EPSILON) {
            return null;
        }
        double max = FlightLimits.maxGroundRise(s.stepMaxHeight(), effectLevel(p, JUMP_BOOST)) + GROUND_EPSILON;
        if (dy <= max) {
            return null;
        }
        return new Flag("Step", String.format("rose %.3f blocks in one move, max %.3f", dy, max));
    }

    /** The client claims to be on the ground while falling through air, to avoid fall damage. */
    private static Flag checkNoFall(Player p, PlayerData d, Settings s, double dy, boolean grounded,
                                    boolean inLiquid, boolean slowed, int now) {
        @SuppressWarnings("deprecation") // deliberately reading the client-controlled flag
        boolean clientGround = p.isOnGround();
        if (!s.noFallEnabled() || grounded || dy >= 0 || !clientGround || inLiquid || slowed
                || p.isClimbing() || now < d.flightGraceUntil) {
            d.noFallTicks = 0;
            return null;
        }
        if (++d.noFallTicks < s.noFallMaxTicks()) {
            return null;
        }
        if (standingOnEntity(p)) {
            d.noFallTicks = 0;
            return null;
        }
        return new Flag("NoFall", String.format("claimed ground %d moves in a row while falling, dy=%.3f",
                d.noFallTicks, dy));
    }

    /** More movement packets than real time allows. */
    private Flag checkTimer(PlayerData d, Settings s, boolean exempt) {
        if (!s.timerEnabled()) {
            return null;
        }
        long nanos = System.nanoTime();
        if (exempt || plugin.serverLagging(nanos)) {
            d.timer.reset();
            return null;
        }
        double ahead = d.timer.onPacket(nanos, s.timerMaxCreditMs());
        if (ahead <= s.timerMaxAheadMs()) {
            return null;
        }
        d.timer.afterFlag(s.timerMaxAheadMs());
        return new Flag("Timer", String.format("%.0f ms ahead of real time", ahead));
    }

    // --- Grace periods ---

    /** Extends grace periods while the player is in a state that legitimately breaks the limits. */
    private static void refreshGrace(Player p, PlayerData d, World world, Location to,
                                     boolean grounded, boolean inLiquid, int now, int graceTicks) {
        int until = now + graceTicks;
        GameMode mode = p.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR || p.getAllowFlight()
                || p.isGliding() || p.isInsideVehicle() || p.isRiptiding() || p.isDead()) {
            d.exemptUntil = Math.max(d.exemptUntil, until);
            return;
        }
        if (inLiquid || p.isClimbing()
                || p.hasPotionEffect(PotionEffectType.LEVITATION)
                || p.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
            d.flightGraceUntil = Math.max(d.flightGraceUntil, until);
        }
        if (grounded) {
            Material below = world.getType(to.getBlockX(), (int) Math.floor(to.getY() - 0.2), to.getBlockZ());
            if (below == Material.SLIME_BLOCK || Tag.BEDS.isTagged(below)) {
                d.flightGraceUntil = Math.max(d.flightGraceUntil, until); // bounce
            }
        }
    }

    private void knockbackGrace(Player player) {
        PlayerData d = plugin.data(player);
        int until = Bukkit.getCurrentTick() + plugin.settings().knockbackGraceTicks();
        d.exemptUntil = Math.max(d.exemptUntil, until);
    }

    // --- World queries. Block lookups use World#getType so the common all-air case does not allocate. ---

    /**
     * True when the box centred on x/z, from y up by {@code height}, overlaps any block collision shape.
     * One extra layer below is scanned because fences and walls reach 0.5 above their own block.
     */
    private static boolean collides(World world, BoundingBox box, double x, double y, double z,
                                    double halfWidth, double height) {
        box.resize(x - halfWidth, y, z - halfWidth, x + halfWidth, y + height, z + halfWidth);
        int minX = (int) Math.floor(box.getMinX());
        int maxX = (int) Math.floor(box.getMaxX());
        int minY = (int) Math.floor(box.getMinY()) - 1;
        int maxY = (int) Math.floor(box.getMaxY());
        int minZ = (int) Math.floor(box.getMinZ());
        int maxZ = (int) Math.floor(box.getMaxZ());
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

    /** Highest (or lowest) slipperiness of the blocks under the player's feet. */
    private static double slipperinessBelow(World world, Location loc, boolean highest) {
        int y = (int) Math.floor(loc.getY() - 0.5000001);
        double result = MovePhysics.DEFAULT_SLIPPERINESS;
        boolean found = false;
        for (int bx = (int) Math.floor(loc.getX() - 0.3); bx <= (int) Math.floor(loc.getX() + 0.3); bx++) {
            for (int bz = (int) Math.floor(loc.getZ() - 0.3); bz <= (int) Math.floor(loc.getZ() + 0.3); bz++) {
                Material m = world.getType(bx, y, bz);
                if (m.isAir()) {
                    continue;
                }
                double slip = slipperiness(m);
                result = !found ? slip : highest ? Math.max(result, slip) : Math.min(result, slip);
                found = true;
            }
        }
        return result;
    }

    private static double slipperiness(Material m) {
        if (m == Material.BLUE_ICE) {
            return 0.989;
        }
        if (m == Material.ICE || m == Material.PACKED_ICE || m == Material.FROSTED_ICE) {
            return 0.98;
        }
        if (m == Material.SLIME_BLOCK) {
            return 0.8;
        }
        return MovePhysics.DEFAULT_SLIPPERINESS;
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

    /** Feet just above or at a liquid surface with the body out of it. */
    private static boolean onLiquidSurface(World world, Location to) {
        int x = to.getBlockX();
        int z = to.getBlockZ();
        return isLiquid(world.getType(x, (int) Math.floor(to.getY() - 0.05), z))
                && !isLiquid(world.getType(x, (int) Math.floor(to.getY() + 0.5), z));
    }

    private static boolean isLiquid(Material m) {
        return m == Material.WATER || m == Material.LAVA || m == Material.BUBBLE_COLUMN
                || m == Material.KELP || m == Material.KELP_PLANT
                || m == Material.SEAGRASS || m == Material.TALL_SEAGRASS;
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

    /** Mobs, players and vehicles push players sideways. Only called on the rare violation path. */
    private static boolean nearPushingEntity(Player player) {
        for (Entity e : player.getNearbyEntities(1.0, 1.0, 1.0)) {
            if (e instanceof LivingEntity || e instanceof Vehicle) {
                return true;
            }
        }
        return false;
    }

    private static double movementSpeed(Player p) {
        AttributeInstance instance = p.getAttribute(MOVEMENT_SPEED);
        return instance == null ? 0.1 : instance.getValue();
    }

    private static int effectLevel(Player p, PotionEffectType type) {
        if (!p.hasPotionEffect(type)) {
            return 0;
        }
        PotionEffect effect = p.getPotionEffect(type);
        return effect == null ? 0 : effect.getAmplifier() + 1;
    }

    private static int armorLevel(ItemStack item, Enchantment enchantment) {
        return item == null ? 0 : item.getEnchantmentLevel(enchantment);
    }

    private static Attribute attribute(String... keys) {
        for (String key : keys) {
            Attribute attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
            if (attribute != null) {
                return attribute;
            }
        }
        throw new IllegalStateException("movement speed attribute not found");
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
        if (event.getEntity() instanceof Player player) {
            knockbackGrace(player);
        }
    }

    /** Explosion knockback (including wind charges) does not fire PlayerVelocityEvent. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        explosionGrace(event.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        explosionGrace(event.getBlock().getLocation());
    }

    private void explosionGrace(Location location) {
        for (Player player : location.getWorld().getNearbyPlayers(location, EXPLOSION_GRACE_RADIUS)) {
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
}
