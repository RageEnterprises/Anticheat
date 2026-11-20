package dev.rage.anticheat.api;

import java.util.Locale;
import java.util.regex.Pattern;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * Base class for a check made by an addon. Extend it, add {@code @EventHandler} methods like in
 * any Bukkit listener, call {@link #flag} when a player cheats, and register it once:
 *
 * <pre>{@code
 * public final class MyAddon extends JavaPlugin {
 *     public void onEnable() {
 *         RageAnticheatApi.get().register(this, new FastBowCheck());
 *     }
 * }
 * }</pre>
 *
 * <p>A flag goes through the same pipeline as the built-in checks: the violation counter, staff
 * alerts, Discord, the player's profile, and the kick or ban at the threshold. The check is
 * unregistered automatically when the addon disables. Server owners can turn it off by adding its
 * name to {@code addons.disabled} in the RageAnticheat config.
 */
public abstract class Check implements Listener {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");

    private final String name;
    private volatile Plugin owner;

    /**
     * @param name shown in alerts, for example "FastBow": 1 to 32 letters, digits, '_' or '-'.
     *             Must not be the name of a built-in check or another addon's check.
     */
    protected Check(String name) {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid check name: " + name);
        }
        this.name = name;
    }

    public final String name() {
        return name;
    }

    /** The addon that registered this check, or null before it is registered. */
    public final Plugin owner() {
        return owner;
    }

    final void attach(Plugin plugin) {
        owner = plugin;
    }

    /** False when unregistered, or when the server owner turned this check off. */
    public final boolean enabled() {
        RageAnticheatApi api = RageAnticheatApi.find();
        return api != null && api.enabled(this);
    }

    /**
     * True when the check should leave this player alone: the check is off, or the player has
     * {@code rageanticheat.bypass} or {@code rageanticheat.bypass.<name>}. Call it at the start of
     * each handler.
     */
    protected boolean exempt(Player player) {
        return !enabled() || player.hasPermission("rageanticheat.bypass")
                || player.hasPermission("rageanticheat.bypass." + name.toLowerCase(Locale.ROOT));
    }

    /**
     * Reports that {@code player} failed this check. Undo the cheat yourself first, for example
     * by cancelling the event. Ignored while the check is disabled. Safe from any thread.
     *
     * @param details what was seen, shown to staff, for example "drew a bow in 2 ticks"
     */
    protected final void flag(Player player, String details) {
        RageAnticheatApi api = RageAnticheatApi.find();
        if (api != null && api.enabled(this)) {
            api.flag(player, name, details);
        }
    }

    /**
     * True while the server is lagging or just recovered. Packets then arrive in bursts, so skip
     * checks based on timing.
     */
    protected final boolean serverLagging() {
        RageAnticheatApi api = RageAnticheatApi.find();
        return api != null && api.serverLagging();
    }
}
