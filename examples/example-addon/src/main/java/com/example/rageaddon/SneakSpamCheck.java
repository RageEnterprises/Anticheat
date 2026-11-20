package com.example.rageaddon;

import dev.rage.anticheat.api.Check;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

/** Flags toggling sneak more often per second than a person can tap the key. */
final class SneakSpamCheck extends Check {

    private static final long SECOND_NANOS = 1_000_000_000L;

    private final int maxPerSecond;
    /** Recent sneak toggles per player, in System.nanoTime. */
    private final Map<UUID, Deque<Long>> toggles = new ConcurrentHashMap<>();

    SneakSpamCheck(int maxPerSecond) {
        super("SneakSpam");
        this.maxPerSecond = maxPerSecond;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (exempt(player) || serverLagging()) {
            return;
        }
        long now = System.nanoTime();
        Deque<Long> recent = toggles.computeIfAbsent(player.getUniqueId(), id -> new ArrayDeque<>());
        recent.addLast(now);
        while (now - recent.peekFirst() > SECOND_NANOS) {
            recent.removeFirst();
        }
        if (recent.size() > maxPerSecond) {
            recent.clear();
            event.setCancelled(true);
            flag(player, "toggled sneak more than " + maxPerSecond + " times in a second");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        toggles.remove(event.getPlayer().getUniqueId());
    }
}
