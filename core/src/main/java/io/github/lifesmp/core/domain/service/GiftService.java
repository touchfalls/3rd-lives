// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.core.domain.service;

import io.github.lifesmp.core.config.LifeConfig;
import io.github.lifesmp.core.domain.model.GiftRequest;
import io.github.lifesmp.core.domain.model.LifeProfile;
import io.github.lifesmp.core.ports.EconomyIntegration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Service managing atomic life transfer transactions between players.
 * Guarantees idempotency, validation upon acceptance, proportional timer adjustments,
 * and cancellation upon player disconnection.
 */
public final class GiftService {

    private final EconomyIntegration economyIntegration;
    private final Map<UUID, GiftRequest> pendingRequestsByRecipient = new ConcurrentHashMap<>();

    public GiftService(@NotNull EconomyIntegration economyIntegration) {
        this.economyIntegration = Objects.requireNonNull(economyIntegration, "economyIntegration cannot be null");
    }

    public enum InitiateStatus {
        SUCCESS,
        CANNOT_GIFT_SELF,
        DONOR_INSUFFICIENT_LIVES,
        RECIPIENT_MAX_LIVES,
        CANNOT_AFFORD
    }

    public record InitiateResult(
            @NotNull InitiateStatus status,
            @Nullable GiftRequest request,
            @Nullable String errorMessage
    ) {
        public static InitiateResult success(@NotNull GiftRequest request) {
            return new InitiateResult(InitiateStatus.SUCCESS, request, null);
        }

        public static InitiateResult fail(@NotNull InitiateStatus status, @NotNull String message) {
            return new InitiateResult(status, null, message);
        }
    }

    public enum AcceptStatus {
        SUCCESS,
        NO_PENDING_REQUEST,
        REQUEST_EXPIRED,
        DONOR_OFFLINE,
        DONOR_INSUFFICIENT_LIVES,
        RECIPIENT_MAX_LIVES
    }

    public record AcceptResult(
            @NotNull AcceptStatus status,
            @Nullable UUID donorUuid,
            int donorNewLives,
            int recipientNewLives,
            @Nullable String errorMessage
    ) {
        public static AcceptResult success(@NotNull UUID donorUuid, int donorNewLives, int recipientNewLives) {
            return new AcceptResult(AcceptStatus.SUCCESS, donorUuid, donorNewLives, recipientNewLives, null);
        }

        public static AcceptResult fail(@NotNull AcceptStatus status, @NotNull String message) {
            return new AcceptResult(status, null, 0, 0, message);
        }
    }

    /**
     * Initiates a life gift request from a donor to a recipient.
     *
     * @param donorUuid        UUID of donor
     * @param donorProfile     LifeProfile of donor
     * @param recipientUuid    UUID of recipient
     * @param recipientProfile LifeProfile of recipient
     * @param now              current instant
     * @param config           active LifeConfig
     * @return InitiateResult detailing outcome
     */
    @NotNull
    public InitiateResult initiateGift(
            @NotNull UUID donorUuid,
            @NotNull LifeProfile donorProfile,
            @NotNull UUID recipientUuid,
            @NotNull LifeProfile recipientProfile,
            @NotNull Instant now,
            @NotNull LifeConfig config
    ) {
        Objects.requireNonNull(donorUuid, "donorUuid cannot be null");
        Objects.requireNonNull(donorProfile, "donorProfile cannot be null");
        Objects.requireNonNull(recipientUuid, "recipientUuid cannot be null");
        Objects.requireNonNull(recipientProfile, "recipientProfile cannot be null");
        Objects.requireNonNull(now, "now cannot be null");
        Objects.requireNonNull(config, "config cannot be null");

        if (donorUuid.equals(recipientUuid)) {
            return InitiateResult.fail(
                    InitiateStatus.CANNOT_GIFT_SELF,
                    config.formatMessage("gift.cannot-gift-self")
            );
        }

        // Donor must have at least 2 lives to gift, leaving at least 1
        if (donorProfile.getLives() <= 1) {
            return InitiateResult.fail(
                    InitiateStatus.DONOR_INSUFFICIENT_LIVES,
                    config.formatMessage("gift.donor-last-life")
            );
        }

        if (recipientProfile.getLives() >= config.getMaxLives()) {
            return InitiateResult.fail(
                    InitiateStatus.RECIPIENT_MAX_LIVES,
                    config.formatMessage("gift.recipient-max-lives")
            );
        }

        if (!economyIntegration.canAffordGift(donorUuid)) {
            return InitiateResult.fail(
                    InitiateStatus.CANNOT_AFFORD,
                    config.formatMessage("gift.cannot-afford")
            );
        }

        Instant expiresAt = now.plusSeconds(config.getGiftRequestTimeoutSeconds());
        GiftRequest request = new GiftRequest(donorUuid, recipientUuid, now, expiresAt);
        pendingRequestsByRecipient.put(recipientUuid, request);

        return InitiateResult.success(request);
    }

    /**
     * Accepts a pending life gift request.
     * Atomically transfers 1 life from donor to recipient and activates accelerated regen on donor.
     *
     * @param recipientUuid        UUID of accepting player
     * @param recipientProfile     recipient profile
     * @param donorProfileLookup   returns the profile of the donor with the given UUID, or null if the donor is offline
     * @param now                  current instant
     * @param config               active LifeConfig
     * @return AcceptResult detailing outcome
     */
    @NotNull
    public AcceptResult acceptGift(
            @NotNull UUID recipientUuid,
            @NotNull LifeProfile recipientProfile,
            @NotNull Function<UUID, LifeProfile> donorProfileLookup,
            @NotNull Instant now,
            @NotNull LifeConfig config
    ) {
        Objects.requireNonNull(recipientUuid, "recipientUuid cannot be null");
        Objects.requireNonNull(recipientProfile, "recipientProfile cannot be null");
        Objects.requireNonNull(donorProfileLookup, "donorProfileLookup cannot be null");
        Objects.requireNonNull(now, "now cannot be null");
        Objects.requireNonNull(config, "config cannot be null");

        // Atomically remove pending request so it cannot be accepted twice
        GiftRequest request = pendingRequestsByRecipient.remove(recipientUuid);
        if (request == null) {
            return AcceptResult.fail(
                    AcceptStatus.NO_PENDING_REQUEST,
                    config.formatMessage("gift.no-pending")
            );
        }

        if (request.isExpired(now)) {
            return AcceptResult.fail(
                    AcceptStatus.REQUEST_EXPIRED,
                    config.formatMessage("gift.expired")
            );
        }

        LifeProfile donorProfile = donorProfileLookup.apply(request.donorUuid());
        if (donorProfile == null) {
            return AcceptResult.fail(
                    AcceptStatus.DONOR_OFFLINE,
                    config.formatMessage("gift.expired")
            );
        }

        // Lock ordering based on UUID to prevent deadlocks
        Object firstLock = request.donorUuid().compareTo(recipientUuid) < 0 ? donorProfile : recipientProfile;
        Object secondLock = firstLock == donorProfile ? recipientProfile : donorProfile;

        synchronized (firstLock) {
            synchronized (secondLock) {
                // Re-validate state at time of acceptance
                if (donorProfile.getLives() <= 1) {
                    return AcceptResult.fail(
                            AcceptStatus.DONOR_INSUFFICIENT_LIVES,
                            config.formatMessage("gift.donor-last-life")
                    );
                }

                if (recipientProfile.getLives() >= config.getMaxLives()) {
                    return AcceptResult.fail(
                            AcceptStatus.RECIPIENT_MAX_LIVES,
                            config.formatMessage("gift.recipient-max-lives")
                    );
                }

                // Execute atomic transfer
                int donorNewLives = donorProfile.decrementLives();
                int recipientNewLives = recipientProfile.incrementLives();

                // Recipient gained a life: if recipient had giftRegenActive, deactivate it
                if (recipientProfile.isGiftRegenActive()) {
                    recipientProfile.setGiftRegenActive(false);
                    recipientProfile.resetOnlineSeconds();
                }

                // Donor: activate accelerated gift regeneration
                if (!donorProfile.isGiftRegenActive()) {
                    // Recalculate timer progress proportionally to new gift threshold
                    long adjusted = RegenerationService.recalculateProgressProportionally(
                            donorProfile.getOnlineSecondsSinceLastLoss(),
                            config.getRegenSecondsPerLife(),
                            config.getGiftRegenSecondsPerLife()
                    );
                    donorProfile.setOnlineSecondsSinceLastLoss(adjusted);
                    donorProfile.setGiftRegenActive(true);
                }
                // If donor already had giftRegenActive active, progress is preserved and flag remains true

                economyIntegration.onGiftCompleted(request.donorUuid(), recipientUuid);

                return AcceptResult.success(request.donorUuid(), donorNewLives, recipientNewLives);
            }
        }
    }

    /**
     * Cancels any pending gift requests involving the specified player when they disconnect.
     *
     * @param playerUuid UUID of quitting player
     */
    public void onPlayerQuit(@NotNull UUID playerUuid) {
        pendingRequestsByRecipient.remove(playerUuid);
        pendingRequestsByRecipient.entrySet().removeIf(entry -> entry.getValue().donorUuid().equals(playerUuid));
    }

    /**
     * Clears all expired pending gift requests without memory leaks.
     *
     * @param now current instant
     */
    public void pruneExpired(@NotNull Instant now) {
        pendingRequestsByRecipient.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    /**
     * Checks if a recipient has an active pending gift request.
     *
     * @param recipientUuid recipient UUID
     * @param now           current instant
     * @return pending gift request if unexpired
     */
    @Nullable
    public GiftRequest getPendingRequest(@NotNull UUID recipientUuid, @NotNull Instant now) {
        GiftRequest request = pendingRequestsByRecipient.get(recipientUuid);
        if (request != null && request.isExpired(now)) {
            pendingRequestsByRecipient.remove(recipientUuid, request);
            return null;
        }
        return request;
    }
}
