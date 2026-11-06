// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.core.config;

import org.jetbrains.annotations.NotNull;

/**
 * Settings of the bounty system, read from the {@code bounty} section of the platform config.
 *
 * @param enabled             whether bounties and the wanted status work at all
 * @param durationHours       online hours the target (or a wanted player) must survive
 * @param taxPercent          share of the XP stake that is burned when a bounty is placed
 * @param cancelRefundPercent share of the stake returned to the issuer on cancel
 * @param minXp               XP levels every bounty must stake, against spam
 * @param minBuyout           lowest buyout price in XP levels the issuer may set
 * @param pairCooldownHours   hours before the same hunter can claim a bounty on the same target again
 * @param maxActivePerIssuer  active bounties one issuer may hold
 * @param wantedStreak        PvP kills in a row without dying that make a player wanted
 * @param icon                text shown after the name of a hunted player in TAB and above the head
 */
public record BountySettings(
        boolean enabled,
        double durationHours,
        int taxPercent,
        int cancelRefundPercent,
        int minXp,
        int minBuyout,
        double pairCooldownHours,
        int maxActivePerIssuer,
        int wantedStreak,
        @NotNull String icon
) {

    public static final BountySettings DEFAULTS =
            new BountySettings(true, 12.0, 10, 75, 5, 5, 24.0, 1, 3, "☠");

    public BountySettings {
        durationHours = Math.max(0.01, durationHours);
        taxPercent = Math.clamp(taxPercent, 0, 100);
        cancelRefundPercent = Math.clamp(cancelRefundPercent, 0, 100);
        minXp = Math.max(0, minXp);
        minBuyout = Math.max(0, minBuyout);
        pairCooldownHours = Math.max(0.0, pairCooldownHours);
        maxActivePerIssuer = Math.max(1, maxActivePerIssuer);
        wantedStreak = Math.max(1, wantedStreak);
    }

    public long durationSeconds() {
        return Math.round(durationHours * 3600.0);
    }

    public long pairCooldownSeconds() {
        return Math.round(pairCooldownHours * 3600.0);
    }
}
