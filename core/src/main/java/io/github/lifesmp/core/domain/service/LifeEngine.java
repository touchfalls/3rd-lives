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

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

    // Set of player UUIDs pardoned while offline waiting for join notification
    private final Set<UUID> pendingPardonNotifications = ConcurrentHashMap.newKeySet();

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
            if (pendingPardonNotifications.remove(uuid)) {
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

        LifeProfile profile = profileCache.getOrLoadProfile(victimUuid, config.getMaxLives(), now).join();

        int remainingLives = profile.decrementLives();
        profile.resetOnlineSeconds();
        if (playerName != null && !playerName.isBlank()) {
            profile.setLastName(playerName);
        }
        profileCache.markDirty(victimUuid);
        pvpPolicyEngine.clearRetaliationForPlayer(victimUuid);

        if (remainingLives <= 0) {
            Instant bannedUntil = now.plusSeconds(config.getBanSeconds());
            profile.setBannedUntil(bannedUntil);
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
            profileCache.saveProfileImmediately(profile);
        });
    }

    public enum PardonStatus {
        SUCCESS,
        NOT_BANNED,
        NOT_FOUND
    }

    public record PardonResult(
            @NotNull PardonStatus status,
            @Nullable LifeProfile profile
    ) {
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

    /**
     * Pardons a player by prematurely ending their death ban.
     *
     * @param uuid player UUID
     * @return CompletableFuture completing with PardonResult
     */
    @NotNull
    public CompletableFuture<PardonResult> pardon(@NotNull UUID uuid) {
        return pardon(uuid, Instant.now());
    }

    /**
     * Pardons a player by prematurely ending their death ban at the specified instant.
     * Sets their lives to exactly 1 (red), resets bannedUntil = null, onlineSeconds = 0, and giftRegenActive = false.
     * Works for both online and offline players.
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

            profile.setLives(1);
            profile.setBannedUntil(null);
            profile.resetOnlineSeconds();
            profile.setGiftRegenActive(false);

            if (deadTabService != null) {
                deadTabService.removeDead(uuid);
            }

            pendingPardonNotifications.add(uuid);
            profileCache.markDirty(uuid);
            profileCache.saveProfileImmediately(profile);
            notificationService.updateDisplayNameAndTab(uuid, 1);

            return PardonResult.success(profile);
        });
    }

    /**
     * Pardons a player by username, resolving their UUID asynchronously (including offline players).
     *
     * @param playerName player username to pardon
     * @return CompletableFuture completing with PardonResult
     */
    @NotNull
    public CompletableFuture<PardonResult> pardonByName(@NotNull String playerName) {
        return pardonByName(playerName, Instant.now());
    }

    /**
     * Pardons a player by username at the given timestamp.
     *
     * @param playerName player username to pardon
     * @param now        current timestamp
     * @return CompletableFuture completing with PardonResult
     */
    @NotNull
    public CompletableFuture<PardonResult> pardonByName(@NotNull String playerName, @NotNull Instant now) {
        return profileCache.findUuidByLastName(playerName).thenCompose(optUuid -> {
            if (optUuid.isEmpty()) {
                return CompletableFuture.completedFuture(PardonResult.notFound());
            }
            return pardon(optUuid.get(), now);
        });
    }

    public enum ResetStatus {
        SUCCESS,
        NOT_FOUND
    }

    public record ResetResult(
            @NotNull ResetStatus status,
            @Nullable LifeProfile profile
    ) {
        public static ResetResult success(@NotNull LifeProfile profile) {
            return new ResetResult(ResetStatus.SUCCESS, profile);
        }

        public static ResetResult notFound() {
            return new ResetResult(ResetStatus.NOT_FOUND, null);
        }
    }

    /**
     * Resets a player's lives to the maximum configured lives using current active configuration.
     *
     * @param uuid player UUID
     * @return CompletableFuture completing with ResetResult
     */
    @NotNull
    public CompletableFuture<ResetResult> reset(@NotNull UUID uuid) {
        return reset(uuid, configSupplier.get(), Instant.now());
    }

    /**
     * Resets a player's lives to the maximum configured lives using the provided configuration.
     *
     * @param uuid   player UUID
     * @param config active configuration
     * @return CompletableFuture completing with ResetResult
     */
    @NotNull
    public CompletableFuture<ResetResult> reset(@NotNull UUID uuid, @NotNull LifeConfig config) {
        return reset(uuid, config, Instant.now());
    }

    /**
     * Resets a player's lives to the maximum configured lives.
     * If the player was banned, the ban is cleared.
     * Resets onlineSeconds and deactivates giftRegenActive.
     * Works for both online and offline players.
     *
     * @param uuid   player UUID
     * @param config active configuration
     * @param now    current instant
     * @return CompletableFuture completing with ResetResult
     */
    @NotNull
    public CompletableFuture<ResetResult> reset(
            @NotNull UUID uuid,
            @NotNull LifeConfig config,
            @NotNull Instant now
    ) {
        return profileCache.loadPlayerOffline(uuid).thenApply(opt -> {
            if (opt.isEmpty()) {
                return ResetResult.notFound();
            }
            LifeProfile profile = opt.get();
            int maxLives = config.getMaxLives();
            profile.setLives(maxLives);
            profile.setBannedUntil(null);
            profile.resetOnlineSeconds();
            profile.setGiftRegenActive(false);

            if (deadTabService != null) {
                deadTabService.removeDead(uuid);
            }

            profileCache.markDirty(uuid);
            profileCache.saveProfileImmediately(profile);
            notificationService.updateDisplayNameAndTab(uuid, maxLives);

            // If player is currently online, send notification message
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
     * Resets a player's lives by username, resolving their UUID asynchronously (including offline players).
     *
     * @param playerName player username to reset
     * @return CompletableFuture completing with ResetResult
     */
    @NotNull
    public CompletableFuture<ResetResult> resetByName(@NotNull String playerName) {
        return resetByName(playerName, configSupplier.get(), Instant.now());
    }

    /**
     * Resets a player's lives by username with specific configuration.
     *
     * @param playerName player username to reset
     * @param config     active configuration
     * @return CompletableFuture completing with ResetResult
     */
    @NotNull
    public CompletableFuture<ResetResult> resetByName(@NotNull String playerName, @NotNull LifeConfig config) {
        return resetByName(playerName, config, Instant.now());
    }

    /**
     * Resets a player's lives by username with specific configuration and timestamp.
     *
     * @param playerName player username to reset
     * @param config     active configuration
     * @param now        current timestamp
     * @return CompletableFuture completing with ResetResult
     */
    @NotNull
    public CompletableFuture<ResetResult> resetByName(@NotNull String playerName, @NotNull LifeConfig config, @NotNull Instant now) {
        return profileCache.findUuidByLastName(playerName).thenCompose(optUuid -> {
            if (optUuid.isEmpty()) {
                return CompletableFuture.completedFuture(ResetResult.notFound());
            }
            return reset(optUuid.get(), config, now);
        });
    }

    /**
     * Alias for {@link #reset(UUID, LifeConfig, Instant)} for backward compatibility.
     */
    @NotNull
    public CompletableFuture<ResetResult> resetPlayer(
            @NotNull UUID uuid,
            @NotNull LifeConfig config,
            @NotNull Instant now
    ) {
        return reset(uuid, config, now);
    }

    /**
     * Admin command to forcefully set a player's lives.
     *
     * @param uuid   target player UUID
     * @param lives  new life count (1..maxLives)
     * @param config active configuration
     * @return CompletableFuture completing when applied
     */
    public CompletableFuture<Void> adminSetLives(
            @NotNull UUID uuid,
            int lives,
            @NotNull LifeConfig config
    ) {
        int targetLives = Math.max(1, Math.min(config.getMaxLives(), lives));
        return profileCache.getOrLoadProfile(uuid, config.getMaxLives(), Instant.now()).thenAccept(profile -> {
            int oldLives = profile.getLives();
            profile.setLives(targetLives);
            profile.setBannedUntil(null);

            // If lives increased and gift regen was active, turn it off
            if (targetLives > oldLives && profile.isGiftRegenActive()) {
                profile.setGiftRegenActive(false);
                profile.resetOnlineSeconds();
            }

            profileCache.markDirty(uuid);
            notificationService.updateDisplayNameAndTab(uuid, targetLives);
        });
    }

    public boolean hasPendingPardonNotification(@NotNull UUID uuid) {
        return pendingPardonNotifications.contains(uuid);
    }

    public void addPendingPardonNotification(@NotNull UUID uuid) {
        pendingPardonNotifications.add(uuid);
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
