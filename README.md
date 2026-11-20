<div align="center">

# RageAnticheat

**An open-source anti-cheat and moderation suite for Paper servers.**

One plugin catches movement, combat, block and inventory cheats, blocks crash exploits and cheat clients, and gives your staff bans, mutes, freezes, chat anti-spam, maintenance mode, player profiles, alt detection, anti-VPN, anti-bot and a Discord bot. Anyone can add their own checks as [addons](ADDONS.md).

[![Paper 1.20+](https://img.shields.io/badge/Paper-1.20%2B-b22222)](https://papermc.io)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-8b0000)](https://adoptium.net)
[![Gradle](https://img.shields.io/badge/build-Gradle-b22222)](https://gradle.org)
[![Issues](https://img.shields.io/github/issues/RageEnterprises/Anticheat?color=8b0000)](https://github.com/RageEnterprises/Anticheat/issues)

[Features](#features) · [Installation](#installation) · [Commands](#commands-and-permissions) · [Discord](#discord) · [Configuration](#configuration) · [How it works](#how-the-checks-work) · [Addons](ADDONS.md) · [Contributing](#contributing)

</div>

---

## Why RageAnticheat?

- **Based on vanilla physics.** Speed and Flight limits come from Minecraft's own friction, gravity and acceleration, so the numbers are not guessed. Ice, Speed effects, Soul Speed, Depth Strider and sprint-jumping are all part of the calculation.
- **No trust in the client.** Ground contact is checked against block collision shapes on the server, and the speed limit comes from the server's own `movement_speed` attribute.
- **Cheaters don't keep their progress.** A position only becomes a setback point if the move to it was fully legal.
- **Moderation built in.** Bans, mutes, warnings, kicks, freezes and their full history are kept in one database. That includes punishments the anticheat hands out itself.
- **Your look.** Every message is in `messages.yml` with RGB hex colors and gradients.
- **One jar for every version.** It is built against the Paper 1.20.4 API and runs on 1.20 and every later version. SQLite and the Discord library are downloaded by Paper on first start, so the jar stays small.

## Features

### Anticheat checks

| Check | Catches | Response |
|---|---|---|
| **Flight** | Hovering, rising in mid-air, staying airborne too long | Setback |
| **Spider** | Climbing walls without a ladder or vine | Setback |
| **Speed** | Moving faster horizontally than vanilla physics allows | Setback |
| **Jesus** | Walking on water or lava | Setback |
| **Step** | Stepping up more than 0.6 blocks in one move | Setback |
| **NoFall** | Claiming to be on the ground while falling | Setback to before the fall |
| **Timer** | A sped-up client sending more movement than real time allows | Setback |
| **Reach** | Melee hits from farther away than vanilla allows | Hit cancelled |
| **KillAura** | Hitting targets outside the crosshair, or many targets at once | Hit cancelled |
| **Nuker** | Breaking blocks too fast, too far away, or outside the view | Break cancelled |
| **Scaffold** | Placing blocks against faces the player isn't looking at | Place cancelled |
| **Xray** | Mining far more hidden ores per stone than normal | Staff alert (or violation) |
| **NoSlow** | Moving at full speed while eating, blocking or drawing a bow | Setback |
| **FastBreak** | Mining blocks faster than the tool and effects allow | Break cancelled |
| **AutoClicker** | Clicking faster than people can, or at a machine-even rhythm | Violation |
| **CrystalAura** | Breaking end crystals before the client could see them, or too many per second | Hit or place cancelled |
| **AutoTotem** | Refilling the off-hand totem faster than a person can react | Refill cancelled |
| **InvalidRotation** | Pitch beyond straight up or down, sent by silent-aim and spin cheats | Violation |
| **NoSwing** | Attacking without the arm swing packet that every vanilla attack sends | Violation |
| **ImpossibleSprint** | Starting to sprint with 6 food or less, or while blind | Sprint cancelled |
| **ElytraFly** | Gliding without a boost faster, higher or longer than an elytra allows, or hovering | Gliding stopped |
| **BoatFly** | A boat hanging in the air without falling | Dismount, vehicle moved back |
| **EntitySpeed** | Boats and horses moving faster than they can | Dismount, vehicle moved back |
| **Velocity** | Taking less knockback than the server gave (anti-knockback) | Violation |
| **Phase** | Moving into solid blocks | Setback |
| **AirPlace** | Placing a block against air | Place cancelled |
| **Tower** | Stacking blocks under yourself faster than jumping allows | Place cancelled |
| **Criticals** | Critical hits from a faked fall while standing on the ground | Hit cancelled |
| **ChestStealer** | Taking items from a chest faster than a person reacts to it opening | Click cancelled |
| **AutoArmor** | Putting on armor pieces faster than a person can click | Click cancelled |
| **InventoryMove** | Using the inventory while walking or sprinting | Click cancelled |
| **AutoFish** | Reeling in right after every bite | Catch cancelled |
| **Blink** | Holding movement back and sending it all at once | Setback |
| **Aim** / **Bot** | Camera turns that are not whole mouse steps: aimbots in combat, bots such as Baritone elsewhere | Staff alert (or violation) |

Each violation:

- adds 1 to the player's violation level, which goes down over time,
- is logged to the console with the measured values,
- alerts staff in game and on Discord,
- is saved to the player's profile.

When the level reaches the limit, the player is **kicked or banned**, depending on `violations.action`.

### Protection

| Feature | What it does |
|---|---|
| **Crash exploits** | Blocks oversized books, creative items with huge NBT data and oversized sign lines, then bans the player (configurable). |
| **Anti-bot** | Limits accounts per IP. During a join flood it turns on attack mode, in which only returning players can join. |
| **Anti-VPN** | Optionally refuses VPN and proxy connections (via proxycheck.io), with an allow-list. |
| **Alt detection** | Links accounts by shared IP, alerts staff when a player with alts joins, and can block alts of banned players. |
| **Client mods** | Refuses clients whose brand or plugin channels name a blocked mod (Wurst, Meteor, Baritone and more), or every modded client with `vanilla-only`. Also catches clients that fake a vanilla brand to hide their mods. |
| **Chat anti-spam** | Blocks messages sent too fast or repeated, and mutes players who keep spamming. |
| **Maintenance mode** | `/maintenance on` lets only ops and whitelisted players join, and the server list shows "Maintenance" instead of the ping bars. |

### Moderation

| Feature | What it does |
|---|---|
| **Punishments** | `/ban`, `/mute`, `/kick`, `/warn`, `/unban` and `/unmute`. Bans and mutes can be timed (`30m`, `7d`) or permanent. |
| **Clear chat** | `/clearchat` scrolls everyone's chat away. Staff with the bypass permission keep theirs. |
| **Freeze** | `/freeze` holds a player in place. Logging out while frozen alerts staff, and can optionally ban them. |
| **Profiles** | `/profile` shows status, first join and last seen, IP and alt counts, punishments and anticheat flags. |
| **History** | `/history` lists every punishment, with who gave it and whether it is active, expired or lifted. |
| **Discord** | A webhook for alerts, and a bot that lets staff moderate from Discord. |

## Installation

1. Download the latest jar from [Releases](https://github.com/RageEnterprises/Anticheat/releases), or [build it yourself](#building-from-source).
2. Put the jar in your server's `plugins/` folder.
3. Restart the server. On the first start, Paper downloads SQLite and JDA (the Discord library) from Maven Central, so the server needs internet access.
4. Edit `plugins/RageAnticheat/config.yml` and `messages.yml`, then run `/rageanticheat reload`.
5. Optional: set `violations.kick-enabled: false` for the first few days. You can then watch the log and adjust the limits before anyone gets kicked.

**Requirements:** a Paper 1.20 server or newer, or a fork of Paper such as Purpur. Folia is not supported.

> **Upgrading from 1.x?** Many settings were added. Delete or rename your old `config.yml` and `messages.yml` so the new ones are created.

## Commands and permissions

| Command | Permission | Description |
|---|---|---|
| `/rageanticheat reload\|status\|reset <player>` | `rageanticheat.admin` | Reload config, show checks and violations, or reset a player. |
| `/ban <player> [duration] [reason]` | `rageanticheat.mod.ban` | Ban, permanently or for a time. Alias: `/tempban`. |
| `/mute <player> [duration] [reason]` | `rageanticheat.mod.mute` | Mute chat and private messages. Alias: `/tempmute`. |
| `/kick <player> [reason]` | `rageanticheat.mod.kick` | Kick an online player. |
| `/warn <player> [reason]` | `rageanticheat.mod.warn` | Warn a player. The warning is saved in their history. |
| `/unban <player>` | `rageanticheat.mod.unban` | Lift a ban. |
| `/unmute <player>` | `rageanticheat.mod.unmute` | Lift a mute. |
| `/clearchat` | `rageanticheat.mod.clearchat` | Clear the chat for everyone. |
| `/freeze <player>` | `rageanticheat.mod.freeze` | Freeze or unfreeze a player. |
| `/profile <player>` | `rageanticheat.mod.profile` | Show the player's profile. |
| `/history <player>` | `rageanticheat.mod.history` | Show the player's punishment history. |
| `/alts <player>` | `rageanticheat.mod.alts` | List accounts that share an IP with the player. |
| `/maintenance <on\|off\|add\|remove\|list> [player]` | `rageanticheat.maintenance` | Maintenance mode and its whitelist. Ops can always join. |

Durations look like `30s`, `10m`, `2h`, `1d12h`, `7d`, `2w`, `1mo`, `1y`, or `perm`. If you leave the duration out, the ban or mute is permanent.

| Other permission | Default | Effect |
|---|---|---|
| `rageanticheat.mod.*` | op | Every moderation command. |
| `rageanticheat.alerts` | op | Receive anticheat alerts and staff notices. |
| `rageanticheat.bypass` | false | Skip the anticheat checks. |
| `rageanticheat.bypass.<check>` | false | Skip one addon check, for example `rageanticheat.bypass.fastbow`. |
| `rageanticheat.exploits.bypass` | false | Skip the crash-exploit limits. |
| `rageanticheat.mod.exempt` | false | Cannot be punished or frozen by other players. The console still can. |
| `rageanticheat.mod.clearchat.bypass` | op | Keep your chat when it is cleared. |
| `rageanticheat.chat.bypass` | op | Not limited by the chat spam filter. |
| `rageanticheat.mods.bypass` | false | Join with mods on the client-mods blocklist. |

## Discord

### Webhook (alerts only)

1. In Discord, open **Channel settings → Integrations → Webhooks** and create a webhook.
2. Copy its URL into `discord.webhook.url` and set `enabled: true`.
3. Choose which events to post: `alerts`, `punishments`, `joins` (alts, VPN and bot blocks) and `exploits`.

### Bot (moderate from Discord)

1. Create an application at the [Discord Developer Portal](https://discord.com/developers/applications), add a bot, and copy its token.
2. Invite the bot with the `bot` and `applications.commands` scopes.
3. Turn on **Developer Mode** (Discord settings → Advanced), then right-click your server, staff role and alert channel to copy their IDs.
4. Fill in `discord.bot` in `config.yml`, set `enabled: true`, and run `/rageanticheat reload`.

Members with the staff role can use `/ban`, `/mute`, `/kick`, `/warn`, `/unban`, `/unmute`, `/freeze`, `/clearchat`, `/profile`, `/history`, `/players` and `/maintenance`. Punishments given from Discord are recorded as `Discord:<username>`.

`/console` runs any server command as the console. It is only available when `allow-console: true` is set, and only to members with the admin role.

> Keep the bot token secret. Anyone with the token controls the bot, and with `/console` enabled, the server.

## Configuration

Everything is in `plugins/RageAnticheat/config.yml`, and every option is commented. Apply changes with `/rageanticheat reload`. Each anticheat check has its own `enabled` switch, and so do the anti-VPN, anti-bot, alt detection, exploit protection, webhook and bot.

```yaml
violations:
  kick-threshold: 20
  action: ban          # kick or ban at the threshold
  ban-duration: 7d

anti-vpn:
  enabled: false       # sends joining IPs to proxycheck.io when on
  allowed-players: []

anti-bot:
  enabled: true
  max-accounts-per-ip: 3
  max-new-players: 8   # first-time joins within window-seconds before attack mode
  window-seconds: 10
```

<details>
<summary><b>Tuning tips</b></summary>

- **Speed flags legitimate players:** first read `moved` and `allowed` in the log. Raise `max-excess` (try 0.15–0.2) before you raise `tolerance`, because a cheater can use `tolerance` on every move.
- **Flight or Spider flags legitimate players:** raise `gravity-tolerance` (try 0.08–0.1).
- **KillAura or Scaffold flags laggy players:** raise `hitbox-expand` or `max-misses`.
- **Reach flags high-ping players:** raise `max-distance` to 3.8–4.0.
- **Timer flags players with an unstable connection:** raise `max-ahead-ms` or `max-lag-credit-ms`.
- **AutoClicker flags fast legitimate clickers:** lower `min-deviation-ms` or raise `max-cps`.
- **FastBreak flags players with unstable ping:** lower `min-ratio` toward 0.7.
- **AutoTotem flags quick players:** lower `min-reaction-ms`.
- **Velocity flags players on bad connections:** raise `fails-to-flag` or lower `min-ratio`.
- **Aim alerts for a player who uses cinematic camera or zoom:** that is expected; leave `punish` off.
- **Xray alerts on legitimate miners:** raise `min-ores` or `max-ores-per-stone`. Paper's own anti-xray (`paper-world-defaults.yml → anticheat.anti-xray`) is still the best way to stop X-ray itself.
- **Too many kicks:** raise `kick-threshold` or `decay-per-second`.

</details>

### Theme and messages

All chat text is in `messages.yml` and uses [MiniMessage](https://docs.advntr.dev/minimessage/format) tags, with RGB hex colors (`<#ff6b6b>`) and gradients rather than the 16 default colors. Each chat message is inserted into one shared `format` line at `{message}`:

```yaml
format: "<bold><gradient:#7a0000:#ff2a2a>RAGE →</gradient></bold> <#ff6b6b>{message}</#ff6b6b>"
```

To retheme the whole plugin, change that line. Names and values are highlighted with `#ffc2c2`; search and replace it to change the highlight color. The ban, kick and VPN screens (`*-screen`) are shown on the disconnect screen and use `\n` for new lines. To show a literal `<`, write `\<`.

## How the checks work

The movement checks run on `PlayerMoveEvent` and only when the player's position changed. The common case does no allocations for block lookups.

<details>
<summary><b>Flight and Spider</b></summary>

Vanilla physics sets the next vertical move to `(lastDy - 0.08) * 0.98`. A move that is higher than that by more than `gravity-tolerance` is a violation. If the player was rising next to a wall, it is reported as **Spider**; otherwise as **Flight**.

Flight also has an airtime rule. After more than `max-airborne-ticks` moves in a row in the air, the player must be falling faster than `min-fall-speed`. Before either check flags a player, it checks for boats and shulkers nearby, because players can stand on those.

</details>

<details>
<summary><b>Speed</b></summary>

Each tick, vanilla moves a player by `previous move × friction + acceleration`. The check calculates the largest legal move from the same values the game uses:

- **Friction** depends on the block under the player, or on air, water or lava.
- **Acceleration** comes from the `movement_speed` attribute, plus Swift Sneak, Depth Strider and Dolphin's Grace.
- **A sprint-jump** adds 0.2 to the move.

The player is flagged once the excess over a rolling window of moves adds up past `max-excess`. The prediction continues from the *allowed* move rather than the real one, so moving just inside the tolerance every tick cannot slowly build up speed.

**NoSlow** is part of the same prediction. While the server sees the player using an item (eating, drinking, blocking, drawing a bow), acceleration is cut to 20% as in vanilla, and a violation is reported as NoSlow.

</details>

<details>
<summary><b>Jesus, Step, NoFall and Timer</b></summary>

- **Jesus:** staying level at a liquid surface, with the body out of the liquid, for more than `max-ticks` moves.
- **Step:** a move from ground to ground that rises more than `max-height`, or a jump's height if that is higher.
- **NoFall:** the client claims to be on the ground while the server finds no block or boat under a falling player.
- **Timer:** a balance of movement packets against real time. Idle time and lag are banked as credit, and the check resets while the server itself lags.
- **Blink:** a Timer burst within 2 seconds of the client going silent for half a second. Blink cheats hold moves back and then release them together.
- **Phase:** a move into solid block collision from outside it, tested with a box slightly smaller than the player. Paper already refuses most such moves itself; this flags the rest.

</details>

<details>
<summary><b>ElytraFly, BoatFly, EntitySpeed and Velocity</b></summary>

- **ElytraFly:** gliding only trades height for speed and back, and drag takes energy away. Without a firework, knockback, riptide or levitation, height plus the height the glider's speed could buy (`v² / 2g`) cannot rise more than `climb-margin` above its peak. Gliding faster than `max-speed` without a boost also flags, and so does a glider that holds still for a second.
- **BoatFly and EntitySpeed:** each vehicle move is measured per tick. Boats are limited to `max-boat-speed`, or `max-boat-ice-speed` on ice. Horses are limited to their speed attribute times `mount-speed-factor`. A boat that neither rests on a block or liquid nor falls for `max-boat-air-moves` moves flags BoatFly.
- **Velocity:** after knockback, the player's longest run of movement along it is measured until their ping plus 6 ticks have passed. Less than `min-ratio` of the expected distance, `fails-to-flag` times in a row, flags. Players next to walls, in liquid, cobwebs, vehicles or gliding are skipped.

</details>

<details>
<summary><b>Reach and KillAura</b></summary>

- **Reach:** distance from the attacker's eyes to the closest point of the target's hitbox.
- **KillAura:** the check ray-traces from the attacker's eyes along their view. A hit on a target whose hitbox (expanded by `hitbox-expand` to allow for ping) is not under the crosshair counts as a miss. `max-misses` misses within the window flag the player. Hitting more than `max-targets` different entities within half a second also flags.
- **AutoClicker:** the server handles packets between ticks, so arm swings arrive with real-time precision. Swings aimed at a block (mining, placing) and swings from dropping items are ignored. More than `max-cps` clicks per second flags, and so does a streak of `samples` clicks whose spacing varies by less than `min-deviation-ms`.
- **CrystalAura:** a crystal's spawn packet goes out on the tick after it is placed and takes half the ping to reach the client. A hit on a crystal younger than that is aimed at a predicted entity ID, which only cheats do. Crystal places and breaks per second are limited, and crystal hits get the KillAura crosshair test.
- **InvalidRotation, NoSwing, ImpossibleSprint:** packets the vanilla client cannot send. Its pitch is kept between -90 and 90 degrees, every attack comes with a swing packet in the same tick, and it does not start sprinting while hungry or blind. Food and effect changes get a second plus the ping of grace, because the client learns about them late.
- **Criticals:** a hit while the client claims to be falling, but the server finds the ground under the player and they have not jumped in the last 15 ticks. Cheats fake the fall with hops too small for the server to see as moves. Stepping off a slab does the same now and then, so only a streak flags.
- **Aim and Bot:** mouse input turns the camera in whole steps of the sensitivity, at least 0.0096° at the lowest setting. Each player's last `samples` pitch changes are checked for such a step. Computed rotations have none, and are reported as Aim within 3 seconds of an attack and as Bot otherwise.
- **ChestStealer, AutoArmor and InventoryMove:** ChestStealer times the first click after a chest opens, minus the ping. AutoArmor flags three armor pieces put on less than `min-interval-ms` apart. InventoryMove flags two inventory clicks within a second while walking or sprinting on normal ground; the vanilla client lets go of the movement keys when a screen opens.
- **AutoFish:** the time from a fish biting to the reel-in, minus the ping, `streak` catches in a row.
- **AutoTotem:** when a totem pops, the time until the click that moves the next totem into the off hand, minus the ping, is the player's reaction time. Each inventory click event is one click packet, so this works without ProtocolLib.

</details>

<details>
<summary><b>Nuker, Scaffold and Xray</b></summary>

- **FastBreak:** the time between the client's "start mining" and "finished" packets is compared with the time the block takes with the player's tool, enchantments and effects. Vanilla servers accept 70%, which FastBreak cheats use; the default limit is 80%.
- **AirPlace:** every vanilla placement clicks the face of an existing block, so a placement against air is cancelled.
- **Tower:** blocks stacked one above the other under the player, more than `max-per-second`. Jumping and placing manages about 2.5 a second.
- **Nuker:** more blocks per tick or per second than instamining allows, blocks beyond reach, or blocks more than `max-angle` away from the crosshair.
- **Scaffold:** each placement ray-traces the player's view. The block they clicked must be the block and face they are looking at, and repeated mismatches flag. Too many placements per second also flag.
- **Xray:** counts "hidden" ores, meaning ores with no air or liquid touching them, against stone mined. Only the first ore of a vein counts. A ratio far above normal mining alerts staff.

</details>

<details>
<summary><b>Crash exploits, anti-bot, anti-VPN and alts</b></summary>

- **Crash exploits:** a book edit with more than 100 pages, pages over 1024 characters, or a title over 32 characters is cancelled. So is a creative-mode item whose serialized data is over `max-item-bytes`, and a sign line over 384 characters. The player is punished through the moderation system, so the punishment appears in their history.
- **Anti-bot:** runs before the ban and VPN checks, so a flood cannot use up the VPN lookup quota. Returning players, those with a profile, are never held back by attack mode.
- **Anti-VPN:** each IP is looked up once per `cache-hours`. Local and LAN addresses are skipped. Behind BungeeCord or Velocity, enable IP forwarding so the plugin sees players' real IPs.
- **Alts:** every join records the IP. Accounts that have used the same IP are linked.
- **Client mods:** clients report a brand (`vanilla`, `fabric`, `forge`, ...) and the plugin channels their mods use. Both are matched against `client-mods.blocked` as whole words. Brand changers that make a modded client say `vanilla` leave the mod loader's channels behind, which `detect-spoofing` catches, along with a brand that changes after joining.
- **Chat anti-spam:** at most `max-messages` per `window-seconds`, and no repeat of the same message (ignoring case, spaces and punctuation) within `repeat-seconds`. Private-message commands count as chat. Every blocked message is a strike; enough strikes in a minute mute the player through the moderation system.
- **Maintenance:** the state and whitelist are saved in `maintenance.yml`. The server list gets an unknown protocol version, so clients show the version text, "Maintenance", where the ping bars normally are.

</details>

<details>
<summary><b>Grace periods</b></summary>

- **All movement checks** are paused during and for `grace.boost-ticks` after elytra flight, riptide, vehicles and flying. They are also paused for `grace.knockback-ticks` after knockback, damage, explosions (including wind charges) or velocity set by the server.
- **The vertical checks** are also paused after water, ladders, levitation, slow falling, slime blocks and beds. They are skipped while touching cobwebs, powder snow, berry bushes or honey.

</details>

## Data and privacy

- **Local storage:** player names, UUIDs, join times, IP history, anticheat flags and punishments are stored locally in `plugins/RageAnticheat/data.db` (SQLite). IP addresses are never shown in game or on Discord; only counts and linked account names are.
- **Anti-VPN:** with anti-VPN enabled, each joining player's IP is sent to [proxycheck.io](https://proxycheck.io). Mention this in your server rules or privacy policy if you turn it on.
- **Discord:** with the webhook or bot enabled, alerts and punishments, including player names and reasons, are posted to your Discord.

## Known limitations

No anti-cheat is perfect. These are the cases we know about:

- **Plugins that move players** without setting their velocity can cause movement flags, as can pistons and slime launchers.
- **Lag:** high ping needs a higher Reach `max-distance` and a higher KillAura `hitbox-expand`. Ghost blocks during lag can briefly flag NoFall or Flight.
- **Rotation-faking cheats:** Scaffold and KillAura cheats that send correct rotations are harder to catch. These checks look for clear mismatches, not perfect aim.
- **Xray is a heuristic:** lucky players and cave miners can look suspicious, so it only alerts staff by default.
- **Shared IPs:** families, schools and public Wi-Fi share IPs, which affects alt detection and the per-IP limit. That's why blocking alts of banned players is off by default.
- **Vanilla bans:** `/ban` overrides the vanilla command. Bans made with `minecraft:ban` or from before installing are not in RageAnticheat's database.
- **Single server:** storage is SQLite on one server. It is not shared across a BungeeCord or Velocity network.
- **Client mod detection only sees what clients send:** a cheat client that fakes a vanilla brand and registers no channels at all looks vanilla. Freecam runs entirely on the client and leaves no trace the server can see; Paper's anti-xray and entity tracking limit what it reveals.
- **AutoClicker and the reaction-time checks need real-time packets:** under heavy server lag they reset rather than judge.
- **No packet library:** RageAnticheat uses only Paper's events. Checks that need raw packets, such as ping-spoof detection and transaction-based knockback timing, are not possible without one. Faking a high ping cannot get around the reaction-time checks: the ping is subtracted, so a higher one only makes the reaction look faster.
- **Aim and Bot are hints:** cinematic camera and zoom mods also smooth the camera, so they only alert staff by default. Bots that imitate mouse steps are not caught.
- **Cheats with a random delay:** AutoFish, ChestStealer and AutoArmor cheats set to wait longer than the limits look like a fast player.
- **Hovering in place:** a player hovering without moving sends no movement packets, so they are caught on their next move.

## Roadmap

- [ ] MySQL/MariaDB storage for networks
- [ ] Optional PacketEvents support for packet-level checks
- [ ] IP bans and IP mutes
- [ ] In-game GUI for profiles and history

## Addons

RageAnticheat has an API for community-made checks. An addon is a small Paper plugin: extend `Check`, call `flag(player, details)`, register it with one line, and it gets the violation levels, alerts, Discord, profiles, punishments and bypass permissions of the built-in checks. See **[ADDONS.md](ADDONS.md)** and the [example addon](examples/example-addon).

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
- **Pull requests:** keep changes small and focused, with one feature per commit. New calculations go in `logic/` with a unit test that uses real vanilla values. Run `./gradlew build` before you open the PR.

### Project layout

```
src/main/java/dev/rage/anticheat/
  RageAnticheat.java       plugin entry point, wiring, violations, Discord routing
  api/                     public addon API: Check, RageAnticheatApi, PlayerFlagEvent
  MovementListener.java    Flight, Spider, Speed, NoSlow, Jesus, Step, NoFall, Timer, Blink, Phase
  CombatListener.java      Reach, KillAura, CrystalAura, AutoClicker, Criticals
  BlockListener.java       Nuker, FastBreak, AirPlace, Tower, Scaffold, Xray
  InventoryListener.java   AutoTotem, ChestStealer, AutoArmor, InventoryMove
  ClientListener.java      blocked client mods (brand and channels), brand spoofing
  PacketListener.java      InvalidRotation, NoSwing, ImpossibleSprint
  TravelListener.java      ElytraFly, BoatFly, EntitySpeed
  VelocityListener.java    Velocity (anti-knockback)
  AimListener.java         Aim and Bot
  FishListener.java        AutoFish
  ExploitListener.java     book, item NBT and sign crash exploits
  RageCommand.java         /rageanticheat
  Settings.java            config.yml snapshot for the checks
  Messages.java            messages.yml + MiniMessage rendering
  PlayerData.java          per-player check state
  moderation/
    Database.java          SQLite on a dedicated thread
    PlayerStore.java       players, IP history, flags, alts (SQL)
    PunishmentStore.java   punishments (SQL)
    Moderation.java        ban/mute/kick/warn logic shared by commands, anticheat and Discord
    ModerationCommand.java /ban /mute /kick /warn /unban /unmute /clearchat /history
    ChatListener.java      mute enforcement, chat anti-spam and auto-mute
    Maintenance.java       /maintenance, join filter and server list status
    LoginListener.java     ban, VPN and alt checks at login; join tracking
    AntiBot.java           per-IP limit and join-flood attack mode
    VpnCheck.java          proxycheck.io lookups with a cache
    Alts.java              /alts and alt notifications
    Profiles.java          /profile
    Freeze.java            /freeze
  discord/
    DiscordWebhook.java    rate-limited webhook posts
    DiscordBot.java        slash-command bot (JDA)
  logic/                   pure Java, no Bukkit imports, unit-tested
examples/example-addon/    an addon with a SneakSpam check, built with the plugin
```

## License

No license has been chosen yet. Until a `LICENSE` file is added, the default copyright rules apply: others may read the code but have no permission to copy, modify or redistribute it.
