// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.core.domain.service;

import io.github.lifesmp.core.config.LifeConfig;
import io.github.lifesmp.core.domain.model.LifeProfile;
import io.github.lifesmp.core.ports.NotificationService;
import io.github.lifesmp.core.storage.LifeProfileCache;
import io.github.lifesmp.core.util.FormatUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Central domain engine managing life transitions, join/quit lifecycle,
 * death processing with tick-level idempotency, admin operations (pardon, reset),
 * and ban enforcement.
 */
public final class LifeEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(LifeEngine.class);

    private final LifeProfileCache profileCache;
    private final ArenaService arenaService;
    private final PvPPolicyEngine pvpPolicyEngine;
    private final RegenerationService regenerationService;
    private final GiftService giftService;
    private final NotificationService notificationService;
    private volatile Supplier<LifeConfig> configSupplier;
    private volatile DeadTabService deadTabService;

    // Idempotency: debouncing multiple death events firing in the same tick / window
    private final Map<UUID, Instant> lastDeathTimestamps = new ConcurrentHashMap<>();

    private final AdminService adminService;

    public LifeEngine(
            @NotNull LifeProfileCache profileCache,
            @NotNull ArenaService arenaService,
            @NotNull PvPPolicyEngine pvpPolicyEngine,
            @NotNull RegenerationService regenerationService,
            @NotNull GiftService giftService,
            @NotNull NotificationService notificationService
    ) {
        this(
                profileCache,
                arenaService,
                pvpPolicyEngine,
                regenerationService,
                giftService,
                notificationService,
                LifeConfig::createDefault,
                null
        );
    }

    public LifeEngine(
            @NotNull LifeProfileCache profileCache,
            @NotNull ArenaService arenaService,
            @NotNull PvPPolicyEngine pvpPolicyEngine,
            @NotNull RegenerationService regenerationService,
            @NotNull GiftService giftService,
            @NotNull NotificationService notificationService,
            @NotNull Supplier<LifeConfig> configSupplier
    ) {
        this(
                profileCache,
                arenaService,
                pvpPolicyEngine,
                regenerationService,
                giftService,
                notificationService,
                configSupplier,
                null
        );
    }

    public LifeEngine(
            @NotNull LifeProfileCache profileCache,
            @NotNull ArenaService arenaService,
            @NotNull PvPPolicyEngine pvpPolicyEngine,
            @NotNull RegenerationService regenerationService,
            @NotNull GiftService giftService,
            @NotNull NotificationService notificationService,
            @NotNull Supplier<LifeConfig> configSupplier,
            @Nullable DeadTabService deadTabService
    ) {
        this.profileCache = Objects.requireNonNull(profileCache, "profileCache cannot be null");
        this.arenaService = Objects.requireNonNull(arenaService, "arenaService cannot be null");
        this.pvpPolicyEngine = Objects.requireNonNull(pvpPolicyEngine, "pvpPolicyEngine cannot be null");
        this.regenerationService = Objects.requireNonNull(regenerationService, "regenerationService cannot be null");
        this.giftService = Objects.requireNonNull(giftService, "giftService cannot be null");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService cannot be null");
        this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier cannot be null");
        this.deadTabService = deadTabService;
        this.adminService = new AdminService(profileCache, notificationService, () -> this.deadTabService);
    }

    public void setConfigSupplier(@NotNull Supplier<LifeConfig> configSupplier) {
        this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier cannot be null");
    }

    public void setDeadTabService(@Nullable DeadTabService deadTabService) {
        this.deadTabService = deadTabService;
    }

    @Nullable
    public DeadTabService getDeadTabService() {
        return deadTabService;
    }

    @NotNull
    public AdminService getAdminService() {
        return adminService;
    }

    public record JoinEvaluation(
            boolean allowed,
            @NotNull LifeProfile profile,
            @Nullable String kickReason
    ) {
        public static JoinEvaluation allow(@NotNull LifeProfile profile) {
            return new JoinEvaluation(true, profile, null);
        }

        public static JoinEvaluation ban(@NotNull LifeProfile profile, @NotNull String kickReason) {
            return new JoinEvaluation(false, profile, kickReason);
        }
    }

    /**
     * Evaluates a player connection attempt, loading or initializing their profile,
     * checking death bans, and restoring lives if the natural ban period has elapsed.
     *
     * @param uuid   player UUID
     * @param now    current instant
     * @param config active LifeConfig
     * @return CompletableFuture completing with JoinEvaluation
     */
    public CompletableFuture<JoinEvaluation> handlePlayerJoin(
            @NotNull UUID uuid,
            @NotNull Instant now,
            @NotNull LifeConfig config
    ) {
        return handlePlayerJoin(uuid, null, now, config);
    }

    /**
     * Evaluates a player connection attempt with known player username,
     * updating last_name in profile and persistent storage even if currently banned.
     *
     * @param uuid       player UUID
     * @param playerName player username (optional)
     * @param now        current instant
     * @param config     active LifeConfig
     * @return CompletableFuture completing with JoinEvaluation
     */
    public CompletableFuture<JoinEvaluation> handlePlayerJoin(
            @NotNull UUID uuid,
            @Nullable String playerName,
            @NotNull Instant now,
            @NotNull LifeConfig config
    ) {
        return profileCache.getOrLoadProfile(uuid, config.getMaxLives(), now).thenApply(profile -> {
            profile.setLastSeen(now);
            if (playerName != null && !playerName.isBlank()) {
                profile.setLastName(playerName);
                profileCache.markDirty(uuid);
            }

            if (profile.isBanned(now)) {
                if (playerName != null && !playerName.isBlank()) {
                    profileCache.saveProfileImmediately(profile);
                }
                Duration remaining = profile.getRemainingBanDuration(now);
                String remainingFormatted = config.formatDurationHoursMinutes(remaining);
                String banMessage = config.formatMessage(
                        "death.ban-join",
                        Map.of(
                                "remaining", remainingFormatted,
                                "time", remainingFormatted
                        )
                );
                return JoinEvaluation.ban(profile, banMessage);
            }

            // IMPORTANT (edge case resolution):
            // When a banned player's ban period naturally expires, their lives == 0 and banned_until != null.
            // In that case: lives = restored-lives-after-ban (default 3), online_seconds = 0, gift_regen_active = false.
            // However, when a player is pardoned via /life pardon, their lives are set to 1 and banned_until is set to null.
            // Therefore, when a pardoned player connects (even after the original ban duration would have elapsed),
            // this restoration block will NOT execute because profile.getLives() == 1 and profile.getBannedUntil() == null.
            // This guarantees that pardoned players strictly remain on 1 life.
            if (profile.getLives() <= 0 && profile.getBannedUntil() != null) {
                profile.setBannedUntil(null);
                profile.setLives(config.getRestoredLivesAfterBan());
                profile.resetOnlineSeconds();
                profile.setGiftRegenActive(false);
                profileCache.markDirty(uuid);
                if (deadTabService != null) {
                    deadTabService.removeDead(uuid);
                }
            }

            // If player was pardoned by an admin while offline, notify them upon login
            if (profile.isPardonNoticePending()) {
                profile.setPardonNoticePending(false);
                profileCache.markDirty(uuid);
                notificationService.sendMessage(uuid, config.formatMessage("admin.pardon-join-notify"));
            }

            notificationService.updateDisplayNameAndTab(uuid, profile.getLives());

            if (deadTabService != null) {
                deadTabService.onPlayerJoin(uuid);
            }

            return JoinEvaluation.allow(profile);
        });
    }

    public record DeathResult(
            boolean lifeLost,
            boolean isBanned,
            int remainingLives,
            boolean insideArena
    ) {}

    /**
     * Processes a player death event with idempotency guarantees.
     *
     * @param victimUuid player UUID who died
     * @param world      world name where death occurred
     * @param x          X coordinate
     * @param y          Y coordinate
     * @param z          Z coordinate
     * @param killerUuid optional killer UUID
     * @param now        current instant
     * @param config     active configuration
     * @return DeathResult detailing whether a life was lost or player was banned
     */
    @NotNull
    public DeathResult handlePlayerDeath(
            @NotNull UUID victimUuid,
            @NotNull String world,
            double x, double y, double z,
            @Nullable UUID killerUuid,
            @NotNull Instant now,
            @NotNull LifeConfig config
    ) {
        return handlePlayerDeath(victimUuid, null, world, x, y, z, killerUuid, now, config);
    }

    /**
     * Processes a player death event with idempotency guarantees and known player name.
     *
     * @param victimUuid player UUID who died
     * @param playerName player username (optional)
     * @param world      world name where death occurred
     * @param x          X coordinate
     * @param y          Y coordinate
     * @param z          Z coordinate
     * @param killerUuid optional killer UUID
     * @param now        current instant
     * @param config     active configuration
     * @return DeathResult detailing whether a life was lost or player was banned
     */
    @NotNull
    public DeathResult handlePlayerDeath(
            @NotNull UUID victimUuid,
            @Nullable String playerName,
            @NotNull String world,
            double x, double y, double z,
            @Nullable UUID killerUuid,
            @NotNull Instant now,
            @NotNull LifeConfig config
    ) {
        // Idempotency: ignore duplicate deaths occurring within 100 milliseconds
        Instant lastDeath = lastDeathTimestamps.get(victimUuid);
        if (lastDeath != null && Duration.between(lastDeath, now).toMillis() < 100L) {
            Optional<LifeProfile> existing = profileCache.getCachedProfile(victimUuid);
            int currentLives = existing.map(LifeProfile::getLives).orElse(0);
            return new DeathResult(false, currentLives <= 0, currentLives, false);
        }
        lastDeathTimestamps.put(victimUuid, now);

        if (arenaService.isInsideActiveArena(world, x, y, z)) {
            Optional<LifeProfile> cached = profileCache.getCachedProfile(victimUuid);
            int currentLives = cached.map(LifeProfile::getLives).orElse(1);
            notificationService.sendMessage(victimUuid, config.formatMessage("death.arena-death"));
            return new DeathResult(false, false, currentLives, true);
        }

        // Online players are cached on join. The cache is empty after a plugin reload or a quick rejoin,
        // and skipping the death would be a free life, so load the profile as a rare fallback.
        LifeProfile profile = profileCache.getCachedProfile(victimUuid).orElseGet(() -> {
            LOGGER.warn("Profile of {} was not in memory at death; loading it from the database", victimUuid);
            return profileCache.getOrLoadProfile(victimUuid, config.getMaxLives(), now).join();
        });

        int remainingLives;
        Instant bannedUntil = null;
        // Lock so an autosave snapshot never sees the lost life without the ban.
        synchronized (profile) {
            remainingLives = profile.decrementLives();
            profile.resetOnlineSeconds();
            if (playerName != null && !playerName.isBlank()) {
                profile.setLastName(playerName);
            }
            if (remainingLives <= 0) {
                bannedUntil = now.plusSeconds(config.getBanSeconds());
                profile.setBannedUntil(bannedUntil);
            }
        }
        profileCache.markDirty(victimUuid);
        pvpPolicyEngine.clearRetaliationForPlayer(victimUuid);

        if (remainingLives <= 0) {
            profileCache.saveProfileImmediately(profile);

            if (deadTabService != null) {
                deadTabService.recordDeathBan(victimUuid, profile.getLastName(), bannedUntil);
            }

            Duration remaining = Duration.ofSeconds(config.getBanSeconds());
            String formattedTime = config.formatDuration(remaining);
            String kickMsg = config.formatMessage(
                    "death.ban-kick",
                    Map.of(
                            "remaining", formattedTime,
                            "time", formattedTime
                    )
            );
            notificationService.kickPlayer(victimUuid, kickMsg);
            return new DeathResult(true, true, remainingLives, false);
        }

        profileCache.saveProfileImmediately(profile);
        notificationService.updateDisplayNameAndTab(victimUuid, remainingLives);

        String colorHex = config.getColorHex(remainingLives);
        String hearts = FormatUtils.formatHearts(remainingLives, config.getMaxLives(), colorHex);
        notificationService.sendMessage(
                victimUuid,
                config.formatMessage(
                        "death.life-lost",
                        Map.of(
                                "lives", String.valueOf(remainingLives),
                                "max", String.valueOf(config.getMaxLives()),
                                "hearts", hearts,
                                "color", colorHex
                        )
                )
        );

        // Last life warning if exactly 1 life remains
        if (remainingLives == 1) {
            Duration banDuration = Duration.ofSeconds(config.getBanSeconds());
            notificationService.sendMessage(
                    victimUuid,
                    config.formatMessage(
                            "death.last-life-warning",
                            "time",
                            config.formatDuration(banDuration)
                    )
            );
        }

        return new DeathResult(true, false, remainingLives, false);
    }

    /**
     * Handles player disconnection, cleaning up transient state to prevent memory leaks.
     * Note: disconnecting during combat incurs no death penalties.
     *
     * @param uuid player UUID
     * @param now  current instant
     */
    public void handlePlayerQuit(@NotNull UUID uuid, @NotNull Instant now) {
        giftService.onPlayerQuit(uuid);
        regenerationService.onPlayerQuit(uuid);
        pvpPolicyEngine.clearRetaliationForPlayer(uuid);
        lastDeathTimestamps.remove(uuid);

        profileCache.getCachedProfile(uuid).ifPresent(profile -> {
            profile.setLastSeen(now);
            // A quick rejoin marks the profile dirty again, so evictIfClean keeps it in that case.
            profileCache.saveProfileImmediately(profile).thenRun(() -> profileCache.evictIfClean(uuid));
        });
    }

    /**
     * Advances one online player by one second of regeneration. The position feeds AFK detection,
     * so a player who never moves after joining still becomes AFK.
     */
    public void tickOnlinePlayer(
            @NotNull UUID uuid,
            @NotNull String world,
            double x, double y, double z,
            @NotNull Instant now,
            @NotNull LifeConfig config
    ) {
        regenerationService.updatePlayerPosition(uuid, world, x, y, z, now);
        profileCache.getCachedProfile(uuid).ifPresent(profile -> {
            RegenerationService.TickResult result = regenerationService.tickPlayer(uuid, profile, 1L, now, config);
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

    /**
     * Server-wide part of the one-second tick, run after all online players were ticked.
     */
    public void tickServer(@NotNull Instant now) {
        if (deadTabService != null) {
            deadTabService.tick(now);
        }
        pruneExpired(now);
    }

    /**
     * Periodic pruning routine for expired retaliation records, pending gift requests,
     * and old death timestamps to guarantee zero memory leaks.
     *
     * @param now current instant
     */
    public void pruneExpired(@NotNull Instant now) {
        pvpPolicyEngine.pruneExpired(now);
        giftService.pruneExpired(now);
        lastDeathTimestamps.entrySet().removeIf(entry ->
                Duration.between(entry.getValue(), now).toMillis() > 1000L);
    }

    @NotNull
    public LifeProfileCache getProfileCache() {
        return profileCache;
    }

    @NotNull
    public ArenaService getArenaService() {
        return arenaService;
    }

    @NotNull
    public PvPPolicyEngine getPvpPolicyEngine() {
        return pvpPolicyEngine;
    }

    @NotNull
    public RegenerationService getRegenerationService() {
        return regenerationService;
    }

    @NotNull
    public GiftService getGiftService() {
        return giftService;
    }

    @NotNull
    public NotificationService getNotificationService() {
        return notificationService;
    }
}
