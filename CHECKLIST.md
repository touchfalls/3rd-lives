# Verification Checklist

This checklist tracks platform API dependencies, Mojang mappings, binary packaging, and runtime assumptions across modules (`:core`, `:paper`, `:fabric`).

## 1. Core Domain (`:core`)

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
  - **Verification:** Verified by compilation against `paperDevBundle("1.21.1-R0.1-SNAPSHOT")` and unit tests.
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
  - **Verification:** Verified by Gradle task `verifyJars`: unpacks and checks nested jars in `dist/fabric/lifesmp-fabric-1.0.0.jar`.
- [x] **Mixin Accessor for `ClientboundPlayerInfoUpdatePacket`**
  - **What:** Accessor mixin mapping `entries` field in `ClientboundPlayerInfoUpdatePacket` to allow injecting custom dead entries without mock `ServerPlayer` entities.
  - **Where:** `io.github.lifesmp.fabric.mixin.ClientboundPlayerInfoUpdatePacketAccessor`.
  - **Verification:** Verified by `lifesmp.refmap.json`: remapped to Intermediary `field_12369:Ljava/util/List;`.
- [x] **Scoreboard Team `zz_dead` in Fabric**
  - **What:** `ServerScoreboard.addPlayerToTeam` with `ChatFormatting.GRAY` for bottom-sorting dead entries.
  - **Where:** `io.github.lifesmp.fabric.scoreboard.FabricScoreboardManager`.
  - **Verification:** Verified by `FabricScoreboardManagerTest` and `FabricTabPresenterTest`.

---

## 4. Verification Commands

Run the full verification suite on a clean environment:
```bash
./gradlew clean dist test verifyJars
```
Expected output:
- All unit and integration tests pass (75/75).
- Both artifacts assembled and verified:
  - `dist/paper/lifesmp-1.0.0.jar`
  - `dist/fabric/lifesmp-fabric-1.0.0.jar`
