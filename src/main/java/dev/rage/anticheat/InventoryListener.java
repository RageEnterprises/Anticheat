package dev.rage.anticheat;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * AutoTotem: after a totem of undying pops, a cheat moves the next totem into the off hand within
 * milliseconds. Each inventory click event is one click packet from the client, so the time from
 * the pop to the click that refills the off hand, minus the ping, is the player's reaction time.
 */
final class InventoryListener implements Listener {

    /** PlayerInventory slot of the off hand. */
    private static final int OFFHAND_SLOT = 40;
    /** Only the first refill within this time after a pop is judged. */
    private static final long POP_WINDOW_NANOS = 3_000_000_000L;

    private final RageAnticheat plugin;

    InventoryListener(RageAnticheat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent event) {
        if (event.getEntity() instanceof Player player && plugin.settings().autoTotem().enabled()) {
            plugin.data(player).totemPopNanos = System.nanoTime();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        Settings.AutoTotem t = plugin.settings().autoTotem();
        if (!t.enabled() || !(event.getWhoClicked() instanceof Player player)
                || player.getGameMode() == GameMode.CREATIVE || player.hasPermission(MovementListener.BYPASS)) {
            return;
        }
        PlayerData d = plugin.data(player);
        long now = System.nanoTime();
        if (now - d.totemPopNanos > POP_WINDOW_NANOS || !refillsOffhand(event, player)) {
            return;
        }
        long elapsedMs = (now - d.totemPopNanos) / 1_000_000L;
        d.totemPopNanos = Long.MIN_VALUE / 2;
        if (plugin.serverLagging(now)) {
            return; // the pop and the click may have been handled in one burst
        }
        long reactionMs = elapsedMs - player.getPing();
        if (reactionMs < t.minReactionMs()) {
            event.setCancelled(true);
            plugin.flag(player, d, "AutoTotem", String.format(
                    "refilled the off hand %d ms after a totem pop (ping %d ms, reaction %d ms, min %d)",
                    elapsedMs, player.getPing(), reactionMs, t.minReactionMs()));
        }
    }

    /** True if this click moves a totem of undying into the off hand. */
    private static boolean refillsOffhand(InventoryClickEvent event, Player player) {
        PlayerInventory inventory = player.getInventory();
        if (event.getClick() == ClickType.SWAP_OFFHAND) {
            return isTotem(event.getCurrentItem()); // F while hovering a totem
        }
        if (event.getClickedInventory() != inventory || event.getSlot() != OFFHAND_SLOT) {
            return false;
        }
        if (event.getClick() == ClickType.NUMBER_KEY) {
            return isTotem(inventory.getItem(event.getHotbarButton()));
        }
        return isTotem(event.getCursor());
    }

    private static boolean isTotem(ItemStack item) {
        return item != null && item.getType() == Material.TOTEM_OF_UNDYING;
    }
}
