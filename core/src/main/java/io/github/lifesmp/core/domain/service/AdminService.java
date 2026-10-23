// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.core.domain.service;

import io.github.lifesmp.core.config.LifeConfig;
import io.github.lifesmp.core.domain.model.LifeProfile;
import io.github.lifesmp.core.ports.NotificationService;
import io.github.lifesmp.core.storage.LifeProfileCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Administrator actions on player lives: pardon, reset and set lives.
 * All actions work for offline players too.
 */
public final class AdminService {

    public enum PardonStatus {
        SUCCESS,
        NOT_BANNED,
        NOT_FOUND
    }

    public record PardonResult(@NotNull PardonStatus status, @Nullable LifeProfile profile) {
        public static PardonResult success(@NotNull LifeProfile profile) {
            return new PardonResult(PardonStatus.SUCCESS, profile);
        }

        public static PardonResult notBanned(@NotNull LifeProfile profile) {
            return new PardonResult(PardonStatus.NOT_BANNED, profile);
        }

        public static PardonResult notFound() {
            return new PardonResult(PardonStatus.NOT_FOUND, null);
        }
    }

    public enum ResetStatus {
        SUCCESS,
        NOT_FOUND
    }

    public record ResetResult(@NotNull ResetStatus status, @Nullable LifeProfile profile) {
        public static ResetResult success(@NotNull LifeProfile profile) {
            return new ResetResult(ResetStatus.SUCCESS, profile);
        }

        public static ResetResult notFound() {
            return new ResetResult(ResetStatus.NOT_FOUND, null);
        }
    }

    private final LifeProfileCache profileCache;
    private final NotificationService notificationService;
    private final Supplier<DeadTabService> deadTabServiceSupplier;

    public AdminService(
            @NotNull LifeProfileCache profileCache,
            @NotNull NotificationService notificationService,
            @NotNull Supplier<DeadTabService> deadTabServiceSupplier
    ) {
        this.profileCache = Objects.requireNonNull(profileCache, "profileCache cannot be null");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService cannot be null");
        this.deadTabServiceSupplier = Objects.requireNonNull(deadTabServiceSupplier, "deadTabServiceSupplier cannot be null");
    }

    /**
     * Resolves a player name to a UUID: the online UUID if given, otherwise the last known name
     * stored for the player. Works for offline players.
     *
     * @param name       player name typed by the administrator
     * @param onlineUuid UUID of the online player with this name, or null if nobody online has it
     * @return CompletableFuture completing with the UUID, or empty if the player is unknown
     */
    @NotNull
    public CompletableFuture<Optional<UUID>> resolveUuid(@NotNull String name, @Nullable UUID onlineUuid) {
        if (onlineUuid != null) {
            return CompletableFuture.completedFuture(Optional.of(onlineUuid));
        }
        return profileCache.findUuidByLastName(name);
    }

    /**
     * Ends a death ban early. The player gets exactly 1 life and is told on the next login.
     *
     * @param uuid player UUID
     * @param now  current instant
     * @return CompletableFuture completing with PardonResult
     */
    @NotNull
    public CompletableFuture<PardonResult> pardon(@NotNull UUID uuid, @NotNull Instant now) {
        return profileCache.loadPlayerOffline(uuid).thenApply(opt -> {
            if (opt.isEmpty()) {
                return PardonResult.notFound();
            }
            LifeProfile profile = opt.get();
            if (!profile.isBanned(now)) {
                return PardonResult.notBanned(profile);
            }

            synchronized (profile) {
                profile.setLives(1);
                profile.setBannedUntil(null);
                profile.resetOnlineSeconds();
                profile.setGiftRegenActive(false);
                profile.setPardonNoticePending(true);
            }

            DeadTabService deadTabService = deadTabServiceSupplier.get();
            if (deadTabService != null) {
                deadTabService.removeDead(uuid);
            }

            profileCache.saveProfileImmediately(profile);
            notificationService.updateDisplayNameAndTab(uuid, 1);

            return PardonResult.success(profile);
        });
    }

    /**
     * Restores a player to the maximum number of lives and clears any ban.
     *
     * @param uuid   player UUID
     * @param config active configuration
     * @return CompletableFuture completing with ResetResult
     */
    @NotNull
    public CompletableFuture<ResetResult> reset(@NotNull UUID uuid, @NotNull LifeConfig config) {
        return profileCache.loadPlayerOffline(uuid).thenApply(opt -> {
            if (opt.isEmpty()) {
                return ResetResult.notFound();
            }
            LifeProfile profile = opt.get();
            int maxLives = config.getMaxLives();
            synchronized (profile) {
                profile.setLives(maxLives);
                profile.setBannedUntil(null);
                profile.resetOnlineSeconds();
                profile.setGiftRegenActive(false);
            }

            DeadTabService deadTabService = deadTabServiceSupplier.get();
            if (deadTabService != null) {
                deadTabService.removeDead(uuid);
            }

            profileCache.saveProfileImmediately(profile);
            notificationService.updateDisplayNameAndTab(uuid, maxLives);
            notificationService.sendMessage(
                    uuid,
                    config.formatMessage(
                            "admin.reset-player-notify",
                            Map.of(
                                    "lives", String.valueOf(maxLives),
                                    "max", String.valueOf(maxLives)
                            )
                    )
            );

            return ResetResult.success(profile);
        });
    }

    /**
     * Sets a player's lives (clamped to 1..maxLives) and clears any ban.
     *
     * @param uuid   target player UUID
     * @param lives  new life count
     * @param config active configuration
     * @return CompletableFuture completing when applied
     */
    @NotNull
    public CompletableFuture<Void> setLives(@NotNull UUID uuid, int lives, @NotNull LifeConfig config) {
        int targetLives = Math.max(1, Math.min(config.getMaxLives(), lives));
        return profileCache.getOrLoadProfile(uuid, config.getMaxLives(), Instant.now()).thenAccept(profile -> {
            synchronized (profile) {
                int oldLives = profile.getLives();
                profile.setLives(targetLives);
                profile.setBannedUntil(null);
                if (targetLives > oldLives && profile.isGiftRegenActive()) {
                    profile.setGiftRegenActive(false);
                    profile.resetOnlineSeconds();
                }
            }
            DeadTabService deadTabService = deadTabServiceSupplier.get();
            if (deadTabService != null) {
                deadTabService.removeDead(uuid);
            }
            profileCache.saveProfileImmediately(profile);
            notificationService.updateDisplayNameAndTab(uuid, targetLives);
        });
    }
}
