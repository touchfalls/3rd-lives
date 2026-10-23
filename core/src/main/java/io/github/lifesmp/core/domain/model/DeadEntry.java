// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.core.domain.model;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable value object representing a deceased (banned) player displayed in the TAB list.
 *
 * @param uuid        player UUID
 * @param name        player last known username (up to 16 characters)
 * @param bannedUntil timestamp until which the death ban lasts
 */
public record DeadEntry(
        @NotNull UUID uuid,
        @NotNull String name,
        @NotNull Instant bannedUntil
) {
    public DeadEntry {
        Objects.requireNonNull(uuid, "uuid cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(bannedUntil, "bannedUntil cannot be null");
    }
}
