// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.core.domain.service;

import io.github.lifesmp.core.config.LifeConfig;
import io.github.lifesmp.core.domain.model.DeadEntry;
import io.github.lifesmp.core.domain.model.LifeProfile;
import io.github.lifesmp.core.ports.*;
import io.github.lifesmp.core.storage.LifeProfileCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeadTabServiceTest {

    private TestTabPresenter tabPresenter;
    private TestPlayerRepository playerRepository;
    private LifeConfig config;
    private DeadTabService deadTabService;

    @BeforeEach
    void setUp() {
        tabPresenter = new TestTabPresenter();
        playerRepository = new TestPlayerRepository();
        config = LifeConfig.createDefault();
        deadTabService = new DeadTabService(tabPresenter, playerRepository, () -> config);
    }

    @Test
    @DisplayName("Recording death ban adds player to cache and calls showDead")
    void recordDeathBanAddsToCacheAndPresenter() {
        UUID uuid = UUID.randomUUID();
        Instant banExpiry = Instant.now().plusSeconds(3600);

        deadTabService.recordDeathBan(uuid, "Alex", banExpiry);

        assertEquals(1, deadTabService.getDeadCount());
        assertTrue(deadTabService.getDeadEntry(uuid).isPresent());
        assertEquals("Alex", deadTabService.getDeadEntry(uuid).get().name());
        assertEquals(banExpiry, deadTabService.getDeadEntry(uuid).get().bannedUntil());

        assertTrue(tabPresenter.shownDead.containsKey(uuid));
        assertEquals("Alex", tabPresenter.shownDead.get(uuid).name());
    }

    @Test
    @DisplayName("Removing dead player removes from cache and calls hideDead")
    void removeDeadRemovesFromCacheAndPresenter() {
        UUID uuid = UUID.randomUUID();
        Instant banExpiry = Instant.now().plusSeconds(3600);
        deadTabService.recordDeathBan(uuid, "Steve", banExpiry);

        deadTabService.removeDead(uuid);

        assertEquals(0, deadTabService.getDeadCount());
        assertFalse(deadTabService.getDeadEntry(uuid).isPresent());
        assertFalse(tabPresenter.shownDead.containsKey(uuid));
        assertTrue(tabPresenter.hiddenDead.contains(uuid));
    }

    @Test
    @DisplayName("Idempotent removeDead: removing non-existent UUID is safe")
    void removeDeadNonExistentIsIdempotent() {
        UUID uuid = UUID.randomUUID();
        assertDoesNotThrow(() -> deadTabService.removeDead(uuid));
        assertTrue(tabPresenter.hiddenDead.contains(uuid));
    }

    @Test
    @DisplayName("Tick naturally expires banned players and triggers hideDead")
    void tickExpiresBannedPlayers() {
        Instant now = Instant.now();
        UUID expiredUuid = UUID.randomUUID();
        UUID activeUuid = UUID.randomUUID();

        deadTabService.recordDeathBan(expiredUuid, "ExpiredUser", now.minusSeconds(10));
        deadTabService.recordDeathBan(activeUuid, "ActiveUser", now.plusSeconds(300));

        assertEquals(2, deadTabService.getDeadCount());

        deadTabService.tick(now);

        assertEquals(1, deadTabService.getDeadCount());
        assertFalse(deadTabService.getDeadEntry(expiredUuid).isPresent());
        assertTrue(deadTabService.getDeadEntry(activeUuid).isPresent());
        assertTrue(tabPresenter.hiddenDead.contains(expiredUuid));
    }

    @Test
    @DisplayName("Tick fast path: empty cache returns immediately without presenter interactions")
    void tickFastPathWhenEmpty() {
        Instant now = Instant.now();
        deadTabService.tick(now);

        assertEquals(0, deadTabService.getDeadCount());
        assertTrue(tabPresenter.hiddenDead.isEmpty());
        assertEquals(0, tabPresenter.refreshCalls.get());
    }

    @Test
    @DisplayName("onPlayerJoin delivers all active dead entries as a batch to the joining viewer")
    void onPlayerJoinDeliversBatch() {
        UUID dead1 = UUID.randomUUID();
        UUID dead2 = UUID.randomUUID();
        Instant expiry = Instant.now().plusSeconds(1000);

        deadTabService.recordDeathBan(dead1, "DeadOne", expiry);
        deadTabService.recordDeathBan(dead2, "DeadTwo", expiry);

        UUID viewerUuid = UUID.randomUUID();
        deadTabService.onPlayerJoin(viewerUuid);

        List<DeadEntry> batch = tabPresenter.shownToViewers.get(viewerUuid);
        assertNotNull(batch);
        assertEquals(2, batch.size());
        assertTrue(batch.stream().anyMatch(e -> e.uuid().equals(dead1)));
        assertTrue(batch.stream().anyMatch(e -> e.uuid().equals(dead2)));
    }

    @Test
    @DisplayName("Conflict resolution: if joining viewer was listed as dead, they are removed from dead cache first")
    void onPlayerJoinConflictResolution() {
        UUID joiningPlayerUuid = UUID.randomUUID();
        deadTabService.recordDeathBan(joiningPlayerUuid, "ConflictPlayer", Instant.now().plusSeconds(500));

        assertEquals(1, deadTabService.getDeadCount());

        deadTabService.onPlayerJoin(joiningPlayerUuid);

        assertEquals(0, deadTabService.getDeadCount());
        assertFalse(deadTabService.getDeadEntry(joiningPlayerUuid).isPresent());
        assertTrue(tabPresenter.hiddenDead.contains(joiningPlayerUuid));
    }

    @Test
    @DisplayName("Name validation: null, blank, or >16 characters names are skipped and not added")
    void nameValidationRejectsInvalidNames() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        UUID u3 = UUID.randomUUID();
        Instant expiry = Instant.now().plusSeconds(500);

        deadTabService.recordDeathBan(u1, null, expiry);
        deadTabService.recordDeathBan(u2, "   ", expiry);
        deadTabService.recordDeathBan(u3, "ThisNameIsWayTooLongForMinecraft", expiry);

        assertEquals(0, deadTabService.getDeadCount());
        assertTrue(tabPresenter.shownDead.isEmpty());
    }

    @Test
    @DisplayName("Duplicate username prevention: entry with same username as another UUID is skipped")
    void duplicateUsernamePrevention() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        Instant expiry = Instant.now().plusSeconds(500);

        deadTabService.recordDeathBan(u1, "DuplicateName", expiry);
        deadTabService.recordDeathBan(u2, "duplicatename", expiry.plusSeconds(100));

        assertEquals(1, deadTabService.getDeadCount());
        assertTrue(deadTabService.getDeadEntry(u1).isPresent());
        assertFalse(deadTabService.getDeadEntry(u2).isPresent());
    }

    @Test
    @DisplayName("Max entries capacity limit: evicts entry expiring earliest when full")
    void maxEntriesCapacityEviction() {
        // Set max entries to 2
        config = new LifeConfig(
                LifeConfig.DEFAULT_PREFIX,
                3, 24.0, 3, 10.0, 2.5, 3.0, 30L, 60L, 5.0,
                true,
                LifeConfig.DEFAULT_DEAD_TAB_FORMAT,
                LifeConfig.DEFAULT_DEAD_TAB_COLOR,
                false,
                2, // max 2
                true,
                Collections.emptyMap(),
                Collections.emptyMap()
        );

        Instant base = Instant.now();
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        UUID u3 = UUID.randomUUID();

        // u1 expires in 100s, u2 expires in 300s
        deadTabService.recordDeathBan(u1, "User1", base.plusSeconds(100));
        deadTabService.recordDeathBan(u2, "User2", base.plusSeconds(300));
        assertEquals(2, deadTabService.getDeadCount());

        // u3 expires in 500s -> should evict u1 (expires earliest: 100s)
        deadTabService.recordDeathBan(u3, "User3", base.plusSeconds(500));
        assertEquals(2, deadTabService.getDeadCount());
        assertFalse(deadTabService.getDeadEntry(u1).isPresent());
        assertTrue(deadTabService.getDeadEntry(u2).isPresent());
        assertTrue(deadTabService.getDeadEntry(u3).isPresent());
        assertTrue(tabPresenter.hiddenDead.contains(u1));

        // u4 expires in 50s (earlier than u2: 300s and u3: 500s) -> should be discarded
        UUID u4 = UUID.randomUUID();
        deadTabService.recordDeathBan(u4, "User4", base.plusSeconds(50));
        assertEquals(2, deadTabService.getDeadCount());
        assertFalse(deadTabService.getDeadEntry(u4).isPresent());
    }

    @Test
    @DisplayName("Initial startup load loads active entries from SQLite up to max-entries")
    void loadInitialDeadEntriesLoadsFromRepository() {
        Instant base = Instant.now();
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        playerRepository.initialEntries.add(new DeadEntry(u1, "StartUser1", base.plusSeconds(200)));
        playerRepository.initialEntries.add(new DeadEntry(u2, "StartUser2", base.plusSeconds(400)));

        deadTabService.loadInitialDeadEntries(base.getEpochSecond()).join();

        assertEquals(2, deadTabService.getDeadCount());
        assertTrue(deadTabService.getDeadEntry(u1).isPresent());
        assertTrue(deadTabService.getDeadEntry(u2).isPresent());
        assertEquals(1, tabPresenter.refreshCalls.get());
    }

    @Test
    @DisplayName("Config reload toggles feature and clears or refreshes entries")
    void configReloadTogglesFeature() {
        UUID u1 = UUID.randomUUID();
        deadTabService.recordDeathBan(u1, "ReloadUser", Instant.now().plusSeconds(600));
        assertEquals(1, deadTabService.getDeadCount());

        // Disable feature
        LifeConfig disabledConfig = new LifeConfig(
                LifeConfig.DEFAULT_PREFIX,
                3, 24.0, 3, 10.0, 2.5, 3.0, 30L, 60L, 5.0,
                false, // disabled
                LifeConfig.DEFAULT_DEAD_TAB_FORMAT,
                LifeConfig.DEFAULT_DEAD_TAB_COLOR,
                false, 50, true,
                Collections.emptyMap(), Collections.emptyMap()
        );

        deadTabService.onConfigReload(config, disabledConfig);
        assertEquals(0, deadTabService.getDeadCount());
        assertTrue(tabPresenter.hiddenDead.contains(u1));

        // Re-enable feature
        playerRepository.initialEntries.add(new DeadEntry(u1, "ReloadUser", Instant.now().plusSeconds(600)));
        deadTabService.onConfigReload(disabledConfig, config);
        assertEquals(1, deadTabService.getDeadCount());
    }

    @Test
    @DisplayName("Shutdown removes all fake entries from all online players")
    void shutdownClearsAllEntries() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        deadTabService.recordDeathBan(u1, "Shut1", Instant.now().plusSeconds(600));
        deadTabService.recordDeathBan(u2, "Shut2", Instant.now().plusSeconds(600));

        deadTabService.shutdown();

        assertEquals(0, deadTabService.getDeadCount());
        assertTrue(tabPresenter.hiddenDead.contains(u1));
        assertTrue(tabPresenter.hiddenDead.contains(u2));
    }

    @Test
    @DisplayName("Integration with LifeEngine: death loss bans player and shows in dead tab; pardon/reset removes them")
    void lifeEngineIntegrationWithDeadTabService() {
        NotificationService notificationService = mock(NotificationService.class);
        LifeProfileCache profileCache = new LifeProfileCache(playerRepository);
        ArenaRepository arenaRepository = mock(ArenaRepository.class);
        when(arenaRepository.loadAllArenas()).thenReturn(CompletableFuture.completedFuture(Collections.emptyList()));
        ArenaService arenaService = new ArenaService(arenaRepository);
        PvPPolicyEngine pvpPolicyEngine = new PvPPolicyEngine();
        RegenerationService regenService = new RegenerationService();
        GiftService giftService = new GiftService(mock(EconomyIntegration.class));

        LifeEngine lifeEngine = new LifeEngine(
                profileCache,
                arenaService,
                pvpPolicyEngine,
                regenService,
                giftService,
                notificationService,
                () -> config
        );
        lifeEngine.setDeadTabService(deadTabService);

        UUID victimUuid = UUID.randomUUID();
        Instant now = Instant.now();

        // Player starts with 1 life
        LifeProfile profile = profileCache.getOrLoadProfile(victimUuid, 1, now).join();
        profile.setLastName("VictimUser");
        profileCache.saveProfileImmediately(profile).join();

        // Victim dies -> loses last life -> banned
        LifeEngine.DeathResult deathResult = lifeEngine.handlePlayerDeath(
                victimUuid, "VictimUser", "world", 0, 64, 0, null, now, config
        );
        assertTrue(deathResult.isBanned());
        assertEquals(0, deathResult.remainingLives());

        // Check that player is now in DeadTabService
        assertTrue(deadTabService.getDeadEntry(victimUuid).isPresent());
        assertEquals("VictimUser", deadTabService.getDeadEntry(victimUuid).get().name());
        assertTrue(tabPresenter.shownDead.containsKey(victimUuid));

        // Admin pardons player by name
        LifeEngine.PardonResult pardonResult = lifeEngine.pardonByName("VictimUser", now).join();
        assertEquals(LifeEngine.PardonStatus.SUCCESS, pardonResult.status());

        // Player must be immediately removed from DeadTabService
        assertFalse(deadTabService.getDeadEntry(victimUuid).isPresent());
        assertTrue(tabPresenter.hiddenDead.contains(victimUuid));
    }

    // --- Test Doubles ---

    private static class TestTabPresenter implements TabPresenter {
        final Map<UUID, DeadEntry> shownDead = new ConcurrentHashMap<>();
        final Set<UUID> hiddenDead = ConcurrentHashMap.newKeySet();
        final Map<UUID, List<DeadEntry>> shownToViewers = new ConcurrentHashMap<>();
        final AtomicInteger refreshCalls = new AtomicInteger();

        @Override
        public void showDead(DeadEntry entry) {
            shownDead.put(entry.uuid(), entry);
            hiddenDead.remove(entry.uuid());
        }

        @Override
        public void hideDead(UUID uuid) {
            shownDead.remove(uuid);
            hiddenDead.add(uuid);
        }

        @Override
        public void showAllTo(UUID viewerUuid, Collection<DeadEntry> entries) {
            shownToViewers.put(viewerUuid, new ArrayList<>(entries));
        }

        @Override
        public void refreshAll(Collection<DeadEntry> entries) {
            refreshCalls.incrementAndGet();
        }
    }

    private static class TestPlayerRepository implements PlayerRepository {
        final Map<UUID, LifeProfile> storage = new ConcurrentHashMap<>();
        final List<DeadEntry> initialEntries = new CopyOnWriteArrayList<>();

        @Override
        public CompletableFuture<Optional<LifeProfile>> loadPlayer(UUID uuid) {
            return CompletableFuture.completedFuture(Optional.ofNullable(storage.get(uuid)));
        }

        @Override
        public CompletableFuture<Void> savePlayer(LifeProfile profile) {
            storage.put(profile.getUuid(), profile);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> saveAll(Collection<LifeProfile> profiles) {
            for (LifeProfile p : profiles) {
                storage.put(p.getUuid(), p);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Optional<UUID>> findUuidByLastName(String lastName) {
            for (LifeProfile p : storage.values()) {
                if (lastName.equalsIgnoreCase(p.getLastName())) {
                    return CompletableFuture.completedFuture(Optional.of(p.getUuid()));
                }
            }
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public CompletableFuture<List<DeadEntry>> loadActiveDeadEntries(long nowEpochSecond) {
            return CompletableFuture.completedFuture(new ArrayList<>(initialEntries));
        }
    }
}
