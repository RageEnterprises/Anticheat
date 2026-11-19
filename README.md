# RageAnticheat

A small anti-cheat plugin for Paper 1.20 and newer. It has these checks:

| Check | Catches | Response |
|---|---|---|
| **Flight** | Hovering, rising in mid-air, staying airborne too long | Setback |
| **Spider** | Climbing walls without a ladder or vine | Setback |
| **Speed** | Moving faster horizontally than vanilla physics allows | Setback |
| **Jesus** | Walking on water or lava | Setback |
| **Step** | Stepping up more than 0.6 blocks in one move | Setback |
| **NoFall** | Claiming to be on the ground while falling, to avoid fall damage | Setback |
| **Timer** | A sped-up game client that sends more movement than real time allows | Setback |
| **Reach** | Melee hits from farther away than vanilla allows | The hit is cancelled |

Every violation adds 1 to the player's violation level and is logged. Online staff are alerted. The player is kicked when the level reaches the limit.

## Build

```
./gradlew build
```

The jar is written to `build/libs/RageAnticheat-<version>.jar`. The plugin is compiled against the Paper 1.20.4 API with Java 17 bytecode, so one jar works on 1.20 and every later version.

**Upgrading from 1.0:** the speed settings changed. Delete or rename your old `plugins/RageAnticheat/config.yml` so the new one is created.

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

Players in creative or spectator mode, and players an admin has allowed to fly (`allowFlight`), are exempt from the movement checks.

## How the checks work

The movement checks run in `PlayerMoveEvent` and only when the player's position changed. The ground test uses block collision shapes on the server and ignores the `onGround` flag the client sends, because a cheat client can fake that flag.

### Grace periods

Some states legitimately break the movement rules, so the checks are paused while they last and for a short time afterwards:

- **All movement checks** are paused during and for `grace.boost-ticks` after elytra flight, riptide, vehicles and flying. They are also paused for `grace.knockback-ticks` after knockback, damage, a nearby explosion (including wind charges) or velocity set by the server.
- **The vertical checks** (Flight, Spider, NoFall) are also paused after water, lava, ladders, vines, scaffolding, levitation, slow falling, slime blocks and beds. They are skipped while the player touches cobwebs, powder snow, berry bushes or honey blocks.

### Flight and Spider

Vanilla physics sets the next vertical move to `(lastDy - 0.08) * 0.98`. A move that is higher than that by more than `gravity-tolerance` is a violation. If the player was rising next to a wall, it is reported as **Spider**; otherwise as **Flight**. On the first move after leaving the ground, the player can rise as much as a jump allows: 0.42, plus 0.1 for each Jump Boost level.

Flight also has an airtime rule. Once a player has made more than `max-airborne-ticks` moves in a row in the air, they must be falling faster than `min-fall-speed` blocks per tick.

Before either check flags a player, it checks for boats and shulkers nearby, because players can stand on those.

### Speed

Each tick, vanilla moves a player by `previous move × friction + acceleration`. The check calculates the largest legal move from the same values the game uses:

- **Friction** depends on the block under the player (ice and slime are slippery), or on air, water or lava.
- **Acceleration** comes from the server's `movement_speed` attribute. This already includes sprinting, Speed effects and Soul Speed. Sneaking with Swift Sneak, Depth Strider and Dolphin's Grace are included as well.
- **A sprint-jump** adds 0.2 to the move.

Any distance beyond the prediction plus `tolerance` counts as excess. The player is flagged when the excess over the last `samples` moves adds up to more than `max-excess`. One small mistake in the prediction is tolerated this way, while a speed hack adds up quickly. A 25% speed boost is flagged within 3 moves.

Two rules keep cheaters from making progress:

- The prediction continues from the *allowed* move, not the real one. Moving just inside the tolerance every tick therefore cannot slowly build up speed.
- A position is only saved as a setback point if the move to it was fully legal. A cheater is sent back to where the cheating started, not to a point partway through it.

Before a flag counts, the check looks for mobs, players and vehicles within 1 block, because they push players sideways.

### Jesus

A player whose feet are at a liquid surface, with their body out of the liquid, who stays level for more than `max-ticks` moves is flagged. In vanilla, a player in that position sinks or bobs. Speed cheats on water are also caught by the Speed check, which uses water physics while the player is in water.

### Step

A move that starts and ends on the ground may rise at most `max-height` (vanilla 0.6), or the height of a jump if that is higher.

### NoFall

The client claims to be on the ground, but the server finds no block or boat under the player, and the player is falling. If this happens for `max-ticks` moves in a row, the player is flagged and set back to before the fall.

### Timer

A vanilla client sends one movement packet every 50 ms. The check keeps a balance of packets against real time and flags a client that gets more than `max-ahead-ms` ahead.

Idle time and network hiccups can be banked as credit, up to `max-lag-credit-ms`, so a burst of delayed packets is fine. When the server itself lags (a tick longer than 100 ms), the check resets for 2 seconds, because the queued packets arrive in a burst.

### Reach

The check measures the distance from the attacker's eyes to the closest point of the target's hitbox. If it is more than `max-distance`, the hit is cancelled and flagged. Creative mode is exempt.

### Response

The setback moves the player to their last valid location: on the ground, in liquid or climbing. If no valid location exists yet, the move is cancelled instead. The violation level goes down by `decay-per-second` every second. When it reaches `kick-threshold`, the player is kicked on the next tick and the level is reset.

Every violation is logged with the measured values, for example:

```
[RageAnticheat] Steve failed Speed (moved=0.360 allowed=0.286 excess=0.128 blocks) vl=3.0
[RageAnticheat] Alex failed Spider (gravity: dy=0.200 lastDy=0.200) vl=1.0
[RageAnticheat] Sam failed Reach (distance=4.12 max=3.50) vl=2.0
```

## Tuning

All values are in `config.yml`. Apply changes with `/rageanticheat reload`. Every check has an `enabled` switch.

- **Start gently:** set `violations.kick-enabled: false` and use only the log and alerts for a few days.
- **Speed flags legitimate players:** first read `moved` and `allowed` in the log. Raise `max-excess` (try 0.15–0.2) before you raise `tolerance`. `tolerance` is added to every move, so a cheater can always use it.
- **Flight or Spider flags legitimate players:** raise `gravity-tolerance` (try 0.08–0.1).
- **Timer flags players with an unstable connection:** raise `max-ahead-ms` or `max-lag-credit-ms`.
- **Reach flags high-ping players:** raise `max-distance` to 3.8–4.0.
- **Too many kicks:** raise `kick-threshold` or `decay-per-second`.
- **Flags right after jump pads or launchers from other plugins:** raise `grace.knockback-ticks` or `grace.boost-ticks`.

## Known false positives and limitations

- **Effects from other plugins:** jump pads or launchers that set velocity fire `PlayerVelocityEvent` and are handled. Plugins that move players in other ways, or that change movement physics (for example custom attributes other than `movement_speed`, or `step_height`), can cause flags.
- **Pistons and slime launchers** that push players sideways can be flagged by Speed.
- **Ghost blocks** happen when the client and server disagree about a block, for example right after placing or breaking one during lag. These can flag NoFall or Flight, and the setback fixes the mismatch.
- **Reach and ping:** the server checks the target's current position, not the one the attacker saw. Very high ping needs a higher `max-distance`.
- **Timer** is measured with move events, not every packet, and is reset during server lag. Very small timer boosts (under about 5%) are not detected.
- **Pushing entities:** a speed cheat used within 1 block of a mob or vehicle is not flagged, because those push players sideways.
- **Running up stairs:** while sprinting up stairs or slabs, Speed allows the sprint-jump bonus on every step, so a small speed boost there can go unnoticed.
- **Jesus cheats that bob** up and down are not caught by the Jesus check, but they move at swimming speed and the Speed check flags faster movement.
- **A player hovering without moving** sends no movement packets, so no move event runs. The plugin catches them on their next move.
- **Not detected:** combat cheats other than Reach (KillAura, AutoClicker, aim assist), and block cheats (X-ray, Nuker, FastBreak, Scaffold). Those need separate checks.
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
  RageAnticheat.java      plugin entry point, player state, violations, server lag tracking
  MovementListener.java   Flight, Spider, Speed, Jesus, Step, NoFall, Timer, and the grace/reset events
  CombatListener.java     Reach
  RageCommand.java        /rageanticheat
  Settings.java           config.yml snapshot
  Messages.java           messages.yml + MiniMessage rendering
  PlayerData.java         per-player state
  logic/                  pure Java, no Bukkit imports, unit-tested
    MovePhysics.java      vanilla friction and acceleration constants
    SpeedTracker.java     speed prediction and rolling excess budget
    FlightLimits.java     gravity model, airtime and step rules
    TimerBalance.java     packet-vs-time balance
    Reach.java            eye-to-hitbox distance
    RollingAverage.java   ring buffer
    ViolationCounter.java decaying violation level
```
