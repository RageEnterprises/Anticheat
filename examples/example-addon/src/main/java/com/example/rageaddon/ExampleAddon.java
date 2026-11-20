package com.example.rageaddon;

import dev.rage.anticheat.api.RageAnticheatApi;
import org.bukkit.plugin.java.JavaPlugin;

/** The whole addon: load the config and register the check. RageAnticheat does the rest. */
public final class ExampleAddon extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        long minReactionMs = getConfig().getLong("chest-stealer.min-reaction-ms", 50);
        RageAnticheatApi.get().register(this, new ChestStealerCheck(minReactionMs));
    }
}
