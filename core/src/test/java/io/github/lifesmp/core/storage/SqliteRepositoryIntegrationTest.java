// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.core.storage;

import io.github.lifesmp.core.domain.model.Arena;
import io.github.lifesmp.core.domain.model.LifeProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SqliteRepositoryIntegrationTest {

    @TempDir
    Path tempDir;

    private Path dbFile;
    private DatabaseManager databaseManager;
    private SqlitePlayerRepository playerRepository;
    private SqliteArenaRepository arenaRepository;

    @BeforeEach
    void setUp() {
        dbFile = tempDir.resolve("lifesmp_test.db");
        databaseManager = new DatabaseManager(dbFile);
        new SqliteSchemaMigrator(databaseManager).migrate();

        playerRepository = new SqlitePlayerRepository(databaseManager);
        arenaRepository = new SqliteArenaRepository(databaseManager);
    }

    @AfterEach
    void tearDown() {
        if (playerRepository != null) playerRepository.close();
        if (arenaRepository != null) arenaRepository.close();
        if (databaseManager != null) databaseManager.close();
    }

    @Test
    @DisplayName("Players table persists the pending pardon notice flag")
    void playerProfilePersistsPardonNotice() {
        UUID playerUuid = UUID.randomUUID();
        LifeProfile profile = new LifeProfile(playerUuid, 1, 0L, null, Instant.parse("2026-03-20T12:00:00Z"), false);
        profile.setPardonNoticePending(true);

        playerRepository.savePlayer(profile).join();

        assertThat(playerRepository.loadPlayer(playerUuid).join().orElseThrow().isPardonNoticePending()).isTrue();
    }

    @Test
    @DisplayName("Players table persists gift_regen_active flag and recovers state after server restart")
    void playerProfileSurvivesRestartWithGiftRegenFlag() {
        UUID playerUuid = UUID.randomUUID();
        Instant now = Instant.parse("2026-03-20T12:00:00Z");

        LifeProfile original = new LifeProfile(
                playerUuid,
                2,
                4500L,
                null,
                now,
                true // gift_regen_active = true
        );

        // Save profile
        playerRepository.savePlayer(original).join();

        // Verify loaded profile
        Optional<LifeProfile> loaded = playerRepository.loadPlayer(playerUuid).join();
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getLives()).isEqualTo(2);
        assertThat(loaded.get().getOnlineSecondsSinceLastLoss()).isEqualTo(4500L);
        assertThat(loaded.get().isGiftRegenActive()).isTrue();

        // Simulate server restart: close all connections and pools
        playerRepository.close();
        databaseManager.close();

        // Re-open brand new DatabaseManager and repository pointing to the same file
        try (DatabaseManager restartedDb = new DatabaseManager(dbFile);
             SqlitePlayerRepository restartedRepo = new SqlitePlayerRepository(restartedDb)) {

            Optional<LifeProfile> afterRestart = restartedRepo.loadPlayer(playerUuid).join();

            assertThat(afterRestart).isPresent();
            assertThat(afterRestart.get().getUuid()).isEqualTo(playerUuid);
            assertThat(afterRestart.get().getLives()).isEqualTo(2);
            assertThat(afterRestart.get().getOnlineSecondsSinceLastLoss()).isEqualTo(4500L);
            assertThat(afterRestart.get().isGiftRegenActive()).isTrue();
        }
    }

    @Test
    @DisplayName("Banned player UUIDs query retrieves active bans using idx_players_banned_until")
    void getBannedPlayerUuidsRetrievesActiveBans() {
        Instant now = Instant.parse("2026-03-20T12:00:00Z");
        long nowSec = now.getEpochSecond();

        UUID activeBanned1 = UUID.randomUUID();
        UUID activeBanned2 = UUID.randomUUID();
        UUID expiredBanned = UUID.randomUUID();
        UUID normalPlayer = UUID.randomUUID();

        // 2 active bans (banned_until > nowSec)
        playerRepository.savePlayer(new LifeProfile(activeBanned1, 0, 0, now.plusSeconds(3600), now, false)).join();
        playerRepository.savePlayer(new LifeProfile(activeBanned2, 0, 0, now.plusSeconds(7200), now, false)).join();

        // 1 expired ban (banned_until < nowSec)
        playerRepository.savePlayer(new LifeProfile(expiredBanned, 0, 0, now.minusSeconds(10), now, false)).join();

        // 1 unbanned player (banned_until == null)
        playerRepository.savePlayer(new LifeProfile(normalPlayer, 3, 0, null, now, false)).join();

        List<UUID> banned = playerRepository.getBannedPlayerUuids(nowSec).join();
        assertThat(banned).containsExactlyInAnyOrder(activeBanned1, activeBanned2);
    }

    @Test
    @DisplayName("Arenas table persists bounding box coordinates and survives reload and removal")
    void arenaPersistenceLifecycle() {
        Arena arena = new Arena("colosseum", "world", -50.0, 60.0, -50.0, 50.0, 120.0, 50.0, true);

        // Save arena
        arenaRepository.saveArena(arena).join();

        // Load all arenas
        List<Arena> arenas = arenaRepository.loadAllArenas().join();
        assertThat(arenas).hasSize(1);
        Arena loaded = arenas.get(0);
        assertThat(loaded.getName()).isEqualTo("colosseum");
        assertThat(loaded.getWorld()).isEqualTo("world");
        assertThat(loaded.getMinX()).isEqualTo(-50.0);
        assertThat(loaded.getMaxX()).isEqualTo(50.0);
        assertThat(loaded.isEnabled()).isTrue();
        assertThat(loaded.contains("world", 0.0, 70.0, 0.0)).isTrue();
        assertThat(loaded.contains("world", 100.0, 70.0, 0.0)).isFalse();

        // Delete arena
        arenaRepository.deleteArena("colosseum").join();
        List<Arena> emptyList = arenaRepository.loadAllArenas().join();
        assertThat(emptyList).isEmpty();
    }

    @Test
    @DisplayName("LifeProfileCache flushes dirty profiles and handles batch autosave")
    void profileCacheFlushesDirtyBatch() {
        LifeProfileCache cache = new LifeProfileCache(playerRepository);
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        Instant now = Instant.now();

        LifeProfile profile1 = cache.getOrLoadProfile(p1, 3, now).join();
        LifeProfile profile2 = cache.getOrLoadProfile(p2, 3, now).join();

        profile1.setLives(1);
        profile2.setLives(2);
        cache.markDirty(p1);
        cache.markDirty(p2);

        // Flush batch
        cache.flushDirtyProfiles().join();

        // Re-read directly from DB
        Optional<LifeProfile> dbP1 = playerRepository.loadPlayer(p1).join();
        Optional<LifeProfile> dbP2 = playerRepository.loadPlayer(p2).join();

        assertThat(dbP1).isPresent();
        assertThat(dbP1.get().getLives()).isEqualTo(1);
        assertThat(dbP2).isPresent();
        assertThat(dbP2.get().getLives()).isEqualTo(2);
    }

    @Test
    @DisplayName("Players table persists last_name and findUuidByLastName finds player case-insensitively")
    void lastNamePersistenceAndCaseInsensitiveLookup() {
        UUID playerUuid = UUID.randomUUID();
        Instant now = Instant.now();

        LifeProfile profile = new LifeProfile(
                playerUuid,
                3,
                0L,
                null,
                now,
                false,
                "CoolPlayer123"
        );
        playerRepository.savePlayer(profile).join();

        // Direct load verification
        Optional<LifeProfile> loaded = playerRepository.loadPlayer(playerUuid).join();
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getLastName()).isEqualTo("CoolPlayer123");

        // Case-insensitive lookups
        Optional<UUID> exact = playerRepository.findUuidByLastName("CoolPlayer123").join();
        assertThat(exact).contains(playerUuid);

        Optional<UUID> lowercase = playerRepository.findUuidByLastName("coolplayer123").join();
        assertThat(lowercase).contains(playerUuid);

        Optional<UUID> uppercase = playerRepository.findUuidByLastName("COOLPLAYER123").join();
        assertThat(uppercase).contains(playerUuid);

        Optional<UUID> unknown = playerRepository.findUuidByLastName("NonExistentPlayer").join();
        assertThat(unknown).isEmpty();
    }

    @Test
    @DisplayName("loadActiveDeadEntries retrieves only players with lives=0, active ban, and non-null valid last_name")
    void loadActiveDeadEntriesFiltersCorrectly() {
        Instant now = Instant.now();
        long nowSec = now.getEpochSecond();

        UUID deadValid = UUID.randomUUID();
        UUID deadNoName = UUID.randomUUID();
        UUID deadExpired = UUID.randomUUID();
        UUID aliveBanned = UUID.randomUUID();

        // 1. Valid dead player
        playerRepository.savePlayer(new LifeProfile(deadValid, 0, 0, now.plusSeconds(3600), now, false, "DeadHero")).join();

        // 2. Dead player without last_name (old record)
        playerRepository.savePlayer(new LifeProfile(deadNoName, 0, 0, now.plusSeconds(3600), now, false, null)).join();

        // 3. Dead player whose ban already expired
        playerRepository.savePlayer(new LifeProfile(deadExpired, 0, 0, now.minusSeconds(100), now, false, "ExpiredHero")).join();

        // 4. Alive player (lives > 0)
        playerRepository.savePlayer(new LifeProfile(aliveBanned, 1, 0, now.plusSeconds(3600), now, false, "AliveHero")).join();

        List<io.github.lifesmp.core.domain.model.DeadEntry> deadEntries = playerRepository.loadActiveDeadEntries(nowSec).join();
        assertThat(deadEntries).hasSize(1);
        assertThat(deadEntries.get(0).uuid()).isEqualTo(deadValid);
        assertThat(deadEntries.get(0).name()).isEqualTo("DeadHero");
    }
}
