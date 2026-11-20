package dev.rage.anticheat;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;

/**
 * AutoFish: the time from a fish biting to the player reeling in, minus the ping. People need
 * about 200 ms to notice the bobber dip; auto-fish cheats reel in at once. Fast players get one
 * now and then, so only a streak of quick catches flags. The flagged catch is cancelled.
 */
final class FishListener implements Listener {

    private final RageAnticheat plugin;

    FishListener(RageAnticheat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Settings.AutoFish f = plugin.settings().autoFish();
        Player player = event.getPlayer();
        if (!f.enabled() || player.hasPermission(MovementListener.BYPASS)) {
            return;
        }
        PlayerData d = plugin.data(player);
        long now = System.nanoTime();
        if (event.getState() == PlayerFishEvent.State.BITE) {
            d.biteNanos = now;
            return;
        }
        long bite = d.biteNanos;
        d.biteNanos = 0;
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || bite == 0 || plugin.serverLagging(now)) {
            return;
        }
        long elapsedMs = (now - bite) / 1_000_000L;
        long reactionMs = elapsedMs - player.getPing();
        d.quickCatches = reactionMs < f.minReactionMs() ? d.quickCatches + 1 : 0;
        if (d.quickCatches < f.streak()) {
            return;
        }
        d.quickCatches = 0;
        event.setCancelled(true);
        plugin.flag(player, d, "AutoFish", String.format(
                "%d catches in a row reeled in within %d ms of the bite (last %d ms, ping %d ms)",
                f.streak(), f.minReactionMs(), elapsedMs, player.getPing()));
    }
}
