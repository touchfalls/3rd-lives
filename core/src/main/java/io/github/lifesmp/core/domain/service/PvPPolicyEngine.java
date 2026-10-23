// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.core.domain.service;

import io.github.lifesmp.core.config.LifeConfig;
import io.github.lifesmp.core.domain.model.LifeProfile;
import io.github.lifesmp.core.domain.model.PvPDecision;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deterministic decision engine for Life SMP PvP interactions.
 * <p>
 * Implements the core PvP matrix:
 * <ul>
 *     <li>Active Arena / Global Override: All PvP allowed</li>
 *     <li>Non-red vs Non-red (2/3 lives): Free combat allowed</li>
 *     <li>Red (1 life) vs Non-red: Allowed; activates defender retaliation window</li>
 *     <li>Non-red vs Red: Denied UNLESS defender is within retaliation window</li>
 *     <li>Red vs Red: Strictly denied outside active arenas</li>
 * </ul>
 */
public final class PvPPolicyEngine {

    /**
     * Stores active retaliation rights:
     * Key: victim player UUID (the player with 2 or 3 lives who was attacked)
     * Value: Map of (redAttackerUuid -> retaliation expiration instant)
     */
    private final Map<UUID, Map<UUID, Instant>> retaliationRegistry = new ConcurrentHashMap<>();

    /**
     * Evaluates whether a PvP damage event is permitted.
     *
     * @param attackerUuid       UUID of the attacking entity/player
     * @param attackerProfile    LifeProfile of the attacker, or null if non-player
     * @param victimUuid         UUID of the defender/victim player
     * @param victimProfile      LifeProfile of the defender
     * @param inActiveArena      whether combat occurs inside an active enabled arena
     * @param globalPvpOverride  whether server-wide PvP override event is active
     * @param now                current instant
     * @param config             active LifeConfig instance
     * @return PvPDecision containing outcome, reason, and any notification message
     */
    @NotNull
    public PvPDecision evaluate(
            @NotNull UUID attackerUuid,
            @Nullable LifeProfile attackerProfile,
            @NotNull UUID victimUuid,
            @Nullable LifeProfile victimProfile,
            boolean inActiveArena,
            boolean globalPvpOverride,
            @NotNull Instant now,
            @NotNull LifeConfig config
    ) {
        Objects.requireNonNull(attackerUuid, "attackerUuid cannot be null");
        Objects.requireNonNull(victimUuid, "victimUuid cannot be null");
        Objects.requireNonNull(now, "now cannot be null");
        Objects.requireNonNull(config, "config cannot be null");

        // Self-damage is never PvP
        if (attackerUuid.equals(victimUuid)) {
            return PvPDecision.denyInvalid("Self damage is not PvP");
        }

        // Active arena or global event overrides all restrictions
        if (inActiveArena || globalPvpOverride) {
            return PvPDecision.allowArenaOverride();
        }

        if (attackerProfile == null || victimProfile == null) {
            return PvPDecision.denyInvalid("Participant profile missing");
        }

        if (attackerProfile.isDead() || victimProfile.isDead()) {
            return PvPDecision.denyInvalid("Participant is dead or inactive");
        }

        int attackerLives = attackerProfile.getLives();
        int victimLives = victimProfile.getLives();

        // Case 1: Attacker is Red (1 life)
        if (attackerLives == 1) {
            if (victimLives == 1) {
                // Red vs Red is forbidden
                return PvPDecision.denyRedCannotAttackRed(
                        config.formatMessage("pvp.red-cannot-attack-red")
                );
            } else {
                // Red attacking Non-red (2 or 3 lives) is permitted and opens retaliation window
                recordRetaliation(attackerUuid, victimUuid, now, config.getRetaliationWindowSeconds());
                return PvPDecision.allowRedAttackingNonRed();
            }
        }

        // Case 2: Attacker is Non-Red (>= 2 lives)
        if (victimLives >= 2) {
            // Non-red vs Non-red (2 vs 2, 3 vs 3, 2 vs 3) is freely permitted
            return PvPDecision.allowNormal();
        }

        // Case 3: Non-Red attacking Red (1 life)
        // Permitted ONLY if Non-red is retaliating within retaliation window against this red player
        if (isRetaliationAllowed(attackerUuid, victimUuid, now)) {
            return PvPDecision.allowRetaliation();
        }

        return PvPDecision.denyCannotAttackRed(
                config.formatMessage("pvp.cannot-attack-red")
        );
    }

    /**
     * Registers a retaliation window allowing the victim to attack the red attacker.
     *
     * @param redAttackerUuid UUID of the red player who attacked
     * @param victimUuid      UUID of the victim who gains retaliation rights
     * @param now             current instant
     * @param windowSeconds   duration of retaliation rights in seconds
     */
    public void recordRetaliation(
            @NotNull UUID redAttackerUuid,
            @NotNull UUID victimUuid,
            @NotNull Instant now,
            long windowSeconds
    ) {
        Instant expiresAt = now.plusSeconds(Math.max(1L, windowSeconds));
        retaliationRegistry
                .computeIfAbsent(victimUuid, k -> new ConcurrentHashMap<>())
                .put(redAttackerUuid, expiresAt);
    }

    /**
     * Checks if the non-red player currently has active retaliation rights against the red player.
     *
     * @param retaliatorUuid UUID of the non-red player retaliating
     * @param redTargetUuid  UUID of the red player target
     * @param now            current instant
     * @return true if retaliation is permitted and unexpired
     */
    public boolean isRetaliationAllowed(
            @NotNull UUID retaliatorUuid,
            @NotNull UUID redTargetUuid,
            @NotNull Instant now
    ) {
        Map<UUID, Instant> map = retaliationRegistry.get(retaliatorUuid);
        if (map == null) {
            return false;
        }
        Instant expiresAt = map.get(redTargetUuid);
        if (expiresAt == null) {
            return false;
        }
        if (now.isAfter(expiresAt)) {
            map.remove(redTargetUuid);
            return false;
        }
        return true;
    }

    /**
     * Clears all retaliation records for a specific player (e.g. on death or disconnect).
     *
     * @param playerUuid UUID of player
     */
    public void clearRetaliationForPlayer(@NotNull UUID playerUuid) {
        retaliationRegistry.remove(playerUuid);
        for (Map<UUID, Instant> map : retaliationRegistry.values()) {
            map.remove(playerUuid);
        }
    }

    /**
     * Clears expired retaliation entries across the registry.
     *
     * @param now current instant
     */
    public void pruneExpired(@NotNull Instant now) {
        Iterator<Map.Entry<UUID, Map<UUID, Instant>>> registryIterator = retaliationRegistry.entrySet().iterator();
        while (registryIterator.hasNext()) {
            Map.Entry<UUID, Map<UUID, Instant>> entry = registryIterator.next();
            Map<UUID, Instant> innerMap = entry.getValue();
            innerMap.entrySet().removeIf(innerEntry -> now.isAfter(innerEntry.getValue()));
            if (innerMap.isEmpty()) {
                registryIterator.remove();
            }
        }
    }
}
