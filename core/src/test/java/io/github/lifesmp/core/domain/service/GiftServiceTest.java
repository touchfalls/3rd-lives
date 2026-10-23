// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.core.domain.service;

import io.github.lifesmp.core.config.LifeConfig;
import io.github.lifesmp.core.domain.model.LifeProfile;
import io.github.lifesmp.core.ports.EconomyIntegration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GiftServiceTest {

    private GiftService giftService;
    private LifeConfig config;
    private Instant now;

    private UUID donorUuid;
    private UUID recipientUuid;
    private UUID thirdPlayerUuid;

    private LifeProfile donorProfile;
    private LifeProfile recipientProfile;
    private LifeProfile thirdPlayerProfile;

    @BeforeEach
    void setUp() {
        giftService = new GiftService(EconomyIntegration.noop());
        config = LifeConfig.createDefault();
        now = Instant.parse("2026-03-20T12:00:00Z");

        donorUuid = UUID.randomUUID();
        recipientUuid = UUID.randomUUID();
        thirdPlayerUuid = UUID.randomUUID();

        // Donor starts with 3 lives and 18,000s progress (50% of 10h)
        donorProfile = new LifeProfile(donorUuid, 3, 18000L, null, now, false);
        recipientProfile = new LifeProfile(recipientUuid, 2, 2000L, null, now, false);
        thirdPlayerProfile = new LifeProfile(thirdPlayerUuid, 1, 0L, null, now, false);
    }

    @Test
    @DisplayName("Player cannot gift life to themselves")
    void cannotGiftSelf() {
        GiftService.InitiateResult result = giftService.initiateGift(
                donorUuid, donorProfile, donorUuid, donorProfile, now, config
        );
        assertThat(result.status()).isEqualTo(GiftService.InitiateStatus.CANNOT_GIFT_SELF);
    }

    @Test
    @DisplayName("Player on their last life (1 life) cannot gift")
    void donorOnLastLifeCannotGift() {
        LifeProfile redDonor = new LifeProfile(UUID.randomUUID(), 1, 0L, null, now, false);
        GiftService.InitiateResult result = giftService.initiateGift(
                redDonor.getUuid(), redDonor, recipientUuid, recipientProfile, now, config
        );
        assertThat(result.status()).isEqualTo(GiftService.InitiateStatus.DONOR_INSUFFICIENT_LIVES);
    }

    @Test
    @DisplayName("Recipient already at max lives (3) cannot receive gift")
    void recipientAtMaxLivesCannotReceive() {
        LifeProfile fullRecipient = new LifeProfile(UUID.randomUUID(), 3, 0L, null, now, false);
        GiftService.InitiateResult result = giftService.initiateGift(
                donorUuid, donorProfile, fullRecipient.getUuid(), fullRecipient, now, config
        );
        assertThat(result.status()).isEqualTo(GiftService.InitiateStatus.RECIPIENT_MAX_LIVES);
    }

    @Test
    @DisplayName("Successful gift transfers life atomically and adjusts donor regen proportionally")
    void successfulGiftTransfersLifeAndAdjustsRegen() {
        // Initiate gift
        GiftService.InitiateResult initResult = giftService.initiateGift(
                donorUuid, donorProfile, recipientUuid, recipientProfile, now, config
        );
        assertThat(initResult.status()).isEqualTo(GiftService.InitiateStatus.SUCCESS);

        // Recipient accepts at T + 10s
        Instant acceptTime = now.plusSeconds(10);
        GiftService.AcceptResult acceptResult = giftService.acceptGift(
                recipientUuid, recipientProfile, () -> donorProfile, acceptTime, config
        );

        assertThat(acceptResult.status()).isEqualTo(GiftService.AcceptStatus.SUCCESS);
        assertThat(donorProfile.getLives()).isEqualTo(2);
        assertThat(recipientProfile.getLives()).isEqualTo(3);

        // Recipient timer is preserved
        assertThat(recipientProfile.getOnlineSecondsSinceLastLoss()).isEqualTo(2000L);

        // Donor gains accelerated regen flag and timer is scaled: 50% of 2.5h (9,000s) = 4,500s
        assertThat(donorProfile.isGiftRegenActive()).isTrue();
        assertThat(donorProfile.getOnlineSecondsSinceLastLoss()).isEqualTo(4500L);
    }

    @Test
    @DisplayName("Gifting life twice in a row: flag stays active and progress is NOT reset or scaled again")
    void giftingTwiceInARowPreservesFlagAndProgress() {
        // First gift: Donor (3 lives, 18,000s) -> Recipient (2 lives)
        giftService.initiateGift(donorUuid, donorProfile, recipientUuid, recipientProfile, now, config);
        giftService.acceptGift(recipientUuid, recipientProfile, () -> donorProfile, now.plusSeconds(5), config);

        assertThat(donorProfile.getLives()).isEqualTo(2);
        assertThat(donorProfile.isGiftRegenActive()).isTrue();
        assertThat(donorProfile.getOnlineSecondsSinceLastLoss()).isEqualTo(4500L);

        // Second gift: Donor (2 lives, 4,500s) -> Third player (1 life)
        Instant secondTime = now.plusSeconds(20);
        GiftService.InitiateResult secondInit = giftService.initiateGift(
                donorUuid, donorProfile, thirdPlayerUuid, thirdPlayerProfile, secondTime, config
        );
        assertThat(secondInit.status()).isEqualTo(GiftService.InitiateStatus.SUCCESS);

        GiftService.AcceptResult secondAccept = giftService.acceptGift(
                thirdPlayerUuid, thirdPlayerProfile, () -> donorProfile, secondTime.plusSeconds(5), config
        );
        assertThat(secondAccept.status()).isEqualTo(GiftService.AcceptStatus.SUCCESS);

        // Donor now has 1 life
        assertThat(donorProfile.getLives()).isEqualTo(1);
        assertThat(thirdPlayerProfile.getLives()).isEqualTo(2);

        // Repeated gift while accelerated regen is active keeps flag enabled without resetting progress
        assertThat(donorProfile.isGiftRegenActive()).isTrue();
        assertThat(donorProfile.getOnlineSecondsSinceLastLoss()).isEqualTo(4500L);
    }

    @Test
    @DisplayName("Duplicate acceptance attempts are idempotent and fail safely")
    void duplicateAcceptFailsSafely() {
        giftService.initiateGift(donorUuid, donorProfile, recipientUuid, recipientProfile, now, config);

        // First accept succeeds
        GiftService.AcceptResult first = giftService.acceptGift(
                recipientUuid, recipientProfile, () -> donorProfile, now.plusSeconds(5), config
        );
        assertThat(first.status()).isEqualTo(GiftService.AcceptStatus.SUCCESS);

        // Second accept immediately fails
        GiftService.AcceptResult second = giftService.acceptGift(
                recipientUuid, recipientProfile, () -> donorProfile, now.plusSeconds(6), config
        );
        assertThat(second.status()).isEqualTo(GiftService.AcceptStatus.NO_PENDING_REQUEST);
        assertThat(recipientProfile.getLives()).isEqualTo(3); // does not increment again
    }

    @Test
    @DisplayName("If donor goes offline before accept, transaction fails without changing lives")
    void donorOfflineAbortsGift() {
        giftService.initiateGift(donorUuid, donorProfile, recipientUuid, recipientProfile, now, config);

        // Donor disconnects -> donorProfileSupplier returns null
        GiftService.AcceptResult result = giftService.acceptGift(
                recipientUuid, recipientProfile, () -> null, now.plusSeconds(5), config
        );

        assertThat(result.status()).isEqualTo(GiftService.AcceptStatus.DONOR_OFFLINE);
        assertThat(donorProfile.getLives()).isEqualTo(3);
        assertThat(recipientProfile.getLives()).isEqualTo(2);
    }

    @Test
    @DisplayName("Gift request expires after configured timeout")
    void giftRequestExpiresAfterTimeout() {
        giftService.initiateGift(donorUuid, donorProfile, recipientUuid, recipientProfile, now, config);

        // Timeout is 60 seconds; accept at 61 seconds
        Instant expiredTime = now.plusSeconds(61);
        GiftService.AcceptResult result = giftService.acceptGift(
                recipientUuid, recipientProfile, () -> donorProfile, expiredTime, config
        );

        assertThat(result.status()).isEqualTo(GiftService.AcceptStatus.REQUEST_EXPIRED);
        assertThat(donorProfile.getLives()).isEqualTo(3);
        assertThat(recipientProfile.getLives()).isEqualTo(2);
    }
}
