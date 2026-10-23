// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.paper.task;

import io.github.lifesmp.core.config.LifeConfig;
import io.github.lifesmp.core.domain.service.DeadTabService;
import io.github.lifesmp.core.domain.service.GiftService;
import io.github.lifesmp.core.domain.service.PvPPolicyEngine;
import io.github.lifesmp.core.domain.service.RegenerationService;
import io.github.lifesmp.core.ports.NotificationService;
import io.github.lifesmp.core.storage.LifeProfileCache;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Periodic task running once per second (every 20 ticks) to advance
 * online life regeneration timers for active, non-AFK players,
 * update dead player tab expiry via {@link DeadTabService#tick(Instant)},
 * and prune expired retaliation and gift records without memory leaks.
 */
public final class RegenerationTask implements Runnable {

    private final LifeProfileCache profileCache;
    private final RegenerationService regenerationService;
    private final PvPPolicyEngine pvpPolicyEngine;
    private final GiftService giftService;
    private final NotificationService notificationService;
    private final DeadTabService deadTabService;
    private final Supplier<LifeConfig> configSupplier;

    public RegenerationTask(
            @NotNull LifeProfileCache profileCache,
            @NotNull RegenerationService regenerationService,
            @NotNull PvPPolicyEngine pvpPolicyEngine,
            @NotNull GiftService giftService,
            @NotNull NotificationService notificationService,
            @NotNull Supplier<LifeConfig> configSupplier
    ) {
        this(profileCache, regenerationService, pvpPolicyEngine, giftService, notificationService, null, configSupplier);
    }

    public RegenerationTask(
            @NotNull LifeProfileCache profileCache,
            @NotNull RegenerationService regenerationService,
            @NotNull PvPPolicyEngine pvpPolicyEngine,
            @NotNull GiftService giftService,
            @NotNull NotificationService notificationService,
            @Nullable DeadTabService deadTabService,
            @NotNull Supplier<LifeConfig> configSupplier
    ) {
        this.profileCache = Objects.requireNonNull(profileCache, "profileCache cannot be null");
        this.regenerationService = Objects.requireNonNull(regenerationService, "regenerationService cannot be null");
        this.pvpPolicyEngine = Objects.requireNonNull(pvpPolicyEngine, "pvpPolicyEngine cannot be null");
        this.giftService = Objects.requireNonNull(giftService, "giftService cannot be null");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService cannot be null");
        this.deadTabService = deadTabService;
        this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier cannot be null");
    }

    @Override
    public void run() {
        Instant now = Instant.now();
        LifeConfig config = configSupplier.get();

        // 1. Advance online regeneration
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            profileCache.getCachedProfile(uuid).ifPresent(profile -> {
                RegenerationService.TickResult result = regenerationService.tickPlayer(
                        uuid, profile, 1L, now, config
                );

                if (result.lifeGained()) {
                    profileCache.markDirty(uuid);
                    notificationService.updateDisplayNameAndTab(uuid, result.newLives());
                    notificationService.sendMessage(
                            uuid,
                            config.formatMessage("regen.life-gained", "lives", String.valueOf(result.newLives()))
                    );
                }
            });
        }

        // 2. Periodic dead tab ban pruning & 60-second display refresh
        if (deadTabService != null) {
            deadTabService.tick(now);
        }

        // 3. Periodic pruning of expired retaliation and gift requests (Task 1 point 6)
        pvpPolicyEngine.pruneExpired(now);
        giftService.pruneExpired(now);
    }
}
