package dev.rage.anticheat.api;

import dev.rage.anticheat.RageAnticheat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.jetbrains.annotations.ApiStatus;

/**
 * Entry point for addons. Get it with {@link #get()} in your plugin's {@code onEnable}, and add
 * {@code depend: [RageAnticheat]} to your plugin.yml so RageAnticheat is enabled first.
 */
public final class RageAnticheatApi {

    private final RageAnticheat core;
    /** Registered addon checks by lowercase name. */
    private final Map<String, Check> checks = new ConcurrentHashMap<>();

    /** Created once by RageAnticheat itself. */
    @ApiStatus.Internal
    public RageAnticheatApi(RageAnticheat core) {
        this.core = core;
        // Drop an addon's checks when it disables; Bukkit already removes their listeners.
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onDisable(PluginDisableEvent event) {
                checks.values().removeIf(check -> check.owner() == event.getPlugin());
            }
        }, core);
        Bukkit.getServicesManager().register(RageAnticheatApi.class, this, core, ServicePriority.Normal);
    }

    /**
     * The API of the running RageAnticheat.
     *
     * @throws IllegalStateException if RageAnticheat is not enabled
     */
    public static RageAnticheatApi get() {
        RageAnticheatApi api = find();
        if (api == null) {
            throw new IllegalStateException("RageAnticheat is not enabled; add depend: [RageAnticheat] to plugin.yml");
        }
        return api;
    }

    static RageAnticheatApi find() {
        return Bukkit.getServicesManager().load(RageAnticheatApi.class);
    }

    /**
     * Registers {@code check} as a listener of {@code owner} and lists it in /rageanticheat status.
     *
     * @throws IllegalArgumentException if the owner is not enabled, the name is taken, or the
     *                                  check is already registered
     */
    public void register(Plugin owner, Check check) {
        if (!owner.isEnabled()) {
            throw new IllegalArgumentException(owner.getName() + " is not enabled");
        }
        String key = check.name().toLowerCase(Locale.ROOT);
        for (String builtIn : core.builtInChecks()) {
            if (builtIn.equalsIgnoreCase(key)) {
                throw new IllegalArgumentException("A built-in check is called " + builtIn);
            }
        }
        if (check.owner() != null || checks.putIfAbsent(key, check) != null) {
            throw new IllegalArgumentException("A check called " + check.name() + " is already registered");
        }
        check.attach(owner);
        Bukkit.getPluginManager().registerEvents(check, owner);
        core.getLogger().info("Registered check " + check.name() + " from " + owner.getName());
    }

    /** Removes {@code check} and its event handlers. Returns false if it was not registered. */
    public boolean unregister(Check check) {
        if (!checks.remove(check.name().toLowerCase(Locale.ROOT), check)) {
            return false;
        }
        HandlerList.unregisterAll(check);
        return true;
    }

    /** The registered addon checks. */
    public List<Check> checks() {
        return new ArrayList<>(checks.values());
    }

    boolean enabled(Check check) {
        return checks.get(check.name().toLowerCase(Locale.ROOT)) == check && core.addonCheckEnabled(check.name());
    }

    /**
     * Reports a failed check for a player under any name, through the same pipeline as the
     * built-in checks. Prefer {@link Check#flag}, which also respects the server owner's
     * settings. Safe from any thread.
     */
    public void flag(Player player, String check, String details) {
        if (Bukkit.isPrimaryThread()) {
            core.flag(player, check, details);
        } else {
            Bukkit.getScheduler().runTask(core, () -> core.flag(player, check, details));
        }
    }

    /** How many violations the player has, after decay. Main thread only. */
    public int violations(Player player) {
        return core.violations(player);
    }

    /** True while the server is lagging or just recovered from it. */
    public boolean serverLagging() {
        return core.serverLagging();
    }
}
