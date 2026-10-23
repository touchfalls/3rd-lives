// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) <FILL IN: year> <FILL IN: author>

package io.github.lifesmp.core.domain.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Result of evaluating a PvP interaction by {@link io.github.lifesmp.core.domain.service.PvPPolicyEngine}.
 * Contains whether the attack is permitted, the corresponding {@link DenialReason},
 * any message to be presented to the attacker, and whether retaliation should be recorded.
 * <p>
 * Frequently returned allowed outcomes use static singletons to eliminate heap allocations on hot combat paths.
 */
public record PvPDecision(
        boolean allowed,
        @NotNull DenialReason reason,
        @Nullable String message,
        boolean shouldRecordRetaliation
) {

    private static final PvPDecision ALLOW_NORMAL = new PvPDecision(true, DenialReason.ALLOWED, null, false);
    private static final PvPDecision ALLOW_RED_ATTACKING_NON_RED = new PvPDecision(true, DenialReason.ALLOWED, null, true);
    private static final PvPDecision ALLOW_RETALIATION = new PvPDecision(true, DenialReason.ALLOWED_RETALIATION, null, false);
    private static final PvPDecision ALLOW_ARENA_OVERRIDE = new PvPDecision(true, DenialReason.ALLOWED_ARENA_OVERRIDE, null, false);

    public PvPDecision {
        Objects.requireNonNull(reason, "reason cannot be null");
    }

    /**
     * Combat allowed normally without recording retaliation.
     */
    public static PvPDecision allowNormal() {
        return ALLOW_NORMAL;
    }

    /**
     * Combat allowed because red player attacks non-red, which activates defender's retaliation window.
     */
    public static PvPDecision allowRedAttackingNonRed() {
        return ALLOW_RED_ATTACKING_NON_RED;
    }

    /**
     * Combat allowed because non-red defender is retaliating against the red attacker.
     */
    public static PvPDecision allowRetaliation() {
        return ALLOW_RETALIATION;
    }

    /**
     * Combat allowed unconditionally because of active arena or global override.
     */
    public static PvPDecision allowArenaOverride() {
        return ALLOW_ARENA_OVERRIDE;
    }

    /**
     * Combat denied because red player attempted to attack another red player.
     */
    public static PvPDecision denyRedCannotAttackRed(@NotNull String message) {
        return new PvPDecision(false, DenialReason.DENIED_RED_CANNOT_ATTACK_RED, message, false);
    }

    /**
     * Combat denied because non-red player attempted to attack a red player without unexpired retaliation.
     */
    public static PvPDecision denyCannotAttackRed(@NotNull String message) {
        return new PvPDecision(false, DenialReason.DENIED_CANNOT_ATTACK_RED, message, false);
    }

    /**
     * Combat denied because participant is dead or invalid.
     */
    public static PvPDecision denyInvalid(@NotNull String message) {
        return new PvPDecision(false, DenialReason.DENIED_DEAD_OR_INVALID, message, false);
    }
}
