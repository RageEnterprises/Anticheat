# Making a RageAnticheat addon

An addon is a normal Paper plugin with one or more checks in it. RageAnticheat takes care of
everything after you decide that a player is cheating. That includes the violation level, staff
alerts, Discord, the player's `/profile`, the kick or ban, bypass permissions and the on/off switch.

A complete, working addon is in [`examples/example-addon`](examples/example-addon). Copy it to start.

## 1. Add the API

The API is published through [JitPack](https://jitpack.io/#RageEnterprises/Anticheat). In your
`build.gradle.kts`:

```kotlin
repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    compileOnly("com.github.RageEnterprises:Anticheat:v2.2.0") // a release tag, or main-SNAPSHOT
}
```

Use `compileOnly`, so RageAnticheat is not bundled into your jar; the server already has it.

## 2. Depend on RageAnticheat

In your `plugin.yml`:

```yaml
depend: [RageAnticheat]
```

This makes the server enable RageAnticheat before your addon.

## 3. Write a check

Extend `Check`, give it a name, and write `@EventHandler` methods as in any Bukkit listener:

```java
import dev.rage.anticheat.api.Check;

final class FastBowCheck extends Check {

    FastBowCheck() {
        super("FastBow"); // shown in alerts; letters, digits, '_' and '-'
    }

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || exempt(player)) {
            return; // check turned off, or the player has a bypass permission
        }
        if (/* your detection */) {
            event.setCancelled(true);              // undo the cheat yourself
            flag(player, "shot after 2 ticks");    // RageAnticheat does the rest
        }
    }
}
```

## 4. Register it

```java
public final class MyAddon extends JavaPlugin {
    @Override
    public void onEnable() {
        RageAnticheatApi.get().register(this, new FastBowCheck());
    }
}
```

That's all. You don't need to unregister in `onDisable`; checks are removed when your plugin
disables.

## What a check can use

| Method | What it does |
|---|---|
| `flag(player, details)` | Adds a violation with alerts, Discord, profile and punishment. Safe from any thread. |
| `exempt(player)` | True if the check is turned off, or the player has `rageanticheat.bypass` or `rageanticheat.bypass.<name>`. |
| `serverLagging()` | True during and just after server lag. Skip timing-based checks then. |
| `enabled()` | False if the server owner listed the check in `addons.disabled`. |

`RageAnticheatApi.get()` also has:

| Method | What it does |
|---|---|
| `register(plugin, check)` / `unregister(check)` | Adds or removes a check. |
| `checks()` | Every registered addon check. |
| `flag(player, name, details)` | Flags without a `Check` class. Server owners can't turn these off with `addons.disabled`. |
| `violations(player)` | The player's current violation count. |

## Listening to every flag

`PlayerFlagEvent` is called before any check, built-in or addon, adds a violation. Cancel it to
ignore flags, for example while your minigame launches players:

```java
@EventHandler
public void onFlag(PlayerFlagEvent event) {
    if (event.check().equals("Flight") && launched.contains(event.getPlayer())) {
        event.setCancelled(true);
    }
}
```

For this you don't need a `Check`. With `softdepend: [RageAnticheat]` instead of `depend`, register the
listener only if `getServer().getPluginManager().isPluginEnabled("RageAnticheat")`, because the event class is
missing without it.

## Tips

- **Undo first, flag second.** `flag` only records. Cancel the event or teleport the player back yourself.
- **Use real time for timing.** The server handles packets between ticks, so `System.nanoTime()` in
  an event handler gives real-time precision. Subtract `player.getPing()` from reaction times.
- **Keep thresholds generous.** A false positive kicks an innocent player. Start by only flagging,
  and tune your thresholds on a real server.
- **Give your addon a config** for its thresholds, as the example does.
- **Names are unique.** `register` throws if a built-in check or another addon already uses the name.

## For server owners

Put addon jars in `plugins/` next to RageAnticheat. `/rageanticheat status` lists every addon check
and the addon it comes from. To turn one off, add its name to `addons.disabled` in `config.yml`.
Then run `/rageanticheat reload`.
