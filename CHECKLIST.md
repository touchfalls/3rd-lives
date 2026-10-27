# Verification Checklist

This checklist tracks platform API dependencies, Mojang mappings, binary packaging, and runtime assumptions across modules (`:core`, `:paper`, `:fabric`).

## 1. Core Domain (`:core`)

- [x] **SQLite Schema Migration V4**
  - **What:** Migration adding `players.pardon_notice INTEGER NOT NULL DEFAULT 0`.
  - **Where:** `io.github.lifesmp.core.storage.SqliteSchemaMigrator`.
  - **Verification:** `SqliteRepositoryIntegrationTest` (`playerProfilePersistsPardonNotice`). Start a server on a database created by 1.0.0 and check the log for `Applied migration V4`.
- [x] **SQLite Schema Migration V3**
  - **What:** Migration adding `players.last_name TEXT` and index `idx_players_last_name`.
  - **Where:** `io.github.lifesmp.core.storage.SqliteSchemaMigrator`.
  - **Verification:** Verified by `SqliteRepositoryIntegrationTest` (`playersTablePersistsLastName`, `loadActiveDeadEntries`).
- [x] **Hexagonal Isolation**
  - **What:** Zero dependencies on Bukkit/Paper, Fabric, Minecraft, or Netty in `:core`.
  - **Where:** `core/build.gradle.kts`.
  - **Verification:** Verified by `./gradlew :core:compileJava`.

---

## 2. Paper Platform Adapter (`:paper`)

- [x] **Mojang Mappings via paperweight-userdev**
  - **What:** `paperweight-userdev` plugin version 1.7.4 with `MOJANG_PRODUCTION` reobfuscation configuration.
  - **Where:** `paper/build.gradle.kts:9`.
  - **Verification:** Verified by `./gradlew :paper:compileJava`, `./gradlew :paper:test`, and `./gradlew verifyJars`.
- [x] **HikariCP Relocation & Shading**
  - **What:** HikariCP relocated to `io.github.lifesmp.libs.hikari` to prevent runtime classpath conflicts with other plugins.
  - **Where:** `paper/build.gradle.kts:50`.
  - **Verification:** Verified by Gradle task `verifyJars`: confirms presence of relocated package and absence of un-relocated classes.
- [x] **sqlite-jdbc Un-relocated Packaging**
  - **What:** `org.sqlite` package preserved without relocation so native JNI libraries load correctly, with `META-INF/services/java.sql.Driver` registered.
  - **Where:** `paper/build.gradle.kts:52-63`.
  - **Verification:** Verified by Gradle task `verifyJars`: validates `META-INF/services/java.sql.Driver` contains `org.sqlite.JDBC`.
- [x] **Dead TAB NMS Packet Delivery**
  - **What:** `ClientboundPlayerInfoUpdatePacket` and `ClientboundPlayerInfoRemovePacket` for fake TAB entries.
  - **Where:** `io.github.lifesmp.paper.platform.PaperTabPresenter`.
  - **Verification:** Verified by compilation against `paperDevBundle("1.21.1-R0.1-SNAPSHOT")` and unit tests. The `Entry` record gained `showHat` and `listOrder` after 1.21.1, so `tabEntry` builds it by parameter type; checked against the Paper 26.2 build 134 server classes (sources compile, every field and method the 1.21.1-built classes use exists).
- [x] **Scoreboard Team `zz_dead` Sorting**
  - **What:** Vanilla Minecraft client sorts player TAB entries alphabetically by team name. `zz_dead` sorts after all life teams.
  - **Where:** `io.github.lifesmp.paper.scoreboard.PaperScoreboardManager`.
  - **Verification:** Verified by Paper scoreboard unit tests.

---

## 3. Fabric Platform Adapter (`:fabric`)

- [x] **Mojang Mappings with Loom Official Mappings**
  - **What:** `loom.officialMojangMappings()` for Minecraft 1.21.1.
  - **Where:** `fabric/build.gradle.kts:12`.
  - **Verification:** Verified by `./gradlew :fabric:compileJava`.
- [x] **Jar-in-Jar (JiJ) Bundling of HikariCP and sqlite-jdbc**
  - **What:** Dependencies included via `include(...)` in `fabric/build.gradle.kts`.
  - **Where:** `fabric/build.gradle.kts:18-19`.
  - **Verification:** Verified by Gradle task `verifyJars`: unpacks and checks nested jars in `dist/fabric/lifesmp-fabric-1.1.1.jar`.
- [x] **Mixin Accessor for `ClientboundPlayerInfoUpdatePacket`**
  - **What:** Accessor mixin mapping `entries` field in `ClientboundPlayerInfoUpdatePacket` to allow injecting custom dead entries without mock `ServerPlayer` entities.
  - **Where:** `io.github.lifesmp.fabric.mixin.ClientboundPlayerInfoUpdatePacketAccessor`.
  - **Verification:** Verified by `lifesmp.refmap.json`: remapped to Intermediary `field_12369:Ljava/util/List;`. On 26.x (`:fabric-26`) the game is unobfuscated and no refmap is used.
- [x] **Minecraft 26.2 build (`:fabric-26`)**
  - **What:** The `:fabric` sources compiled against Minecraft 26.2 and Fabric API 0.161.0+26.2 with Java 25. API differences are in `McCompat` (`fabric/src/mc1_21_1/java`, `fabric/src/mc26_2/java`).
  - **Verification:** Verified by `./gradlew :fabric-26:build` (all Fabric tests run against 26.2) and `verifyJars`.
- [ ] **Real-server check on 26.2**
  - **What:** Start Paper 26.2 and Fabric 26.2 servers with the jars, join, die until banned, and check the `[DEAD]` TAB entry, colors and commands.
  - **Status:** Startup checked on Paper 26.2 build 134 and Fabric Loader 0.20.1 with Fabric API 0.161.0+26.2 (Java 25): the plugin and the mod load, all mixins apply, the V1-V4 migrations run, and the console commands `/life`, `/life pardon` and `/life reload` work, with no errors from Life SMP. Not yet checked with a player joining: deaths, bans, colors and the `[DEAD]` TAB entry.
- [x] **Scoreboard Team `zz_dead` in Fabric**
  - **What:** `ServerScoreboard.addPlayerToTeam` with `ChatFormatting.GRAY` for bottom-sorting dead entries.
  - **Where:** `io.github.lifesmp.fabric.scoreboard.FabricScoreboardManager`.
  - **Verification:** Verified by `FabricScoreboardManagerTest` and `FabricTabPresenterTest`.

- [ ] **`sun.misc.Unsafe` fallback in `FabricTabPresenter`**
  - **What:** When `ClientboundPlayerInfoUpdatePacket` does not implement `ClientboundPlayerInfoUpdatePacketAccessor` (the mixin is not applied, for example in plain unit tests), the `entries` field is written through `Unsafe.objectFieldOffset` / `Unsafe.putObject`. `objectFieldOffset` is deprecated for removal and produces a compiler warning on JDK 21.
  - **Where:** `io.github.lifesmp.fabric.platform.FabricTabPresenter` (static initializer and `setEntries`).
  - **Risk:** On a running server the mixin accessor is used, so the fallback is not expected to run. A future JDK may remove or restrict the method; the static initializer catches the failure and the fallback is then disabled.
  - **Verification:** Start a Fabric server, ban a player and confirm the gray `[DEAD]` entry appears in TAB (accessor path). Before moving to a newer JDK, run `./gradlew :fabric:test` and check that `FabricTabPresenterTest` still passes, or replace the fallback with a test-only mixin environment.

- [ ] **Dead TAB entry after the kick**
  - **What:** The fake `[DEAD]` entry uses the banned player's UUID. The game removes that UUID for everyone when the player disconnects, so the entry is shown `QUIT_GRACE_TICKS` (Paper, 10 ticks) / `QUIT_GRACE_MS` (Fabric, 500 ms) after the kick.
  - **Where:** `io.github.lifesmp.paper.platform.PaperTabPresenter#showDead`, `io.github.lifesmp.fabric.platform.FabricTabPresenter#showDead`.
  - **Verification:** With two players online, let one lose the last life. The other player must see the gray `[DEAD]` entry at the bottom of TAB after the kick, and a third player who joins later must see it too. Run `/life pardon` right after the death: the entry must not come back. Repeat on both platforms. If the entry still disappears, raise the grace period.

---

## 4. Localization

- [x] **Message bundles**
  - **What:** `core/src/main/resources/lifesmp/lang/{en,ru,fr,it,es}.properties`, loaded by `MessageBundle` with English as fallback for missing keys.
  - **Verification:** `MessageBundleTest` checks that every bundle has exactly the keys of `en.properties`; `verifyJars` checks that all five files are present in both jars.
- [ ] **Translations reviewed by native speakers**
  - **What:** French, Italian and Spanish texts have not yet been reviewed by native speakers.
  - **Verification:** Set `language` to each value, run `/life`, `/life help`, a gift, a death and a ban, and read the output in game.

---

## 5. Verification Commands

Run the full verification suite on a clean environment:
```bash
./gradlew clean dist test verifyJars
```
Expected output:
- All unit and integration tests pass (91/91).
- Both artifacts assembled and verified:
  - `dist/paper/lifesmp-1.1.1.jar`
  - `dist/fabric/lifesmp-fabric-1.1.1.jar`
