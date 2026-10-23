// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) <FILL IN: year> <FILL IN: author>

package io.github.lifesmp.core.domain.service;

import io.github.lifesmp.core.config.LifeConfig;
import io.github.lifesmp.core.domain.model.LifeProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RegenerationServiceTest {

    private RegenerationService regenerationService;
    private LifeConfig config;
    private Instant now;
    private UUID playerUuid;

    @BeforeEach
    void setUp() {
        regenerationService = new RegenerationService();
        config = LifeConfig.createDefault(); // 10h regen, 2.5h gift regen, 180m afk
        now = Instant.parse("2026-03-20T12:00:00Z");
        playerUuid = UUID.randomUUID();
    }

    @Test
    @DisplayName("Normal regeneration awards 1 life upon reaching 10 hours online and resets timer")
    void normalRegenAwardsLifeAndResetsTimer() {
        LifeProfile profile = new LifeProfile(playerUuid, 2, 0L, null, now, false);
        regenerationService.updatePlayerPosition(playerUuid, "world", 0.0, 64.0, 0.0, now);

        long normalThreshold = config.getRegenSecondsPerLife(); // 36,000 seconds = 10 hours

        // Tick 35,999 seconds: not yet threshold
        RegenerationService.TickResult result1 = regenerationService.tickPlayer(
                playerUuid, profile, normalThreshold - 1, now.plusSeconds(1), config
        );
        assertThat(result1.lifeGained()).isFalse();
        assertThat(profile.getLives()).isEqualTo(2);
        assertThat(profile.getOnlineSecondsSinceLastLoss()).isEqualTo(normalThreshold - 1);

        // Tick 1 more second: threshold reached!
        RegenerationService.TickResult result2 = regenerationService.tickPlayer(
                playerUuid, profile, 1L, now.plusSeconds(2), config
        );
        assertThat(result2.lifeGained()).isTrue();
        assertThat(result2.newLives()).isEqualTo(3);
        assertThat(profile.getLives()).isEqualTo(3);
        assertThat(profile.getOnlineSecondsSinceLastLoss()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Gift-accelerated regeneration awards 1 life after 2.5 hours, then deactivates gift flag")
    void giftRegenAwardsLifeAndDeactivatesFlag() {
        LifeProfile profile = new LifeProfile(playerUuid, 1, 0L, null, now, true); // giftRegenActive = true
        regenerationService.updatePlayerPosition(playerUuid, "world", 10.0, 70.0, 10.0, now);

        long giftThreshold = config.getGiftRegenSecondsPerLife(); // 9,000 seconds = 2.5 hours

        // Advance by 9,000 seconds
        RegenerationService.TickResult result = regenerationService.tickPlayer(
                playerUuid, profile, giftThreshold, now.plusSeconds(10), config
        );

        assertThat(result.lifeGained()).isTrue();
        assertThat(result.newLives()).isEqualTo(2);
        assertThat(profile.getLives()).isEqualTo(2);
        assertThat(profile.getOnlineSecondsSinceLastLoss()).isEqualTo(0L);

        // Accelerated mode remains active until one life is recovered, then returns to normal interval
        assertThat(profile.isGiftRegenActive()).isFalse();

        // Next life requires normal 10 hours threshold (36,000s)
        regenerationService.tickPlayer(playerUuid, profile, giftThreshold, now.plusSeconds(20), config);
        assertThat(profile.getLives()).isEqualTo(2); // still 2 because threshold is now 36,000s
        assertThat(profile.getOnlineSecondsSinceLastLoss()).isEqualTo(giftThreshold);
    }

    @Test
    @DisplayName("AFK detection halts regeneration progress after 180 minutes of inactivity and resumes upon movement")
    void afkHaltsRegenUntilMovement() {
        LifeProfile profile = new LifeProfile(playerUuid, 2, 500L, null, now, false);
        regenerationService.updatePlayerPosition(playerUuid, "world", 0.0, 64.0, 0.0, now);

        // Player is active at T=0
        assertThat(regenerationService.isAfk(playerUuid, now, config.getAfkSeconds())).isFalse();

        // At T = 180 minutes (10,800 seconds): player is considered AFK
        Instant afkInstant = now.plusSeconds(config.getAfkSeconds());
        assertThat(regenerationService.isAfk(playerUuid, afkInstant, config.getAfkSeconds())).isTrue();

        // While AFK, ticking does not advance online seconds
        RegenerationService.TickResult afkResult = regenerationService.tickPlayer(
                playerUuid, profile, 60L, afkInstant, config
        );
        assertThat(afkResult.lifeGained()).isFalse();
        assertThat(profile.getOnlineSecondsSinceLastLoss()).isEqualTo(500L); // Progress frozen at 500

        // Player moves: changes position by 2 blocks
        regenerationService.updatePlayerPosition(playerUuid, "world", 2.0, 64.0, 0.0, afkInstant.plusSeconds(5));
        assertThat(regenerationService.isAfk(playerUuid, afkInstant.plusSeconds(5), config.getAfkSeconds())).isFalse();

        // Ticking resumes progress
        regenerationService.tickPlayer(playerUuid, profile, 60L, afkInstant.plusSeconds(6), config);
        assertThat(profile.getOnlineSecondsSinceLastLoss()).isEqualTo(560L);
    }

    @Test
    @DisplayName("Proportional progress recalculation preserves exact completion ratio on config reload")
    void proportionalRecalculationOnConfigReload() {
        LifeProfile profile = new LifeProfile(playerUuid, 2, 18000L, null, now, false); // 18,000s out of 36,000s = 50%

        // Create new config where regen hours is changed from 10h (36,000s) to 8h (28,800s)
        LifeConfig newConfig = new LifeConfig(
                config.getMaxLives(),
                config.getBanHours(),
                config.getRestoredLivesAfterBan(),
                8.0, // 8 hours
                config.getGiftRegenHoursPerLife(),
                config.getAfkMinutes(),
                config.getRetaliationWindowSeconds(),
                config.getGiftRequestTimeoutSeconds(),
                config.getAutosaveMinutes(),
                config.getLifeColors(),
                config.getMessages()
        );

        regenerationService.adjustProfilesOnConfigReload(
                Collections.singletonList(profile),
                config,
                newConfig
        );

        // 50% of 28,800s = 14,400s
        assertThat(profile.getOnlineSecondsSinceLastLoss()).isEqualTo(14400L);
    }
}
