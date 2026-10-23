// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.core.domain.service;

import io.github.lifesmp.core.config.LifeConfig;
import io.github.lifesmp.core.domain.model.LifeProfile;
import io.github.lifesmp.core.ports.ArenaRepository;
import io.github.lifesmp.core.ports.EconomyIntegration;
import io.github.lifesmp.core.ports.NotificationService;
import io.github.lifesmp.core.ports.PlayerRepository;
import io.github.lifesmp.core.storage.LifeProfileCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LifeEngineTest {

    private LifeEngine lifeEngine;
    private LifeProfileCache profileCache;
    private PlayerRepository playerRepository;
    private ArenaService arenaService;
    private PvPPolicyEngine pvpPolicyEngine;
    private RegenerationService regenerationService;
    private GiftService giftService;
    private NotificationService notificationService;
    private LifeConfig config;
    private Instant now;

    // In-memory fake store for repository
    private final Map<UUID, LifeProfile> dbStore = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        playerRepository = mock(PlayerRepository.class);
        when(playerRepository.loadPlayer(any(UUID.class))).thenAnswer(invocation -> {
            UUID uuid = invocation.getArgument(0);
            LifeProfile p = dbStore.get(uuid);
            return CompletableFuture.completedFuture(Optional.ofNullable(p != null ? p.snapshot() : null));
        });
        when(playerRepository.savePlayer(any(LifeProfile.class))).thenAnswer(invocation -> {
            LifeProfile p = invocation.getArgument(0);
            dbStore.put(p.getUuid(), p.snapshot());
            return CompletableFuture.completedFuture(null);
        });
        when(playerRepository.saveAll(anyCollection())).thenAnswer(invocation -> {
            Collection<LifeProfile> list = invocation.getArgument(0);
            for (LifeProfile p : list) {
                dbStore.put(p.getUuid(), p.snapshot());
            }
            return CompletableFuture.completedFuture(null);
        });

        profileCache = new LifeProfileCache(playerRepository);

        ArenaRepository arenaRepository = mock(ArenaRepository.class);
        when(arenaRepository.loadAllArenas()).thenReturn(CompletableFuture.completedFuture(Collections.emptyList()));
        arenaService = new ArenaService(arenaRepository);

        pvpPolicyEngine = new PvPPolicyEngine();
        regenerationService = new RegenerationService();
        giftService = new GiftService(mock(EconomyIntegration.class));
        notificationService = mock(NotificationService.class);

        config = LifeConfig.createDefault();
        now = Instant.parse("2026-03-20T12:00:00Z");

        lifeEngine = new LifeEngine(
                profileCache,
                arenaService,
                pvpPolicyEngine,
                regenerationService,
                giftService,
                notificationService,
                () -> config
        );
    }

    @Test
    @DisplayName("Pardoning a banned player gives exactly 1 life and resets timers")
    void testPardonBannedPlayer() {
        UUID uuid = UUID.randomUUID();
        Instant banUntil = now.plusSeconds(3600);
        LifeProfile bannedProfile = new LifeProfile(uuid, 0, 1000L, banUntil, now, true);
        dbStore.put(uuid, bannedProfile);

        LifeEngine.PardonResult result = lifeEngine.pardon(uuid, now).join();

        assertThat(result.status()).isEqualTo(LifeEngine.PardonStatus.SUCCESS);
        assertThat(result.profile()).isNotNull();
        assertThat(result.profile().getLives()).isEqualTo(1);
        assertThat(result.profile().getBannedUntil()).isNull();
        assertThat(result.profile().getOnlineSecondsSinceLastLoss()).isEqualTo(0L);
        assertThat(result.profile().isGiftRegenActive()).isFalse();

        // Check persisted state
        LifeProfile persisted = dbStore.get(uuid);
        assertThat(persisted.getLives()).isEqualTo(1);
        assertThat(persisted.getBannedUntil()).isNull();
        assertThat(persisted.getOnlineSecondsSinceLastLoss()).isEqualTo(0L);
        assertThat(persisted.isGiftRegenActive()).isFalse();

        // Check display name update
        verify(notificationService).updateDisplayNameAndTab(uuid, 1);
    }

    @Test
    @DisplayName("Edge Case: Pardoned player does NOT get restored to 3 lives when joining after ban timestamp")
    void testPardonedPlayerDoesNotRestoreToThreeLivesOnJoin() {
        UUID uuid = UUID.randomUUID();
        Instant banUntil = now.plusSeconds(3600);
        LifeProfile bannedProfile = new LifeProfile(uuid, 0, 1000L, banUntil, now, true);
        dbStore.put(uuid, bannedProfile);

        // Admin pardons player
        lifeEngine.pardon(uuid, now).join();

        // Simulate time advancing past original ban time
        Instant joinTime = now.plusSeconds(7200);

        // Pardoned player joins server
        LifeEngine.JoinEvaluation eval = lifeEngine.handlePlayerJoin(uuid, joinTime, config).join();

        assertThat(eval.allowed()).isTrue();
        // Crucial requirement: player must still have 1 life, NOT restored to 3 lives!
        assertThat(eval.profile().getLives()).isEqualTo(1);
        assertThat(eval.profile().getBannedUntil()).isNull();

        // Player must receive pardon notification upon join
        verify(notificationService).sendMessage(eq(uuid), contains("Вы были помилованы администратором"));
    }

    @Test
    @DisplayName("Natural ban expiry restores player to 3 lives upon first join")
    void testNaturalBanExpiryRestoresLivesToThree() {
        UUID uuid = UUID.randomUUID();
        Instant banUntil = now.plusSeconds(3600);
        // Player is dead (0 lives) with expired ban
        LifeProfile expiredBanProfile = new LifeProfile(uuid, 0, 500L, banUntil, now, true);
        dbStore.put(uuid, expiredBanProfile);

        Instant joinTime = now.plusSeconds(3601); // 1 second after ban expired
        LifeEngine.JoinEvaluation eval = lifeEngine.handlePlayerJoin(uuid, joinTime, config).join();

        assertThat(eval.allowed()).isTrue();
        // Restored to restoredLivesAfterBan (3)
        assertThat(eval.profile().getLives()).isEqualTo(3);
        assertThat(eval.profile().getBannedUntil()).isNull();
        assertThat(eval.profile().getOnlineSecondsSinceLastLoss()).isEqualTo(0L);
        assertThat(eval.profile().isGiftRegenActive()).isFalse();
    }

    @Test
    @DisplayName("Player joining during active ban is rejected with ban-join message")
    void testJoinDuringActiveBanIsRejected() {
        UUID uuid = UUID.randomUUID();
        Instant banUntil = now.plusSeconds(7200); // 2 hours remaining
        LifeProfile activeBanProfile = new LifeProfile(uuid, 0, 0L, banUntil, now, false);
        dbStore.put(uuid, activeBanProfile);

        LifeEngine.JoinEvaluation eval = lifeEngine.handlePlayerJoin(uuid, now, config).join();

        assertThat(eval.allowed()).isFalse();
        assertThat(eval.kickReason()).contains("Вы забанены за потерю всех жизней!");
        assertThat(eval.kickReason()).contains("2ч 0м");
    }

    @Test
    @DisplayName("Pardoning a player who is not banned returns NOT_BANNED and does not modify lives")
    void testPardonNonBannedPlayerFailsGracefully() {
        UUID uuid = UUID.randomUUID();
        LifeProfile normalProfile = new LifeProfile(uuid, 2, 500L, null, now, false);
        dbStore.put(uuid, normalProfile);

        LifeEngine.PardonResult result = lifeEngine.pardon(uuid, now).join();

        assertThat(result.status()).isEqualTo(LifeEngine.PardonStatus.NOT_BANNED);
        assertThat(result.profile().getLives()).isEqualTo(2);
        assertThat(dbStore.get(uuid).getLives()).isEqualTo(2);
    }

    @Test
    @DisplayName("Pardoning a non-existent player returns NOT_FOUND")
    void testPardonNonExistentPlayer() {
        UUID uuid = UUID.randomUUID();
        LifeEngine.PardonResult result = lifeEngine.pardon(uuid, now).join();

        assertThat(result.status()).isEqualTo(LifeEngine.PardonStatus.NOT_FOUND);
        assertThat(result.profile()).isNull();
    }

    @Test
    @DisplayName("Resetting a player restores all lives to max and clears ban")
    void testResetPlayer() {
        UUID uuid = UUID.randomUUID();
        Instant banUntil = now.plusSeconds(3600);
        LifeProfile bannedProfile = new LifeProfile(uuid, 0, 800L, banUntil, now, true);
        dbStore.put(uuid, bannedProfile);

        LifeEngine.ResetResult result = lifeEngine.reset(uuid, config, now).join();

        assertThat(result.status()).isEqualTo(LifeEngine.ResetStatus.SUCCESS);
        assertThat(result.profile().getLives()).isEqualTo(3);
        assertThat(result.profile().getBannedUntil()).isNull();
        assertThat(result.profile().getOnlineSecondsSinceLastLoss()).isEqualTo(0L);
        assertThat(result.profile().isGiftRegenActive()).isFalse();

        // Check persisted state
        LifeProfile persisted = dbStore.get(uuid);
        assertThat(persisted.getLives()).isEqualTo(3);
        assertThat(persisted.getBannedUntil()).isNull();

        // Tab and display name updated
        verify(notificationService).updateDisplayNameAndTab(uuid, 3);
        // Message sent to player
        verify(notificationService).sendMessage(eq(uuid), contains("полностью восстановил ваши жизни"));
    }

    @Test
    @DisplayName("Resetting a non-existent player returns NOT_FOUND")
    void testResetNonExistentPlayer() {
        UUID uuid = UUID.randomUUID();
        LifeEngine.ResetResult result = lifeEngine.reset(uuid, config, now).join();

        assertThat(result.status()).isEqualTo(LifeEngine.ResetStatus.NOT_FOUND);
        assertThat(result.profile()).isNull();
    }

    @Test
    @DisplayName("handlePlayerDeath triggers last life warning on reaching 1 life, and bans on 0 lives")
    void testDeathTransitions() {
        UUID uuid = UUID.randomUUID();
        LifeProfile profile = new LifeProfile(uuid, 2, 500L, null, now, false);
        dbStore.put(uuid, profile);

        // Death 1: lives 2 -> 1
        LifeEngine.DeathResult r1 = lifeEngine.handlePlayerDeath(uuid, "world", 0, 64, 0, null, now, config);
        assertThat(r1.lifeLost()).isTrue();
        assertThat(r1.isBanned()).isFalse();
        assertThat(r1.remainingLives()).isEqualTo(1);

        // Should receive life-lost AND last-life-warning
        verify(notificationService).sendMessage(eq(uuid), contains("Осталось жизней:"));
        verify(notificationService).sendMessage(eq(uuid), contains("Это ваша последняя жизнь!"));

        // Death 2: lives 1 -> 0 (death ban)
        Instant death2Time = now.plusSeconds(10);
        LifeEngine.DeathResult r2 = lifeEngine.handlePlayerDeath(uuid, "world", 0, 64, 0, null, death2Time, config);
        assertThat(r2.lifeLost()).isTrue();
        assertThat(r2.isBanned()).isTrue();
        assertThat(r2.remainingLives()).isEqualTo(0);

        // Kick invoked
        ArgumentCaptor<String> kickCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService).kickPlayer(eq(uuid), kickCaptor.capture());
        assertThat(kickCaptor.getValue()).contains("ВЫ ПОТЕРЯЛИ ВСЕ ЖИЗНИ!");
    }
}
