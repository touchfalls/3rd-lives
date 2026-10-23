# Life SMP

A limited-lives survival mechanic for Minecraft 1.21.1 servers, available as a Paper plugin and as a server-side Fabric mod.

[![Build](https://github.com/touchfalls/3rd-lives/actions/workflows/build.yml/badge.svg)](https://github.com/touchfalls/3rd-lives/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/touchfalls/3rd-lives)](https://github.com/touchfalls/3rd-lives/releases)
[![License: GPL-3.0-or-later](https://img.shields.io/badge/license-GPL--3.0--or--later-blue)](LICENSE)
[![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-green)](#requirements)

## What it is

Every player starts with three lives. Each death, from any cause, costs one life, and the player's name changes color to show how many are left: green with three, gold with two, red with one. Losing the last life bans the player for 24 hours of real time. When the ban ends, the player returns with a full set of lives.

Lives come back slowly. Ten hours of active online time restore one life, and the timer pauses while a player is AFK. Players can also give one of their lives to someone else, which costs the donor a life but speeds up the donor's own regeneration.

Red players are protected from being hunted: players with more lives cannot attack them unless the red player attacked first. Red players cannot fight each other. Admins can mark arenas where anyone can fight anyone and deaths cost no lives, or switch on a server-wide PvP event.


## Features

- Lives with colored names in chat and TAB (on Paper also above the player's head).
- A ban of `ban-hours` hours (default 24) after losing the last life, with automatic restoration on the first login after the ban ends.
- PvP protection for players on their last life, with a short retaliation window.
- Life gifting between players, with confirmation and timeout.
- Arenas: cuboid regions where all PvP is allowed and deaths cost no lives.
- A timed global PvP override for events.
- Regeneration based on online time, paused while the player is AFK.
- Banned players shown as gray `[DEAD]` entries at the bottom of the TAB list.
- Admin commands to pardon, reset or set lives.
- Messages in English, Russian, French, Italian and Spanish, each overridable with MiniMessage; reload without restart.
- SQLite storage, written off the main thread.

## Rules

### Who can hit whom

Outside arenas and outside a global PvP event:

| Attacker \ Victim | 3 lives (green) | 2 lives (gold) | 1 life (red) |
|---|---|---|---|
| **3 lives** | yes | yes | only as retaliation |
| **2 lives** | yes | yes | only as retaliation |
| **1 life** | yes | yes | no |

- When a red player hits a green or gold player, that victim may hit the red player back for `retaliation-window-seconds` (default 30). The window is per pair of players and is cleared when either of them dies or disconnects.
- Inside an enabled arena, or while `/life event pvp-override` is active, every player can hit every other player.
- On Paper, damage from projectiles, primed TNT, area effect clouds and tamed pets counts as damage from the player who caused it. On Fabric, the player responsible for the damage source is used, for example the shooter of a projectile.

### Losing lives

- Any death costs one life, except a death inside an enabled arena.
- A death during a global PvP event outside an arena still costs a life.
- Each death resets the regeneration timer.
- On the last remaining life the player gets a warning that the next death leads to a ban.

### Regeneration

- A player with fewer than `max-lives` lives gains one life after `regen-hours-per-life` hours online (default 10).
- Time only counts while the player is online and not AFK. A player is AFK after `afk-minutes` minutes (default 180) without moving.
- After a life is restored, the timer starts again from zero.
- Players at `max-lives` do not accumulate time.
- `/life` shows the current lives, time to the next life, the regeneration mode and the AFK state.

### Ban and unban

- When the last life is lost, the player is kicked and banned for `ban-hours` hours (default 24).
- Connection attempts during the ban are refused with the remaining time.
- On the first login after the ban ends, the player gets `restored-lives-after-ban` lives (default 3) and an empty timer.
- `/life pardon <player>` ends the ban early. The pardoned player gets 1 life, not a full set. A player pardoned while offline is notified on the next login.
- `/life reset <player>` clears any ban and restores `max-lives` lives.
- The ban is kept by Life SMP itself, not in the server's ban list, so the vanilla `/pardon` command does not affect it.

### Gifting

1. The donor runs `/life gift <player>`. The donor needs at least 2 lives, and the recipient must be online and below `max-lives`.
2. The recipient has `gift-request-timeout-seconds` seconds (default 60) to run `/life accept`. A newer request to the same recipient replaces the older one. The request is cancelled if either player disconnects.
3. On acceptance the conditions are checked again, then the donor loses one life and the recipient gains one.
4. The donor switches to accelerated regeneration: the next life takes `gift-regen-hours-per-life` hours (default 2.5) instead of `regen-hours-per-life`. Progress already made is scaled proportionally to the new interval. The accelerated mode ends when the donor regains a life.


## Requirements

- Minecraft Java Edition server 1.21.1.
- Java 21.
- One of:
  - Paper 1.21.1 or a Paper fork such as Purpur (plugin jar);
  - Fabric Loader 0.16.0 or newer with Fabric API (mod jar).
- Server side only. Players use an unmodified vanilla client.

## Installation

Download the jar for your platform from [Releases](https://github.com/touchfalls/3rd-lives/releases):

| Platform | File |
|---|---|
| Paper, Purpur | `lifesmp-paper-<version>.jar` |
| Fabric | `lifesmp-fabric-<version>.jar` |

### Paper

1. Stop the server.
2. Put `lifesmp-paper-<version>.jar` into the `plugins/` folder.
3. Start the server.
4. Check the console for this line:

   ```
   [LifeSMP] Life SMP Paper plugin enabled successfully.
   ```

5. Edit `plugins/LifeSMP/config.yml` if needed and run `/life reload`.

### Fabric

1. Stop the server.
2. Make sure Fabric API for 1.21.1 is in the `mods/` folder.
3. Put `lifesmp-fabric-<version>.jar` into the `mods/` folder.
4. Start the server.
5. Check the log for this line:

   ```
   Life SMP Fabric server adapter initialized successfully.
   ```

6. Edit `config/lifesmp/config.json` if needed and run `/life reload`.

On both platforms the log also shows `Initialized SQLite database at <path>` with the absolute path of the database file.

## Commands and permissions

On Paper, admin commands require the `lifesmp.admin` permission (default: operators). On Fabric, they require operator permission level 4.

| Command | Description | Permission (Paper) | Default access |
|---|---|---|---|
| `/life` | Show your lives, time to the next life, regeneration mode and AFK state | none | everyone |
| `/life help` | List available commands | none | everyone |
| `/life gift <player>` | Offer one of your lives to an online player | none | everyone |
| `/life accept` | Accept a pending life gift | none | everyone |
| `/life reload` | Reload the configuration without resetting player progress | `lifesmp.admin` | operators |
| `/life set <player> <1-3>` | Set a player's lives | `lifesmp.admin` | operators |
| `/life pardon <player>` | End a death ban early; the player gets 1 life | `lifesmp.admin` | operators |
| `/life reset <player>` | Clear any ban and restore `max-lives` lives | `lifesmp.admin` | operators |
| `/life event pvp-override <true\|false> [minutes]` | Turn the global PvP override on or off; `minutes` (1-1440) ends it automatically | `lifesmp.admin` | operators |
| `/life arena create <name>` | Create an arena around your position: 25 blocks in each horizontal direction, 10 below and 30 above | `lifesmp.admin` | operators |
| `/life arena create <name> <x1> <y1> <z1> <x2> <y2> <z2>` | Create an arena from two corners in the current world | `lifesmp.admin` | operators |
| `/life arena list` | List arenas | `lifesmp.admin` | operators |
| `/life arena enable <name>` | Enable an arena | `lifesmp.admin` | operators |
| `/life arena disable <name>` | Disable an arena | `lifesmp.admin` | operators |
| `/life arena remove <name>` | Delete an arena | `lifesmp.admin` | operators |

On Paper, `/lives` and `/lsmp` are aliases of `/life`. `pardon`, `reset` and `set` also accept names of offline players who have joined the server before.

## Configuration

| Platform | File |
|---|---|
| Paper | `plugins/LifeSMP/config.yml` |
| Fabric | `config/lifesmp/config.json` |

The file is created with default values on first start. Both files use the same keys. In `config.json`, `dead-tab` and `colors` are nested objects, as in YAML.

| Key | Default | Description |
|---|---|---|
| `language` | `en` | Language of the built-in messages: `en`, `ru`, `fr`, `it`, `es` |
| `prefix` | `<dark_gray>[<gradient:#55FF55:#FFAA00>Life</gradient>]</dark_gray> ` | Text inserted for `{prefix}` in messages |
| `max-lives` | `3` | Maximum number of lives |
| `ban-hours` | `24.0` | Ban length in real hours after losing the last life |
| `restored-lives-after-ban` | `3` | Lives given on the first login after a ban ends |
| `regen-hours-per-life` | `10.0` | Online hours needed to regenerate one life |
| `gift-regen-hours-per-life` | `2.5` | Online hours per life for a donor after a gift |
| `afk-minutes` | `180.0` | Minutes without movement after which regeneration pauses |
| `retaliation-window-seconds` | `30` | How long a victim may hit back a red attacker |
| `gift-request-timeout-seconds` | `60` | How long a gift request stays valid |
| `autosave-minutes` | `5.0` | Interval for saving changed player data to the database (minimum 1 minute) |
| `dead-tab.enabled` | `true` | Show banned players in the TAB list |
| `dead-tab.format` | `<gray>☠ {player}</gray> <dark_gray>[DEAD]</dark_gray>` | MiniMessage format of a TAB entry; supports `{player}` and `{time}` |
| `dead-tab.color` | `#AAAAAA` | Color of dead entries |
| `dead-tab.show-time` | `false` | Refresh dead entries every 60 seconds so that `{time}` stays current |
| `dead-tab.max-entries` | `50` | Maximum number of dead entries shown |
| `dead-tab.sort-last` | `true` | Place dead entries below all living players |
| `colors.3`, `colors.2`, `colors.1`, `colors.0` | `#55FF55`, `#FFAA00`, `#FF5555`, `#AAAAAA` | Name color for 3, 2, 1 and 0 lives |
| `messages.<key>` | empty | Overrides for single messages of the selected language |

Values below a safe minimum are raised to it: for example `max-lives` and `restored-lives-after-ban` are at least 1.

### Applying changes

Run `/life reload` after editing the file. Regeneration progress is kept: when an interval changes, each player's progress is scaled to the new interval. The autosave task is rescheduled with the new interval.

### Messages

All player-facing texts come from built-in language files. Set `language` to one of the values below and run `/life reload`:

| Value | Language |
|---|---|
| `en` | English (default) |
| `ru` | Russian |
| `fr` | French |
| `it` | Italian |
| `es` | Spanish |

To change a single text, add its key to the `messages` section. An entry there takes precedence over the selected language; every other key keeps the built-in text. The built-in texts and the full list of keys are in [`core/src/main/resources/lifesmp/lang/`](core/src/main/resources/lifesmp/lang). Config files created by earlier builds may still contain a full `messages` section, which then overrides the language; delete that section to use `language`.

Paper (`config.yml`):

```yaml
language: ru
messages:
  gift.no-pending: "{prefix}<red>No gift is waiting for you.</red>"
```

Fabric (`config.json`):

```json
"language": "ru",
"messages": {
  "gift.no-pending": "{prefix}<red>No gift is waiting for you.</red>"
}
```

Messages use [MiniMessage](https://docs.advntr.dev/minimessage/format.html) syntax. Message keys must not be renamed.

| Placeholder | Meaning |
|---|---|
| `{prefix}` | Value of `prefix` |
| `{player}` | Name of the other player |
| `{lives}` | Current number of lives |
| `{max}` | Value of `max-lives` |
| `{hearts}` | Heart symbols for the current lives |
| `{time}` | Duration (ban length, remaining time, regeneration interval, request timeout) |
| `{color}` | Color tag for the current number of lives |
| `{name}`, `{world}`, `{x1}` ... `{z2}`, `{status}`, `{count}` | Arena and event messages |

Message groups: `pvp.*`, `death.*`, `regen.*`, `gift.*`, `info.*` (output of `/life`), `help.*` (output of `/life help`), `arena.*`, `event.*`, `admin.*`, `command.*` (common command errors) and `time.*` (unit suffixes such as `h` and `m`).

## Data storage

All data is stored in a single SQLite database:

| Platform | File |
|---|---|
| Paper | `plugins/LifeSMP/lifesmp.db` |
| Fabric | `config/lifesmp/lifesmp.db` |

The database uses WAL mode, so `lifesmp.db-wal` and `lifesmp.db-shm` may exist next to it while the server runs. The schema is migrated automatically on startup.

To back up:

1. Stop the server. Pending changes are written on shutdown.
2. Copy `lifesmp.db` together with any `lifesmp.db-wal` and `lifesmp.db-shm` files.

With the `sqlite3` tool installed, a consistent copy can also be made while the server runs:

```bash
sqlite3 plugins/LifeSMP/lifesmp.db ".backup lifesmp-backup.db"
```

## FAQ and troubleshooting

**The server fails with `UnsupportedClassVersionError` or refuses to load the mod.**
Life SMP requires Java 21. Check with `java -version` that the server runs on Java 21.

**`NoClassDefFoundError` or `ClassNotFoundException` on startup.**
The jar does not match the platform. Use `lifesmp-paper-<version>.jar` in `plugins/` on Paper and `lifesmp-fabric-<version>.jar` in `mods/` on Fabric. On Fabric, also check that Fabric API is installed.

**A banned player is not shown as DEAD in TAB.**
Check that `dead-tab.enabled` is `true` and that the number of banned players does not exceed `dead-tab.max-entries`. The entry needs the player's name, which is stored on join; a player who has not joined since the name was recorded may be missing. Entries disappear when the ban ends.


**How do I unban a player manually?**
Run `/life pardon <player>` to unban with 1 life, or `/life reset <player>` to unban with full lives. Both work for offline players who have joined the server before. The vanilla `/pardon` command has no effect on Life SMP bans.

**What happens when I update Life SMP?**
Replace the jar and restart. The database schema is migrated automatically and existing data is kept. New config keys fall back to their defaults until you add them to the file. Back up `lifesmp.db` before updating.

## Known limitations

- Only Minecraft 1.21.1 is supported.
- Testing on Paper forks other than Paper itself has been limited.
- The Fabric mod must be installed on the server; installing it on a client has no effect.
- French, Italian and Spanish translations have not yet been reviewed by native speakers. Corrections are welcome.
- `/life set` accepts only values from 1 to 3, regardless of `max-lives`.
- On Fabric, `/lives` and `/lsmp` aliases are not registered.

## For developers

### Architecture

The project follows a ports and adapters layout:

| Module | Contents |
|---|---|
| `:core` | Domain model, services, configuration model, SQLite storage. No dependency on Paper, Fabric or Minecraft. |
| `:paper` | Paper adapter: listeners, Brigadier commands, scheduler, scoreboard teams, TAB packets. |
| `:fabric` | Fabric adapter: Fabric API events, commands, scheduler, mixins, TAB packets. |

Core services: `LifeEngine` (join, death, quit and the one-second tick), `AdminService` (pardon, reset, set lives, name lookup), `PvPPolicyEngine`, `RegenerationService`, `GiftService`, `ArenaService`, `DeadTabService` in `io.github.lifesmp.core.domain.service`.

| Port (`io.github.lifesmp.core.ports`) | Implementations |
|---|---|
| `PlayerRepository` | `SqlitePlayerRepository` (core) |
| `ArenaRepository` | `SqliteArenaRepository` (core) |
| `NotificationService` | `PaperNotificationService`, `FabricNotificationService` |
| `PlatformScheduler` | `PaperPlatformScheduler`, `FabricPlatformScheduler` |
| `TabPresenter` | `PaperTabPresenter`, `FabricTabPresenter` |
| `EconomyIntegration` | `EconomyIntegration.noop()` |
| `Clock` | none yet |

### Building

Requires two JDKs: JDK 25 or newer to run Gradle (Fabric Loom 1.19 does not start on older JVMs) and JDK 21, which Gradle uses as the toolchain to compile the sources. The produced jars run on Java 21. The Gradle wrapper downloads everything else. If Gradle does not find JDK 21 on its own, point it to it with `-Porg.gradle.java.installations.paths=/path/to/jdk-21`.

```bash
./gradlew clean dist
```

Output:

- `dist/paper/lifesmp-<version>.jar`
- `dist/fabric/lifesmp-fabric-<version>.jar`

Run the tests and check the jar contents:

```bash
./gradlew test verifyJars
```

Start a Fabric development server (files go to `fabric/run/`):

```bash
./gradlew :fabric:runServer
```

For Paper, create a folder such as `test-server/` (ignored by Git), place a Paper 1.21.1 server jar there, copy `dist/paper/lifesmp-<version>.jar` into `test-server/plugins/` and start the server with Java 21.

### Forking

| What | Where |
|---|---|
| Version | `mod_version` in `gradle.properties` |
| Group, Java toolchain | `build.gradle.kts` |
| Project name | `settings.gradle.kts` |
| Jar names | `archiveBaseName` in `paper/build.gradle.kts` and `fabric/build.gradle.kts`; file names checked in `verifyJars` in `build.gradle.kts` |
| Java package | `io.github.lifesmp` in all modules; the HikariCP relocation target in `paper/build.gradle.kts` |
| Paper descriptor | `paper/src/main/resources/paper-plugin.yml` (name, main class, permission) |
| Fabric descriptor | `fabric/src/main/resources/fabric.mod.json` (mod id, entrypoint, dependencies) |
| Mixins | `fabric/src/main/resources/lifesmp.mixins.json`, refmap name in `fabric/build.gradle.kts` |

To change the Minecraft version, update the hard-coded versions in `paper/build.gradle.kts` (`paperDevBundle`, `paper-api`) and `fabric/build.gradle.kts` (`minecraft`, `fabric-loader`, `fabric-api`), the `minecraft` dependency in `fabric.mod.json` and `api-version` in `paper-plugin.yml`. The version properties in `gradle.properties` other than `mod_version` are currently not read by the build. Then re-verify:

- the mixin targets in `io.github.lifesmp.fabric.mixin`, especially the `entries` field accessor of `ClientboundPlayerInfoUpdatePacket`;
- TAB packet construction in `PaperTabPresenter` and `FabricTabPresenter`;
- Paper API calls (lifecycle command registration, `AsyncChatEvent`, damage events) and Fabric API events;
- the items in `CHECKLIST.md`.

### Adding a PvP rule

1. Add the rule to `PvPPolicyEngine.evaluate`, before the general cases if it should take precedence.
2. Add a factory method for the outcome to `PvPDecision` and, if needed, a value to `DenialReason`.
3. Add a message key if the attacker should be told why the hit was blocked.
4. Cover it in `PvPPolicyEngineTest`.

### Adding a message

1. Add the key to all five files in `core/src/main/resources/lifesmp/lang/`. `MessageBundleTest` fails if a file is missing a key that `en.properties` has.
2. Send it with `LifeConfig.formatMessage(key, placeholders)`; on Paper, `PaperCommandRegistrar.render` fills `{name}` placeholders through MiniMessage resolvers.
3. Document new placeholders in this README.

To add a language, create `<code>.properties` with all keys of `en.properties` and add the code to `MessageBundle.SUPPORTED_LANGUAGES` and to the check in `verifyJars`.

### Adding a platform

Create a new module that depends on `:core` and implement `NotificationService`, `PlatformScheduler` and `TabPresenter`. Load the configuration into a `LifeConfig`, create `DatabaseManager`, run `SqliteSchemaMigrator`, then build the core services and `LifeEngine` the way `LifeSmpPaperPlugin` and `LifeSmpFabricServer` do. Forward joins, quits, damage, deaths and commands to `LifeEngine` and the services. Once per second call `LifeEngine.tickOnlinePlayer` for every online player with its position, then `LifeEngine.tickServer`; the position is what makes AFK detection work. Run any callback that touches game objects on the server thread, because profile loading completes on the database thread.

### Conventions

- No database IO on the server main thread. Repositories return `CompletableFuture` and run on the database executor; the main thread works with `LifeProfileCache`. A profile is loaded when the player joins and removed from memory after the save on quit.
- Changes to several fields that must be stored together (a death and its ban, a gift) happen inside `synchronized (profile)`, and `LifeProfile.snapshot()` takes the same lock.
- Schema changes are new migrations in `SqliteSchemaMigrator` with an incremented `CURRENT_VERSION`. Existing migrations are never changed.
- Player-facing texts live in the language files under `core/src/main/resources/lifesmp/lang/`; the `messages` section of the configuration only overrides them. Log messages and code comments are in English.
- Every source file starts with the SPDX header. See [CONTRIBUTING.md](CONTRIBUTING.md) for commit style and pull request rules.

## License

Life SMP is licensed under the GNU General Public License v3.0 or later (`GPL-3.0-or-later`). See [LICENSE](LICENSE).

For forks this means: if you distribute a modified version or a work based on it, you must release it under GPL-3.0-or-later as well, make the corresponding source code available, and keep the existing copyright and license notices. Third-party components and their licenses are listed in [NOTICE.md](NOTICE.md). Contributions are accepted under the same license, see [CONTRIBUTING.md](CONTRIBUTING.md).

This section is a summary, not legal advice.
