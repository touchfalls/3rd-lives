// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.core.domain.service;

import io.github.lifesmp.core.config.BountySettings;
import io.github.lifesmp.core.config.LifeConfig;
import io.github.lifesmp.core.domain.model.LifeProfile;
import io.github.lifesmp.core.ports.ArenaRepository;
import io.github.lifesmp.core.ports.BountyRepository;
import io.github.lifesmp.core.ports.EconomyIntegration;
import io.github.lifesmp.core.ports.NotificationService;
import io.github.lifesmp.core.ports.PlayerRepository;
import io.github.lifesmp.core.ports.RewardGateway;
import io.github.lifesmp.core.storage.LifeProfileCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BountyServiceTest {

    private static final byte[] ITEMS = {1, 2, 3};

    private LifeProfileCache cache;
    private RewardGateway rewards;
    private BountyService bounties;
    private LifeEngine engine;
    private PvPPolicyEngine pvp;
    private ArenaService arenas;
    private LifeConfig config;
    private BountySettings settings;
    private Instant now;

    private final UUID issuer = UUID.randomUUID();
    private final UUID target = UUID.randomUUID();
    private final UUID hunter = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        PlayerRepository players = mock(PlayerRepository.class);
        when(players.loadPlayer(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(players.savePlayer(any())).thenReturn(CompletableFuture.completedFuture(null));
        cache = new LifeProfileCache(players);

        BountyRepository repo = mock(BountyRepository.class);
        when(repo.loadAll()).thenReturn(CompletableFuture.completedFuture(
                new BountyRepository.Snapshot(List.of(), Map.of(), Map.of())));
        when(repo.flush()).thenReturn(CompletableFuture.completedFuture(null));

        rewards = mock(RewardGateway.class);
        when(rewards.give(any(), any(), anyInt(), anyInt())).thenReturn(true);
        settings = BountySettings.DEFAULTS;
        NotificationService notifications = mock(NotificationService.class);
        bounties = new BountyService(repo, cache, notifications, rewards, () -> settings);
        bounties.load();

        ArenaRepository arenaRepo = mock(ArenaRepository.class);
        when(arenaRepo.loadAllArenas()).thenReturn(CompletableFuture.completedFuture(List.of()));
        arenas = new ArenaService(arenaRepo);
        pvp = new PvPPolicyEngine();
        config = LifeConfig.createDefault();
        engine = new LifeEngine(cache, arenas, pvp, new RegenerationService(), new GiftService(EconomyIntegration.noop()),
                notifications, () -> config);
        engine.setBountyService(bounties);
        now = Instant.parse("2026-03-20T12:00:00Z");

        join(issuer, 3);
        join(target, 3);
        join(hunter, 3);
    }

    private void join(UUID uuid, int lives) {
        LifeProfile p = cache.getOrLoadProfile(uuid, 3, now).join();
        p.setLives(lives);
    }

    private int lives(UUID uuid) {
        return cache.getCachedProfile(uuid).orElseThrow().getLives();
    }

    private BountyService.Status place(int xp, int stakeLives, int buyout) {
        return bounties.place(issuer, "Issuer", "Target", target, ITEMS, xp, stakeLives, buyout, 100, now, config);
    }

    private void kill(UUID victim, UUID killer) {
        now = now.plusSeconds(1);
        engine.handlePlayerDeath(victim, "world", 0, 0, 0, killer, now, config);
    }

    @Test
    void placeTakesLivesAndTax() {
        assertThat(place(10, 1, 20)).isEqualTo(BountyService.Status.OK);
        assertThat(lives(issuer)).isEqualTo(2);
        assertThat(bounties.getBounty(target).orElseThrow().xpLevels()).isEqualTo(9);
        assertThat(bounties.isHunted(target)).isTrue();
    }

    @Test
    void placeValidation() {
        assertThat(bounties.place(issuer, "I", "I", issuer, null, 10, 0, 10, 100, now, config))
                .isEqualTo(BountyService.Status.SELF);
        assertThat(place(1, 0, 10)).isEqualTo(BountyService.Status.XP_BELOW_MIN);
        assertThat(place(10, 0, 1)).isEqualTo(BountyService.Status.BUYOUT_BELOW_MIN);
        assertThat(place(10, 3, 10)).isEqualTo(BountyService.Status.NOT_ENOUGH_LIVES);
        assertThat(bounties.place(issuer, "I", "T", target, null, 500, 0, 10, 100, now, config))
                .isEqualTo(BountyService.Status.NOT_ENOUGH_XP);
        assertThat(place(10, 0, 10)).isEqualTo(BountyService.Status.OK);
        assertThat(place(10, 0, 10)).isEqualTo(BountyService.Status.TARGET_HAS_BOUNTY);
        assertThat(bounties.place(issuer, "I", "H", hunter, null, 10, 0, 10, 100, now, config))
                .isEqualTo(BountyService.Status.ISSUER_LIMIT);
    }

    @Test
    void hunterClaimsStakeAndLifeOverflowBurns() {
        place(10, 1, 10);
        join(hunter, 2);
        kill(target, hunter);
        verify(rewards).give(eq(hunter), eq(ITEMS), eq(100), eq(9));
        assertThat(lives(hunter)).isEqualTo(3);
        assertThat(bounties.getBounty(target)).isEmpty();

        // Life above the maximum is burned
        bounties.place(hunter, "H", "I", issuer, null, 10, 1, 10, 100, now, config);
        kill(issuer, target);
        assertThat(lives(target)).isEqualTo(3);
    }

    @Test
    void issuerCannotClaimOwnBounty() {
        place(10, 0, 10);
        kill(target, issuer);
        verify(rewards, never()).give(any(), any(), anyInt(), anyInt());
        assertThat(bounties.getBounty(target)).isPresent();
    }

    @Test
    void nonPvpDeathAndEventKeepBounty() {
        place(10, 0, 10);
        kill(target, null);
        arenas.setGlobalPvpOverride(true, null);
        kill(target, hunter);
        verify(rewards, never()).give(any(), any(), anyInt(), anyInt());
        assertThat(bounties.getBounty(target)).isPresent();
    }

    @Test
    void redTargetIsFrozen() {
        place(10, 0, 10);
        join(target, 1);
        for (int i = 0; i < settings.durationSeconds() + 5; i++) {
            bounties.tickOnline(target, config);
        }
        assertThat(bounties.getBounty(target).orElseThrow().survivedSeconds()).isZero();
        kill(target, hunter);
        verify(rewards, never()).give(any(), any(), anyInt(), anyInt());
    }

    @Test
    void pairCooldownBlocksSecondClaim() {
        place(10, 0, 10);
        kill(target, hunter);
        join(target, 3);
        bounties.cancel(issuer, config); // no-op, already claimed
        bounties.place(issuer, "I", "T", target, null, 10, 0, 10, 100, now, config);
        kill(target, hunter);
        assertThat(bounties.getBounty(target)).isPresent();
    }

    @Test
    void targetSurvivesAndTakesStake() {
        place(10, 0, 10);
        for (int i = 0; i < settings.durationSeconds(); i++) {
            bounties.tickOnline(target, config);
        }
        verify(rewards).give(eq(target), eq(ITEMS), eq(100), eq(9));
        assertThat(bounties.getBounty(target)).isEmpty();
    }

    @Test
    void cancelRefundsPart() {
        place(20, 2, 10);
        assertThat(bounties.cancel(issuer, config)).isEqualTo(BountyService.Status.OK);
        verify(rewards).give(eq(issuer), eq(ITEMS), eq(75), eq(13)); // 18 * 75%
        assertThat(lives(issuer)).isEqualTo(2); // 1 + 2 * 75% rounded down
    }

    @Test
    void buyoutGivesStakeToTarget() {
        place(10, 0, 30);
        assertThat(bounties.buyout(target, 29, config)).isEqualTo(BountyService.Status.NOT_ENOUGH_XP);
        assertThat(bounties.buyout(target, 30, config)).isEqualTo(BountyService.Status.OK);
        verify(rewards).give(eq(target), eq(ITEMS), eq(100), eq(9));
    }

    @Test
    void streakMakesWantedAndKillerOfWantedGainsLife() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        join(a, 3);
        join(b, 3);
        kill(a, hunter);
        kill(b, hunter);
        assertThat(bounties.isWanted(hunter)).isFalse();
        kill(issuer, hunter);
        assertThat(bounties.isWanted(hunter)).isTrue();

        join(target, 2);
        kill(hunter, target);
        assertThat(bounties.isWanted(hunter)).isFalse();
        assertThat(lives(target)).isEqualTo(3);
    }

    @Test
    void killingWeakerMakesWantedUnlessRetaliating() {
        join(target, 2);
        kill(target, hunter);
        assertThat(bounties.isWanted(hunter)).isTrue();

        UUID green = UUID.randomUUID();
        join(green, 3);
        join(target, 1);
        pvp.recordRetaliation(target, green, now, 60);
        kill(target, green);
        assertThat(bounties.isWanted(green)).isFalse();
    }

    @Test
    void wantedClearsAfterSurviving() {
        join(target, 2);
        kill(target, hunter);
        for (int i = 0; i < settings.durationSeconds(); i++) {
            bounties.tickOnline(hunter, config);
        }
        assertThat(bounties.isWanted(hunter)).isFalse();
    }
}
