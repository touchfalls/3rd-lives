// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.paper.util;

import org.bukkit.entity.*;
import org.bukkit.projectiles.ProjectileSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility resolving true player attacker attribution across direct attacks,
 * projectiles, splash/lingering potions, TNT, end crystals, and pet attacks.
 */
public final class AttackerAttributor {

    private final Map<UUID, UUID> crystalTriggerers = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> recentDirectAttackers = new ConcurrentHashMap<>();

    /**
     * Resolves the originating player responsible for damage, if any.
     *
     * @param damagerEntity Bukkit entity causing the damage
     * @return originating Player, or null if non-player source
     */
    @Nullable
    public Player resolveAttacker(@Nullable Entity damagerEntity) {
        if (damagerEntity == null) {
            return null;
        }

        // Direct player attack
        if (damagerEntity instanceof Player player) {
            return player;
        }

        // Projectiles (Arrows, Tridents, Snowballs, etc.)
        if (damagerEntity instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }

        // Primed TNT
        if (damagerEntity instanceof TNTPrimed tnt) {
            Entity source = tnt.getSource();
            if (source instanceof Player player) {
                return player;
            }
        }

        // Area Effect Clouds (Lingering Potions)
        if (damagerEntity instanceof AreaEffectCloud cloud) {
            ProjectileSource source = cloud.getSource();
            if (source instanceof Player player) {
                return player;
            }
        }

        // Tamed Pets (Wolves, Cats, etc.)
        if (damagerEntity instanceof Tameable tameable && tameable.isTamed()) {
            AnimalTamer owner = tameable.getOwner();
            if (owner instanceof Player player) {
                return player;
            }
        }

        // Ender Crystal explosions
        if (damagerEntity instanceof EnderCrystal crystal) {
            UUID triggererUuid = crystalTriggerers.get(crystal.getUniqueId());
            if (triggererUuid != null) {
                return damagerEntity.getServer().getPlayer(triggererUuid);
            }
        }

        return null;
    }

    /**
     * Records a player striking an ender crystal, so the resulting explosion is attributed to them.
     *
     * @param crystalUuid crystal UUID
     * @param playerUuid  striking player UUID
     */
    public void recordCrystalTrigger(@NotNull UUID crystalUuid, @NotNull UUID playerUuid) {
        crystalTriggerers.put(crystalUuid, playerUuid);
    }

    public void removeCrystal(@NotNull UUID crystalUuid) {
        crystalTriggerers.remove(crystalUuid);
    }

    /**
     * Records recent combat strike between players to attribute indirect lethal damage (e.g. knockback into lava).
     */
    public void recordRecentCombatStrike(@NotNull UUID victimUuid, @NotNull UUID attackerUuid) {
        recentDirectAttackers.put(victimUuid, attackerUuid);
    }

    @Nullable
    public UUID getRecentAttacker(@NotNull UUID victimUuid) {
        return recentDirectAttackers.get(victimUuid);
    }

    public void clearRecentCombat(@NotNull UUID victimUuid) {
        recentDirectAttackers.remove(victimUuid);
    }
}
