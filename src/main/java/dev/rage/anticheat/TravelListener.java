package dev.rage.anticheat;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

/**
 * Movement the normal movement checks skip: elytra flight (ElytraFly) and vehicles (BoatFly,
 * EntitySpeed).
 *
 * <p>Gliding trades height for speed and back, and drag only takes energy away. So without a
 * firework, knockback or riptide, a player's height plus the height their speed could buy
 * ({@code v² / 2g}) never rises above where it was. ElytraFly cheats climb or hover anyway.
 */
final class TravelListener implements Listener {

    /** Gravity while gliding, in blocks per tick squared. */
    private static final double GRAVITY = 0.08;
    /** A firework boosts for up to about 45 ticks; the extra speed lasts a little longer. */
    private static final int FIREWORK_TICKS = 70;
    /** A hovering glider moves less than this in a second; a real one sinks at least a block. */
    private static final double HOVER_MAX_DISTANCE = 1.0;
    private static final double HOVER_MAX_DROP = 0.5;
    /** A vehicle not seen for this long starts a fresh measurement. */
    private static final int MAX_VEHICLE_GAP_TICKS = 20;
    private static final Attribute MOVEMENT_SPEED = attribute("movement_speed", "generic.movement_speed");

    private final RageAnticheat plugin;

    TravelListener(RageAnticheat plugin) {
        this.plugin = plugin;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkHovering, 20L, 20L);
    }

    private static boolean skip(Player p) {
        return p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR || p.getAllowFlight()
                || p.hasPermission(MovementListener.BYPASS);
    }

    /** Fireworks, knockback, riptide and levitation all add energy. */
    private static boolean boosted(Player p, PlayerData d, int now) {
        return now < d.glideBoostUntil || now < d.knockbackUntil || p.isRiptiding()
                || p.hasPotionEffect(PotionEffectType.LEVITATION);
    }

    // --- ElytraFly ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFirework(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item != null && item.getType() == Material.FIREWORK_ROCKET
                && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            plugin.data(event.getPlayer()).glideBoostUntil = Bukkit.getCurrentTick() + FIREWORK_TICKS;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGlide(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        PlayerData d = plugin.data(p);
        Settings.Elytra e = plugin.settings().elytra();
        if (!p.isGliding() || !e.enabled() || skip(p) || event.getFrom().getWorld() != event.getTo().getWorld()) {
            d.glidePeak = Double.NaN;
            d.glideHover = null;
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        int now = Bukkit.getCurrentTick();
        int ticks = Math.max(1, Math.min(MAX_VEHICLE_GAP_TICKS, now - d.glideLastTick));
        d.glideLastTick = now;
        double dx = (to.getX() - from.getX()) / ticks;
        double dy = (to.getY() - from.getY()) / ticks;
        double dz = (to.getZ() - from.getZ()) / ticks;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double energy = to.getY() + (horizontal * horizontal + dy * dy) / (2 * GRAVITY);
        if (boosted(p, d, now) || Double.isNaN(d.glidePeak)) {
            d.glidePeak = Double.isNaN(d.glidePeak) ? energy : Math.max(d.glidePeak, energy);
            return;
        }
        String details = null;
        if (horizontal > e.maxSpeed()) {
            details = String.format("glided %.2f blocks per tick without a boost, max %.2f", horizontal, e.maxSpeed());
        } else if (energy > d.glidePeak + e.climbMargin()) {
            details = String.format("gained %.1f blocks of height and speed without a boost", energy - d.glidePeak);
        }
        if (details != null) {
            stopGliding(p, d);
            event.setTo(from);
            plugin.flag(p, d, "ElytraFly", details);
        }
    }

    /** A glider that holds still sends no move packets, so hovering is checked once a second. */
    private void checkHovering() {
        Settings.Elytra e = plugin.settings().elytra();
        if (!e.enabled()) {
            return;
        }
        int now = Bukkit.getCurrentTick();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.isGliding() || skip(p)) {
                continue;
            }
            PlayerData d = plugin.data(p);
            Location here = p.getLocation();
            Location before = d.glideHover;
            d.glideHover = here;
            if (before == null || before.getWorld() != here.getWorld() || boosted(p, d, now)
                    || plugin.serverLagging(System.nanoTime())) {
                continue;
            }
            double drop = before.getY() - here.getY();
            double dx = here.getX() - before.getX();
            double dz = here.getZ() - before.getZ();
            if (drop < HOVER_MAX_DROP && Math.sqrt(dx * dx + dz * dz) < HOVER_MAX_DISTANCE) {
                stopGliding(p, d);
                plugin.flag(p, d, "ElytraFly", String.format("hovered for a second while gliding (dropped %.2f blocks)", drop));
            }
        }
    }

    private static void stopGliding(Player p, PlayerData d) {
        p.setGliding(false);
        d.glidePeak = Double.NaN;
        d.glideHover = null;
    }

    // --- BoatFly and EntitySpeed ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnter(VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player player) {
            plugin.data(player).vehicleTick = -1;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVehicleMove(VehicleMoveEvent event) {
        Vehicle vehicle = event.getVehicle();
        Settings.Vehicles v = plugin.settings().vehicles();
        if (!v.enabled() || vehicle.getPassengers().isEmpty()
                || !(vehicle.getPassengers().get(0) instanceof Player p) || skip(p)) {
            return;
        }
        boolean boat = vehicle instanceof Boat;
        boolean mount = vehicle instanceof AbstractHorse && vehicle.getType() != EntityType.CAMEL;
        if (!boat && !mount) {
            return;
        }
        Location to = event.getTo();
        World world = to.getWorld();
        PlayerData d = plugin.data(p);
        int now = Bukkit.getCurrentTick();
        int ticks = now - d.vehicleTick;
        double dx = to.getX() - d.vehicleX;
        double dy = to.getY() - d.vehicleY;
        double dz = to.getZ() - d.vehicleZ;
        boolean fresh = d.vehicleTick < 0 || ticks > MAX_VEHICLE_GAP_TICKS || event.getFrom().getWorld() != world;
        if (ticks <= 0 && !fresh) {
            return; // a second event in the same tick; measure from the first
        }
        d.vehicleTick = now;
        d.vehicleX = to.getX();
        d.vehicleY = to.getY();
        d.vehicleZ = to.getZ();
        if (fresh || now < d.knockbackUntil || plugin.serverLagging(System.nanoTime())) {
            d.vehicleAirMoves = 0;
            return;
        }
        double horizontal = Math.sqrt(dx * dx + dz * dz) / ticks;
        double max = boat ? (onIce(world, to) ? v.maxBoatIceSpeed() : v.maxBoatSpeed())
                : mountSpeed(vehicle) * v.mountSpeedFactor() + 0.1;
        String check = null;
        String details = null;
        if (horizontal > max) {
            check = "EntitySpeed";
            details = String.format("%s moved %.2f blocks per tick, max %.2f",
                    vehicle.getType().name().toLowerCase(java.util.Locale.ROOT), horizontal, max);
        } else if (boat) {
            boolean supported = MovementListener.collides(world, d.scratch, to.getX(), to.getY() - 0.1, to.getZ(), 0.7, 0.15)
                    || MovementListener.isLiquid(world.getType(to.getBlockX(), (int) Math.floor(to.getY() - 0.1), to.getBlockZ()))
                    || MovementListener.isLiquid(world.getType(to.getBlockX(), to.getBlockY(), to.getBlockZ()));
            d.vehicleAirMoves = supported || dy < -0.02 * ticks ? 0 : d.vehicleAirMoves + 1;
            if (d.vehicleAirMoves > v.maxBoatAirMoves()) {
                check = "BoatFly";
                details = d.vehicleAirMoves + " moves in the air without falling";
                d.vehicleAirMoves = 0;
            }
        }
        if (check == null) {
            return;
        }
        Location back = event.getFrom();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            p.leaveVehicle();
            vehicle.teleport(back);
        });
        plugin.flag(p, d, check, details);
    }

    private static boolean onIce(World world, Location at) {
        int y = (int) Math.floor(at.getY() - 0.1);
        for (int bx = (int) Math.floor(at.getX() - 0.7); bx <= (int) Math.floor(at.getX() + 0.7); bx++) {
            for (int bz = (int) Math.floor(at.getZ() - 0.7); bz <= (int) Math.floor(at.getZ() + 0.7); bz++) {
                Material m = world.getType(bx, y, bz);
                if (m == Material.ICE || m == Material.PACKED_ICE || m == Material.BLUE_ICE || m == Material.FROSTED_ICE) {
                    return true;
                }
            }
        }
        return false;
    }

    private static double mountSpeed(Entity mount) {
        AttributeInstance speed = mount instanceof AbstractHorse horse ? horse.getAttribute(MOVEMENT_SPEED) : null;
        return speed == null ? 0.3375 : speed.getValue();
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
}
