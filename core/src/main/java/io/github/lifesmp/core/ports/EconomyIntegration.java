// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) <FILL IN: year> <FILL IN: author>

package io.github.lifesmp.core.ports;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Extensible port for integrating external economy systems, fees, or rewards with Life SMP.
 * Default implementation performs no operations.
 */
public interface EconomyIntegration {

    /**
     * Checks if a player can afford to perform a life gift transaction.
     *
     * @param donorUuid player attempting to gift a life
     * @return true if affordable or no fee required
     */
    boolean canAffordGift(@NotNull UUID donorUuid);

    /**
     * Called when a life gift transaction successfully completes.
     *
     * @param donorUuid     player who gifted the life
     * @param recipientUuid player who received the life
     */
    void onGiftCompleted(@NotNull UUID donorUuid, @NotNull UUID recipientUuid);

    /**
     * Default no-op economy integration.
     */
    static EconomyIntegration noop() {
        return new EconomyIntegration() {
            @Override
            public boolean canAffordGift(@NotNull UUID donorUuid) {
                return true;
            }

            @Override
            public void onGiftCompleted(@NotNull UUID donorUuid, @NotNull UUID recipientUuid) {
                // No-op by default
            }
        };
    }
}
