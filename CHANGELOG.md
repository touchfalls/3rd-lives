# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-10-23

### Added
- **Core Domain (`:core`)**:
  - Hexagonal architecture with zero platform dependencies.
  - Life tracking system supporting configurable maximum lives (default 3: Green, Gold, Red).
  - Death ban logic with real-world hour timeouts and automatic restoration on login after ban expiration.
  - PvP policy engine:
    - Red life combat restrictions (red players cannot initiate attacks against other red players outside arenas).
    - Non-red players cannot attack red players unless retaliating within a configurable time window.
    - Full combat authorization inside registered active arenas or during global PvP events.
  - Life gifting service allowing players with ≥2 lives to offer lives to other players with pending confirmation and timeouts.
  - Time-based regeneration service with proportional progress scaling on configuration reload.
  - AFK detection halting regeneration timer progress during inactivity.
  - Dead TAB presenter service: manages ghost TAB entries for eliminated players without spawning fake server entities.
  - SQLite persistent storage engine with HikariCP connection pooling and automatic schema migrations (V1 through V3).
- **Paper Adapter (`:paper`)**:
  - Native Paper plugin implementation targeting Minecraft 1.21.1 via `paperweight-userdev`.
  - Modern Brigadier command tree registered through Paper Lifecycle Events (`/life`, `/life gift`, `/life accept`, `/life set`, `/life pardon`, `/life reset`, `/life reload`, `/life event`, `/life arena`).
  - Scoreboard team integration (`zz_dead`) with MiniMessage formatting for sorted TAB display.
  - Low-level packet injection for dead TAB updates (`ClientboundPlayerInfoUpdatePacket` and `ClientboundPlayerInfoRemovePacket`).
  - Indirect damage attribution tracking for TNT, primed projectiles, splash potions, and tamed pets.
  - Shaded jar distribution with HikariCP relocated to `io.github.lifesmp.libs.hikari` and un-relocated `sqlite-jdbc` with native libraries intact.
- **Fabric Adapter (`:fabric`)**:
  - Dedicated server Fabric mod targeting Minecraft 1.21.1 via Fabric Loom and official Mojang mappings.
  - Brigadier command registration mapped to vanilla server command sources.
  - Accessor mixin on `ClientboundPlayerInfoUpdatePacket` allowing direct modification of player info entries.
  - Mixin on `Player.getDisplayName()` to color chat and broadcast messages according to life state.
  - Jar-in-Jar bundling of HikariCP and SQLite JDBC.
- **Localization**:
  - Built-in messages in English, Russian, French, Italian and Spanish, selected with the `language` config key (default `en`).
  - All player-facing texts, including `/life help`, command errors, arena and event feedback and time unit suffixes, come from the language files.
  - Any message can still be overridden in the `messages` section of the config file.
- **Project Tooling & Verification**:
  - Unified Gradle root build orchestrating multi-project compilation, test execution, and jar verification (`verifyJars`).
  - GitHub Actions CI workflow for automated testing and artifact assembly on Java 21.
  - Release workflow that publishes `lifesmp-paper-<version>.jar` and `lifesmp-fabric-<version>.jar` for `v*` tags.

