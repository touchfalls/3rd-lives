// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) <FILL IN: year> <FILL IN: author>

package io.github.lifesmp.core.domain.model;

/**
 * Enumeration of possible outcomes and denial reasons when evaluating PvP actions.
 */
public enum DenialReason {

    /**
     * Combat allowed under normal rules (e.g. 2 vs 2, 3 vs 3, 2 vs 3, or red attacking non-red).
     */
    ALLOWED,

    /**
     * Combat allowed because the defender is retaliating against a red attacker within the retaliation window.
     */
    ALLOWED_RETALIATION,

    /**
     * Combat allowed due to an active arena or global PvP override event.
     */
    ALLOWED_ARENA_OVERRIDE,

    /**
     * Attack denied because a red player (1 life) attempted to attack another red player.
     */
    DENIED_RED_CANNOT_ATTACK_RED,

    /**
     * Attack denied because a non-red player attempted to attack a red player without unexpired retaliation rights.
     */
    DENIED_CANNOT_ATTACK_RED,

    /**
     * Attack denied because one or both participants are dead or have invalid state.
     */
    DENIED_DEAD_OR_INVALID
}
