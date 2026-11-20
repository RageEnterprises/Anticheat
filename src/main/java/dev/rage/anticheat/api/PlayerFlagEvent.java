package dev.rage.anticheat.api;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Called before any check, built-in or from an addon, adds a violation. Cancel it to ignore the
 * flag, for example while your minigame launches the player. The check has already undone the
 * cheat (a setback or a cancelled hit) when this is called; cancelling only skips the violation,
 * alerts and punishment.
 */
public final class PlayerFlagEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String check;
    private final String details;
    private boolean cancelled;

    public PlayerFlagEvent(Player player, String check, String details) {
        super(player, !Bukkit.isPrimaryThread());
        this.check = check;
        this.details = details;
    }

    /** The check's name, for example "Speed". */
    public String check() {
        return check;
    }

    /** What the check saw, as shown to staff. */
    public String details() {
        return details;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
