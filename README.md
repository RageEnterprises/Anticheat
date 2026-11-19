<div align="center">

# RageAnticheat

**A lightweight, open-source anti-cheat for Paper servers.**

It catches flight, speed and other movement cheats using the same physics as vanilla Minecraft, with clear logs and configs you can tune yourself.

[![Paper 1.20+](https://img.shields.io/badge/Paper-1.20%2B-b22222)](https://papermc.io)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-8b0000)](https://adoptium.net)
[![Gradle](https://img.shields.io/badge/build-Gradle-b22222)](https://gradle.org)
[![Issues](https://img.shields.io/github/issues/RageEnterprises/Anticheat?color=8b0000)](https://github.com/RageEnterprises/Anticheat/issues)

[Features](#features) · [Installation](#installation) · [Configuration](#configuration) · [How it works](#how-it-works) · [Contributing](#contributing)

</div>

---

## Why RageAnticheat?

- **Based on vanilla physics.** Speed and Flight limits come from Minecraft's own friction, gravity and acceleration, so the numbers are not guessed. Ice, Speed effects, Soul Speed, Depth Strider and sprint-jumping are all part of the calculation.
- **No trust in the client.** Ground contact is checked against block collision shapes on the server, and the speed limit comes from the server's own `movement_speed` attribute. The client's `onGround` and sprint flags can be faked, so they don't decide the result.
- **Cheaters don't keep their progress.** A position only becomes a setback point if the move to it was fully legal. A cheater is sent back to where the cheating started.
- **Small and readable.** There are no NMS calls or packet libraries and no runtime dependencies. All the calculation code is plain Java with unit tests.
- **Easy to tune.** Every check has its own switch and limits. Every violation is logged with the values that were measured, so you can see whether a flag was a real cheat or a limit that needs adjusting.
- **One jar for every version.** It is built against the Paper 1.20.4 API and runs on 1.20 and every later version.

## Features

| Check | Catches | Response |
|---|---|---|
| **Flight** | Hovering, rising in mid-air, staying airborne too long | Setback |
| **Spider** | Climbing walls without a ladder or vine | Setback |
| **Speed** | Moving faster horizontally than vanilla physics allows | Setback |
| **Jesus** | Walking on water or lava | Setback |
| **Step** | Stepping up more than 0.6 blocks in one move | Setback |
| **NoFall** | Claiming to be on the ground while falling, to avoid fall damage | Setback to before the fall |
| **Timer** | A sped-up client that sends more movement than real time allows | Setback |
| **Reach** | Melee hits from farther away than vanilla allows | The hit is cancelled |

Each violation:

- adds 1 to the player's violation level, which goes down over time,
- is logged to the console with the measured values,
- sends an alert to staff with the `rageanticheat.alerts` permission.

A player is kicked once their violation level reaches the configured limit.

```
[RageAnticheat] Steve failed Speed (moved=0.360 allowed=0.286 excess=0.128 blocks) vl=3.0
[RageAnticheat] Alex failed Spider (gravity: dy=0.200 lastDy=0.200) vl=1.0
[RageAnticheat] Sam failed Reach (distance=4.12 max=3.50) vl=2.0
```

## Installation

1. Download the latest jar from [Releases](https://github.com/RageEnterprises/Anticheat/releases), or [build it yourself](#building-from-source).
2. Put the jar in your server's `plugins/` folder.
3. Restart the server. `config.yml` and `messages.yml` are created in `plugins/RageAnticheat/`.
4. Optional: set `violations.kick-enabled: false` for the first few days. You can then watch the log and adjust the limits before anyone gets kicked.

**Requirements:** a Paper 1.20 server or newer, or a fork of Paper such as Purpur. Folia is not supported.

> **Upgrading from 1.0?** The speed settings changed. Delete or rename your old `config.yml` so the new one is created.

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
| `rageanticheat.bypass` | false | Skip all checks. Ops do not get this automatically, so give it to trusted staff on purpose. |

Players in creative or spectator mode, and players an admin has allowed to fly, are always exempt from the movement checks.

## Configuration

Everything is in `plugins/RageAnticheat/config.yml`. Apply changes with `/rageanticheat reload`. The example below shows a few of the settings; the generated file comments every option.

```yaml
checks:
  speed:
    enabled: true
    tolerance: 0.01     # free extra distance per move on top of the vanilla prediction
    max-excess: 0.1     # flag when the extra distance over <samples> moves adds up past this
    samples: 10
  reach:
    enabled: true
    max-distance: 3.5   # vanilla is 3.0; the extra covers ping

violations:
  kick-threshold: 20
  kick-enabled: true
  decay-per-second: 0.5
```

<details>
<summary><b>Tuning tips</b></summary>

- **Speed flags legitimate players:** first read `moved` and `allowed` in the log. Raise `max-excess` (try 0.15–0.2) before you raise `tolerance`. `tolerance` is added to every move, so a cheater can always use it.
- **Flight or Spider flags legitimate players:** raise `gravity-tolerance` (try 0.08–0.1).
- **Timer flags players with an unstable connection:** raise `max-ahead-ms` or `max-lag-credit-ms`.
- **Reach flags high-ping players:** raise `max-distance` to 3.8–4.0.
- **Too many kicks:** raise `kick-threshold` or `decay-per-second`.
- **Flags right after jump pads or launchers from other plugins:** raise `grace.knockback-ticks` or `grace.boost-ticks`.

</details>

### Theme and messages

All chat text is in `messages.yml` and uses [MiniMessage](https://docs.advntr.dev/minimessage/format) tags. Each message is inserted into one shared `format` line at `{message}`, so changing that line recolors the whole plugin:

```yaml
format: "<dark_red><bold>RAGE → </bold></dark_red><red>{message}</red>"   # default
format: "<gold><bold>RAGE → </bold></gold><yellow>{message}</yellow>"     # gold theme
```

The placeholders are `{player}`, `{check}` and `{count}`. To show a literal `<`, write `\<`.

## How it works

The movement checks run on `PlayerMoveEvent` and only when the player's position changed. The common case does no allocations for block lookups.

<details>
<summary><b>Flight and Spider</b></summary>

Vanilla physics sets the next vertical move to `(lastDy - 0.08) * 0.98`. A move that is higher than that by more than `gravity-tolerance` is a violation. If the player was rising next to a wall, it is reported as **Spider**; otherwise as **Flight**. On the first move after leaving the ground, the player can rise as much as a jump allows: 0.42, plus 0.1 for each Jump Boost level.

Flight also has an airtime rule. After more than `max-airborne-ticks` moves in a row in the air, the player must be falling faster than `min-fall-speed`. Before either check flags a player, it checks for boats and shulkers nearby, because players can stand on those.

</details>

<details>
<summary><b>Speed</b></summary>

Each tick, vanilla moves a player by `previous move × friction + acceleration`. The check calculates the largest legal move from the same values the game uses:

- **Friction** depends on the block under the player (ice and slime are slippery), or on air, water or lava.
- **Acceleration** comes from the server's `movement_speed` attribute. This already includes sprinting, Speed effects and Soul Speed. Sneaking with Swift Sneak, Depth Strider and Dolphin's Grace are included as well.
- **A sprint-jump** adds 0.2 to the move.

Any distance beyond the prediction counts as excess. The player is flagged once the excess over a rolling window of moves adds up past `max-excess`. A 25% speed boost is flagged within 3 moves.

Two details keep the check strict:

- The prediction continues from the *allowed* move, not the real one. Moving just inside the tolerance every tick therefore cannot slowly build up speed.
- Paper only fires a move event once a player has moved more than 1/16 of a block, so very slow movement can merge several ticks into one event. The prediction counts those ticks too.

</details>

<details>
<summary><b>Jesus, Step and NoFall</b></summary>

- **Jesus:** a player whose feet are at a liquid surface, with their body out of it, who stays level for more than `max-ticks` moves is flagged. In vanilla, a player in that position sinks or bobs.
- **Step:** a move that starts and ends on the ground may rise at most `max-height` (vanilla 0.6), or the height of a jump if that is higher.
- **NoFall:** the client claims to be on the ground, but the server finds no block or boat under the player while they fall. If this happens for several moves in a row, the player is set back to before the fall.

</details>

<details>
<summary><b>Timer</b></summary>

A vanilla client sends one movement packet every 50 ms. The check keeps a balance of packets against real time and flags a client that gets more than `max-ahead-ms` ahead.

Idle time and network hiccups can be banked as credit, up to `max-lag-credit-ms`, so a burst of delayed packets is fine. When the server itself lags (a tick longer than 100 ms), the check resets for 2 seconds, because the queued packets arrive in a burst.

</details>

<details>
<summary><b>Reach</b></summary>

The check measures the distance from the attacker's eyes to the closest point of the target's hitbox. If it is more than `max-distance`, the hit is cancelled. Creative mode is exempt.

</details>

<details>
<summary><b>Grace periods</b></summary>

Some states legitimately break the movement rules, so the checks are paused while they last and for a short time afterwards:

- **All movement checks** are paused during and for `grace.boost-ticks` after elytra flight, riptide, vehicles and flying. They are also paused for `grace.knockback-ticks` after knockback, damage, a nearby explosion (including wind charges) or velocity set by the server.
- **The vertical checks** (Flight, Spider, NoFall) are also paused after water, lava, ladders, vines, scaffolding, levitation, slow falling, slime blocks and beds. They are skipped while the player touches cobwebs, powder snow, berry bushes or honey blocks.

</details>

## Known limitations

No anti-cheat is perfect. These are the cases we know about:

- **Plugins that move players** without setting their velocity (custom launchers, physics changes, changed `step_height`) can cause flags. Pistons and slime launchers that push players sideways can be flagged by Speed.
- **Ghost blocks** happen when the client and server disagree about a block, for example right after placing or breaking one during lag. These can flag NoFall or Flight; the setback fixes the mismatch.
- **Reach and ping:** the server checks the target's current position, not the one the attacker saw. Very high ping needs a higher `max-distance`.
- **Small gaps:** small timer boosts (under about 5%) are not detected. A speed cheat used within 1 block of a mob or vehicle is not flagged, and neither is a small speed boost while sprinting up stairs. Jesus cheats that bob up and down are left to the Speed check.
- **Hovering in place:** a player hovering without moving sends no movement packets, so they are only caught on their next move.

## Roadmap

These cheats are not detected yet. Contributions are welcome.

- [ ] KillAura and multi-aura
- [ ] AutoClicker (CPS limits)
- [ ] Scaffold and FastPlace
- [ ] Nuker and FastBreak
- [ ] X-ray (ore obfuscation or mining-pattern analysis)

## Building from source

```bash
git clone https://github.com/RageEnterprises/Anticheat.git
cd Anticheat
./gradlew build
```

The jar is written to `build/libs/RageAnticheat-<version>.jar`. Building needs JDK 17 or newer; `./gradlew build` also runs the unit tests.

## Contributing

Bug reports, false-positive reports and pull requests are all welcome.

- **Reporting a false positive:** open an [issue](https://github.com/RageEnterprises/Anticheat/issues). Include:
  - the log line, with its measured values,
  - your server version,
  - what the player was doing at the time,
  - your `config.yml`, if you changed it.
- **Reporting a bypass:** describe the cheat and its settings. Please don't publish working bypass code in a public issue.
- **Pull requests:** keep changes small and focused. New calculations go in `logic/` with a unit test that uses real vanilla values. Run `./gradlew build` before you open the PR.

### Project layout

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

## License

No license has been chosen yet. Until a `LICENSE` file is added, the default copyright rules apply: others may read the code but have no permission to copy, modify or redistribute it.
