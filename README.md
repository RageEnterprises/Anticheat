# RageAnticheat

A small anti-cheat plugin for Paper 1.20 and newer. It has two checks, **Flight** and **Speed**. When a player fails one, the plugin teleports them back to their last valid location, adds a violation, logs it, and alerts staff. Players are kicked when their violations reach the limit.

## Build

```
./gradlew build
```

The jar is written to `build/libs/RageAnticheat-<version>.jar`. The plugin is compiled against the Paper 1.20.4 API with Java 17 bytecode, so one jar works on 1.20 and every later version.

## Commands and permissions

| Command | Description |
|---|---|
| `/rageanticheat reload` | Reload `config.yml` and `messages.yml`. This also clears all player state. |
| `/rageanticheat status` | Show which checks are enabled and which players have violations. |
| `/rageanticheat reset <player>` | Clear a player's violations and movement history. |

| Permission | Default | Effect |
|---|---|---|
| `rageanticheat.admin` | op | Use `/rageanticheat`. |
| `rageanticheat.alerts` | op | Receive violation alerts in chat. |
| `rageanticheat.bypass` | false | Skip all checks. Give this to trusted staff on purpose, because ops do not get it automatically. |

Players in creative or spectator mode, and players an admin has allowed to fly (`allowFlight`), are always exempt.

## The checks

Both checks run in `PlayerMoveEvent` and only when the player's position changed. The ground test uses block collision shapes on the server and ignores the `onGround` flag the client sends, because a cheat client can fake that flag.

### Flight

The check skips players who are on the ground, gliding with an elytra, using riptide, riding a vehicle, climbing (ladders, vines, scaffolding), in water or lava, under levitation or slow falling, or touching cobwebs, powder snow, berry bushes or honey blocks. After any of these ends, the check keeps skipping the player for `grace.boost-ticks`. Slime blocks and beds also start this grace, because they bounce players upward.

For every other move it applies two rules:

1. **Gravity.** Vanilla physics sets the next vertical move to `(lastDy - 0.08) * 0.98`. A move that is higher than that by more than `gravity-tolerance` is flagged. This catches hovering, slow falling and rising in mid-air. On the first move after leaving the ground, the player can rise as much as a jump allows: 0.42, plus 0.1 for each Jump Boost level.
2. **Airtime.** Once a player has made more than `max-airborne-ticks` moves in a row in the air, they must be falling faster than `min-fall-speed` blocks per tick. A real fall of this length is much faster than that.

Before a flag counts, the plugin checks for boats and shulkers near the player, because players can stand on those.

### Speed

The check measures horizontal distance per move and records the limit for the player's current state: `swim` in water or lava, `sneak` when sneaking, `sprint` when sprinting, and `walk` otherwise. Each Speed effect level raises the limit by `speed-potion-bonus`.

The plugin flags a player when the average of the last `samples` distances is above the **highest** limit in that same window. Because it uses an average, one lag spike does not cause a flag. Because it uses the highest limit, a player is not flagged while their speed carries over after they stop sprinting or start sneaking.

The check is paused, and its samples are discarded, during `grace.knockback-ticks` after knockback, damage or velocity set by the server. It is also paused during `grace.boost-ticks` after elytra flight, riptide, a vehicle, flying, ice, Soul Speed on soul blocks, and Dolphin's Grace or Depth Strider in water.

### Response

Each flag adds 1 to the player's violation level. The level goes down by `decay-per-second` every second. When it reaches `kick-threshold`, the player is kicked on the next tick and their level is reset. The setback moves the player to the last location where they were on the ground, in liquid or climbing. If no such location exists yet, the move is cancelled instead.

Every violation is logged to the console with the measured values, for example:

```
[RageAnticheat] Steve failed Flight (gravity: dy=0.000 lastDy=-0.078) vl=3.0
[RageAnticheat] Alex failed Speed (avg=0.612 max=0.450 blocks/tick) vl=1.0
```

## Tuning

All values are in `config.yml`. Apply changes with `/rageanticheat reload`.

- **Legitimate players get flagged by Flight:** raise `gravity-tolerance` (try 0.08–0.1) or `max-airborne-ticks`.
- **Legitimate players get flagged by Speed:** read the `avg` and `max` values in the log, and raise the limit for that state a little above `avg`. Raising `samples` makes the check more tolerant of lag, but it reacts more slowly.
- **Too many kicks:** raise `kick-threshold` or `decay-per-second`. One flag every 2 seconds stays balanced at a decay of 0.5 per second.
- **Players get flagged right after knockback or launch pads:** raise `grace.knockback-ticks` or `grace.boost-ticks`.
- To start a new check gently, set `violations.kick-enabled: false` and use only the log and alerts for a few days.

## Known false positives and limitations

- **Effects from other plugins:** jump pads, launchers or custom knockback that set velocity directly fire `PlayerVelocityEvent`, so they are handled. Effects that move the player in another way can be flagged. Give the affected players `rageanticheat.bypass` or raise the grace values.
- **Wind charges (1.21+) and pistons** can push players without damage or a velocity event. These pushes can be flagged.
- **Hitting your head while sprint-jumping** (for example in 2-block-high tunnels) is faster than the `sprint` limit. On ice this is covered by the ice grace. On normal blocks it can be flagged if the player does it repeatedly.
- **Bubble columns, rising out of water, and climbing to the top of ladders** are covered by the grace period. A very short `boost-ticks` brings back false positives here.
- **A player hovering without moving** sends no movement packets, so no move event runs. The plugin catches them on their next move.
- **Speed is measured per movement packet, not per server tick.** Timer cheats, which send more packets per second, are not detected. That would need a separate check.
- **The plugin does not detect** NoFall, Jesus/water walking, step, phase or blink cheats. Only Flight and Speed are included in this version.
- **Folia** is not supported, because the plugin uses the global scheduler.

## Changing the theme

All text is in `messages.yml` and uses [MiniMessage](https://docs.advntr.dev/minimessage/format) tags. Each message is inserted into `format` at `{message}`:

```yaml
format: "<dark_red><bold>RAGE → </bold></dark_red><red>{message}</red>"
```

For example, to change the theme to gold:

```yaml
format: "<gold><bold>RAGE → </bold></gold><yellow>{message}</yellow>"
```

Messages can use their own tags as well, such as `alert: "<white>{player}</white> failed {check} ({count} violations)."`. The placeholders are `{player}`, `{check}` and `{count}`. To show a literal `<`, write `\<`. Run `/rageanticheat reload` after you edit the file.

## Project layout

```
src/main/java/dev/rage/anticheat/
  RageAnticheat.java      plugin entry point, player state, violation handling
  MovementListener.java   the two checks and the grace/reset events
  RageCommand.java        /rageanticheat
  Settings.java           config.yml snapshot
  Messages.java           messages.yml + MiniMessage rendering
  PlayerData.java         per-player state
  logic/                  pure Java, no Bukkit imports, unit-tested
    RollingAverage.java   ring buffer for speed samples
    SpeedLimits.java      per-state limits and the rolling-average rule
    FlightLimits.java     gravity model and airtime rule
    ViolationCounter.java decaying violation level
```
