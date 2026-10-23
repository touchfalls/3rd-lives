// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) <FILL IN: year> <FILL IN: author>

package io.github.lifesmp.core.domain.model;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Record representing a pending life gift request awaiting acceptance by the recipient.
 *
 * @param donorUuid     UUID of the player giving the life
 * @param recipientUuid UUID of the target player receiving the life
 * @param createdAt     timestamp when the request was initiated
 * @param expiresAt     timestamp when the request expires
 */
public record GiftRequest(
        @NotNull UUID donorUuid,
        @NotNull UUID recipientUuid,
        @NotNull Instant createdAt,
        @NotNull Instant expiresAt
) {

    public GiftRequest {
        Objects.requireNonNull(donorUuid, "donorUuid cannot be null");
        Objects.requireNonNull(recipientUuid, "recipientUuid cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
        Objects.requireNonNull(expiresAt, "expiresAt cannot be null");
    }

    /**
     * Checks if this gift request has expired at the given instant.
     *
     * @param now current instant
     * @return true if expired
     */
    public boolean isExpired(@NotNull Instant now) {
        return now.isAfter(expiresAt);
    }
}
