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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Inventory checks. Each inventory click event is one click packet from the client, handled
 * between ticks, so the time between clicks, or from an event to a click, is real time.
 *
 * <ul>
 *   <li>AutoTotem: the time from a totem popping to the click that refills the off hand.</li>
 *   <li>ChestStealer: the time from a chest opening to the first item taken out of it.</li>
 *   <li>AutoArmor: armor pieces put on in quicker succession than a person can click.</li>
 * </ul>
 *
 * Reaction times subtract the ping, because the client only sees the event half a ping later and
 * the click takes the other half to come back. Flagged clicks are cancelled.
 */
final class InventoryListener implements Listener {

    /** PlayerInventory slot of the off hand. */
    private static final int OFFHAND_SLOT = 40;
    /** Only the first refill within this time after a pop is judged. */
    private static final long POP_WINDOW_NANOS = 3_000_000_000L;
    /** Quick armor pieces in a row that flag: three pieces with two quick gaps. */
    private static final int QUICK_ARMOR_TO_FLAG = 2;

    private record Flag(String check, String details) {
    }

    private final RageAnticheat plugin;

    InventoryListener(RageAnticheat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent event) {
        if (event.getEntity() instanceof Player player) {
            plugin.data(player).totemPopNanos = System.nanoTime();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        InventoryType type = event.getInventory().getType();
        if (event.getPlayer() instanceof Player player && (type == InventoryType.CHEST || type == InventoryType.BARREL
                || type == InventoryType.SHULKER_BOX || type == InventoryType.ENDER_CHEST)) {
            plugin.data(player).containerOpenNanos = System.nanoTime();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            plugin.data(player).containerOpenNanos = 0;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || player.getGameMode() == GameMode.CREATIVE || player.hasPermission(MovementListener.BYPASS)) {
            return;
        }
        Settings s = plugin.settings();
        PlayerData d = plugin.data(player);
        long now = System.nanoTime();
        boolean lagging = plugin.serverLagging(now);
        Flag flag = s.autoTotem().enabled() ? autoTotem(event, player, d, s.autoTotem(), now, lagging) : null;
        if (flag == null && s.chestStealer().enabled()) {
            flag = chestStealer(event, player, d, s.chestStealer(), now, lagging);
        }
        if (flag == null && s.autoArmor().enabled()) {
            flag = autoArmor(event, player, d, s.autoArmor(), now, lagging);
        }
        if (flag != null) {
            event.setCancelled(true);
            plugin.flag(player, d, flag.check(), flag.details());
        }
    }

    private static Flag autoTotem(InventoryClickEvent event, Player player, PlayerData d, Settings.AutoTotem t,
                                  long now, boolean lagging) {
        if (now - d.totemPopNanos > POP_WINDOW_NANOS || !refillsOffhand(event, player)) {
            return null;
        }
        long elapsedMs = (now - d.totemPopNanos) / 1_000_000L;
        d.totemPopNanos = Long.MIN_VALUE / 2;
        if (lagging) {
            return null; // the pop and the click may have been handled in one burst
        }
        long reactionMs = elapsedMs - player.getPing();
        if (reactionMs >= t.minReactionMs()) {
            return null;
        }
        return new Flag("AutoTotem", String.format(
                "refilled the off hand %d ms after a totem pop (ping %d ms, reaction %d ms, min %d)",
                elapsedMs, player.getPing(), reactionMs, t.minReactionMs()));
    }

    /** Only the first click after opening is timed; the cursor starts in the middle of the screen. */
    private static Flag chestStealer(InventoryClickEvent event, Player player, PlayerData d, Settings.ChestStealer c,
                                     long now, boolean lagging) {
        if (d.containerOpenNanos == 0) {
            return null;
        }
        long elapsedMs = (now - d.containerOpenNanos) / 1_000_000L;
        d.containerOpenNanos = 0;
        ItemStack taken = event.getCurrentItem();
        if (lagging || event.getClickedInventory() != event.getView().getTopInventory()
                || taken == null || taken.getType().isAir()) {
            return null;
        }
        long reactionMs = elapsedMs - player.getPing();
        if (reactionMs >= c.minReactionMs()) {
            return null;
        }
        return new Flag("ChestStealer", String.format(
                "took an item %d ms after the chest opened (ping %d ms, reaction %d ms, min %d)",
                elapsedMs, player.getPing(), reactionMs, c.minReactionMs()));
    }

    private static Flag autoArmor(InventoryClickEvent event, Player player, PlayerData d, Settings.AutoArmor a,
                                  long now, boolean lagging) {
        if (!equipsArmor(event, player)) {
            return null;
        }
        long gapMs = (now - d.lastArmorNanos) / 1_000_000L;
        d.lastArmorNanos = now;
        d.quickArmor = !lagging && gapMs < a.minIntervalMs() ? d.quickArmor + 1 : 0;
        if (d.quickArmor < QUICK_ARMOR_TO_FLAG) {
            return null;
        }
        d.quickArmor = 0;
        return new Flag("AutoArmor", String.format("put on %d armor pieces less than %d ms apart",
                QUICK_ARMOR_TO_FLAG + 1, a.minIntervalMs()));
    }

    /** True if this click puts a piece of armor (or an elytra) into an empty armor slot. */
    private static boolean equipsArmor(InventoryClickEvent event, Player player) {
        PlayerInventory inventory = player.getInventory();
        if (event.getSlotType() == SlotType.ARMOR) {
            ItemStack item = event.getClick() == ClickType.NUMBER_KEY
                    ? inventory.getItem(event.getHotbarButton()) : event.getCursor();
            return armor(item) != null;
        }
        if (!event.isShiftClick() || event.getClickedInventory() != inventory) {
            return false;
        }
        EquipmentSlot slot = armor(event.getCurrentItem());
        if (slot == null) {
            return false;
        }
        ItemStack worn = inventory.getItem(slot);
        return worn == null || worn.getType().isAir();
    }

    private static EquipmentSlot armor(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        EquipmentSlot slot = item.getType().getEquipmentSlot();
        return slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS
                || slot == EquipmentSlot.FEET ? slot : null;
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
