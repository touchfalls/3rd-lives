// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.core.domain.service;

import io.github.lifesmp.core.config.LifeConfig;
import io.github.lifesmp.core.domain.model.DenialReason;
import io.github.lifesmp.core.domain.model.LifeProfile;
import io.github.lifesmp.core.domain.model.PvPDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PvPPolicyEngineTest {

    private PvPPolicyEngine pvpPolicyEngine;
    private LifeConfig config;
    private Instant now;

    private UUID playerGreen; // 3 lives
    private UUID playerGold;  // 2 lives
    private UUID playerRed1;  // 1 life
    private UUID playerRed2;  // 1 life

    private LifeProfile profileGreen;
    private LifeProfile profileGold;
    private LifeProfile profileRed1;
    private LifeProfile profileRed2;

    @BeforeEach
    void setUp() {
        pvpPolicyEngine = new PvPPolicyEngine();
        config = LifeConfig.createDefault();
        now = Instant.parse("2026-03-20T12:00:00Z");

        playerGreen = UUID.randomUUID();
        playerGold = UUID.randomUUID();
        playerRed1 = UUID.randomUUID();
        playerRed2 = UUID.randomUUID();

        profileGreen = new LifeProfile(playerGreen, 3, 0L, null, now, false);
        profileGold = new LifeProfile(playerGold, 2, 0L, null, now, false);
        profileRed1 = new LifeProfile(playerRed1, 1, 0L, null, now, false);
        profileRed2 = new LifeProfile(playerRed2, 1, 0L, null, now, false);
    }

    @Test
    @DisplayName("Non-red players (2 and 3 lives) can freely fight each other")
    void nonRedPlayersCanFreelyFight() {
        // 3 vs 2
        PvPDecision greenVsGold = pvpPolicyEngine.evaluate(
                playerGreen, profileGreen, playerGold, profileGold, false, false, now, config
        );
        assertThat(greenVsGold.allowed()).isTrue();
        assertThat(greenVsGold.reason()).isEqualTo(DenialReason.ALLOWED);

        // 2 vs 3
        PvPDecision goldVsGreen = pvpPolicyEngine.evaluate(
                playerGold, profileGold, playerGreen, profileGreen, false, false, now, config
        );
        assertThat(goldVsGreen.allowed()).isTrue();
        assertThat(goldVsGreen.reason()).isEqualTo(DenialReason.ALLOWED);

        // 3 vs 3
        LifeProfile otherGreen = new LifeProfile(UUID.randomUUID(), 3, 0L, null, now, false);
        PvPDecision greenVsGreen = pvpPolicyEngine.evaluate(
                playerGreen, profileGreen, otherGreen.getUuid(), otherGreen, false, false, now, config
        );
        assertThat(greenVsGreen.allowed()).isTrue();
    }

    @Test
    @DisplayName("Red player cannot attack another red player outside active arena")
    void redCannotAttackRedOutsideArena() {
        PvPDecision redVsRed = pvpPolicyEngine.evaluate(
                playerRed1, profileRed1, playerRed2, profileRed2, false, false, now, config
        );

        assertThat(redVsRed.allowed()).isFalse();
        assertThat(redVsRed.reason()).isEqualTo(DenialReason.DENIED_RED_CANNOT_ATTACK_RED);
        assertThat(redVsRed.message()).contains("Вы не можете нападать, пока у вас 1 жизнь");
    }

    @Test
    @DisplayName("Red player CAN attack non-red players and opens retaliation window")
    void redCanAttackNonRedAndOpensRetaliation() {
        PvPDecision redVsGreen = pvpPolicyEngine.evaluate(
                playerRed1, profileRed1, playerGreen, profileGreen, false, false, now, config
        );

        assertThat(redVsGreen.allowed()).isTrue();
        assertThat(redVsGreen.shouldRecordRetaliation()).isTrue();
        assertThat(pvpPolicyEngine.isRetaliationAllowed(playerGreen, playerRed1, now.plusSeconds(5))).isTrue();
    }

    @Test
    @DisplayName("Non-red player cannot attack red player without prior attack")
    void nonRedCannotAttackRedWithoutPriorAttack() {
        PvPDecision greenVsRed = pvpPolicyEngine.evaluate(
                playerGreen, profileGreen, playerRed1, profileRed1, false, false, now, config
        );

        assertThat(greenVsRed.allowed()).isFalse();
        assertThat(greenVsRed.reason()).isEqualTo(DenialReason.DENIED_CANNOT_ATTACK_RED);
        assertThat(greenVsRed.message()).contains("Вы не можете нападать на игрока с 1 жизнью");
    }

    @Test
    @DisplayName("Non-red player CAN retaliate against red within retaliation window, but not after expiration")
    void nonRedCanRetaliateWithinWindowOnly() {
        // Red attacks Green at T=0
        pvpPolicyEngine.evaluate(playerRed1, profileRed1, playerGreen, profileGreen, false, false, now, config);

        // At T = +10s (window is 30s): Green retaliates
        Instant at10s = now.plusSeconds(10);
        PvPDecision retaliationAllowed = pvpPolicyEngine.evaluate(
                playerGreen, profileGreen, playerRed1, profileRed1, false, false, at10s, config
        );
        assertThat(retaliationAllowed.allowed()).isTrue();
        assertThat(retaliationAllowed.reason()).isEqualTo(DenialReason.ALLOWED_RETALIATION);

        // Third-party (Gold) was not attacked by Red, so Gold CANNOT attack Red
        PvPDecision bystanderGoldVsRed = pvpPolicyEngine.evaluate(
                playerGold, profileGold, playerRed1, profileRed1, false, false, at10s, config
        );
        assertThat(bystanderGoldVsRed.allowed()).isFalse();
        assertThat(bystanderGoldVsRed.reason()).isEqualTo(DenialReason.DENIED_CANNOT_ATTACK_RED);

        // At T = +31s: retaliation window expired (30s)
        Instant at31s = now.plusSeconds(31);
        PvPDecision retaliationExpired = pvpPolicyEngine.evaluate(
                playerGreen, profileGreen, playerRed1, profileRed1, false, false, at31s, config
        );
        assertThat(retaliationExpired.allowed()).isFalse();
        assertThat(retaliationExpired.reason()).isEqualTo(DenialReason.DENIED_CANNOT_ATTACK_RED);
    }

    @Test
    @DisplayName("Active arena or global override allows all combat configurations")
    void activeArenaOverridesAllRestrictions() {
        // Red vs Red in active arena
        PvPDecision arenaRedVsRed = pvpPolicyEngine.evaluate(
                playerRed1, profileRed1, playerRed2, profileRed2, true, false, now, config
        );
        assertThat(arenaRedVsRed.allowed()).isTrue();
        assertThat(arenaRedVsRed.reason()).isEqualTo(DenialReason.ALLOWED_ARENA_OVERRIDE);

        // Green vs Red without retaliation in active arena
        PvPDecision arenaGreenVsRed = pvpPolicyEngine.evaluate(
                playerGreen, profileGreen, playerRed1, profileRed1, true, false, now, config
        );
        assertThat(arenaGreenVsRed.allowed()).isTrue();
        assertThat(arenaGreenVsRed.reason()).isEqualTo(DenialReason.ALLOWED_ARENA_OVERRIDE);

        // Global PvP override
        PvPDecision globalOverride = pvpPolicyEngine.evaluate(
                playerGreen, profileGreen, playerRed1, profileRed1, false, true, now, config
        );
        assertThat(globalOverride.allowed()).isTrue();
        assertThat(globalOverride.reason()).isEqualTo(DenialReason.ALLOWED_ARENA_OVERRIDE);
    }
}
