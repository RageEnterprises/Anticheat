package com.example.rageaddon;

import dev.rage.anticheat.api.Check;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Flags taking an item out of a chest faster than a person can react to it opening: the time
 * from the server opening the chest to the first click, minus the player's ping.
 */
final class ChestStealerCheck extends Check {

    private final long minReactionMs;
    /** When each player's current chest was opened, in System.nanoTime. */
    private final Map<UUID, Long> openedAt = new ConcurrentHashMap<>();

    ChestStealerCheck(long minReactionMs) {
        super("ChestStealer");
        this.minReactionMs = minReactionMs;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        InventoryType type = event.getInventory().getType();
        if (type == InventoryType.CHEST || type == InventoryType.BARREL || type == InventoryType.SHULKER_BOX) {
            openedAt.put(event.getPlayer().getUniqueId(), System.nanoTime());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        // Only the first click after opening is timed.
        Long opened = openedAt.remove(player.getUniqueId());
        if (opened == null || exempt(player) || player.getGameMode() == GameMode.CREATIVE || serverLagging()) {
            return;
        }
        // Only taking items out of the chest.
        if (event.getClickedInventory() != event.getView().getTopInventory() || event.getCurrentItem() == null
                || event.getCurrentItem().getType().isAir()) {
            return;
        }
        long reactionMs = (System.nanoTime() - opened) / 1_000_000 - player.getPing();
        if (reactionMs < minReactionMs) {
            event.setCancelled(true);
            flag(player, "took an item " + reactionMs + " ms after the chest opened (ping " + player.getPing() + " ms)");
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        openedAt.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        openedAt.remove(event.getPlayer().getUniqueId());
    }
}
