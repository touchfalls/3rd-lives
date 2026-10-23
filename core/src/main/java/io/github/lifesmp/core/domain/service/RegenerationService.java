// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.core.domain.service;

import io.github.lifesmp.core.config.LifeConfig;
import io.github.lifesmp.core.domain.model.LifeProfile;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service orchestrating life regeneration progress, AFK detection,
 * threshold switching between normal and gift-accelerated rates,
 * and proportional timer recalculations during config reloads.
 * <p>
 * Zero-allocation position tracking is employed on player movement hot paths
 * to eliminate GC pressure.
 */
public final class RegenerationService {

    private static final class PlayerAfkTracker {
        private String world;
        private double x;
        private double y;
        private double z;
        private long lastActivityEpochMillis;

        PlayerAfkTracker(@NotNull String world, double x, double y, double z, long epochMillis) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.lastActivityEpochMillis = epochMillis;
        }

        synchronized void update(@NotNull String newWorld, double newX, double newY, double newZ, long epochMillis) {
            boolean worldChanged = !this.world.equalsIgnoreCase(newWorld);
            double dx = this.x - newX;
            double dy = this.y - newY;
            double dz = this.z - newZ;
            if (worldChanged || (dx * dx + dy * dy + dz * dz) > 0.0004) { // ~0.02 block threshold
                this.world = newWorld;
                this.x = newX;
                this.y = newY;
                this.z = newZ;
                this.lastActivityEpochMillis = epochMillis;
            }
        }

        synchronized boolean isAfk(long nowEpochMillis, long afkThresholdMillis) {
            return (nowEpochMillis - this.lastActivityEpochMillis) >= afkThresholdMillis;
        }
    }

    private final Map<UUID, PlayerAfkTracker> afkTrackers = new ConcurrentHashMap<>();

    /**
     * Updates the recorded position of a player. If the player moved, their AFK timer resets.
     * Uses in-place mutable tracking to guarantee zero object allocations on player movement.
     *
     * @param uuid  player UUID
     * @param world world name
     * @param x     X coordinate
     * @param y     Y coordinate
     * @param z     Z coordinate
     * @param now   current instant
     */
    public void updatePlayerPosition(
            @NotNull UUID uuid,
            @NotNull String world,
            double x, double y, double z,
            @NotNull Instant now
    ) {
        Objects.requireNonNull(now, "now cannot be null");
        updatePlayerPosition(uuid, world, x, y, z, now.toEpochMilli());
    }

    /**
     * Primitive epoch-millis overload avoiding {@link Instant} allocation on player movement hot path.
     *
     * @param uuid        player UUID
     * @param world       world name
     * @param x           X coordinate
     * @param y           Y coordinate
     * @param z           Z coordinate
     * @param nowEpochMillis current epoch timestamp in milliseconds
     */
    public void updatePlayerPosition(
            @NotNull UUID uuid,
            @NotNull String world,
            double x, double y, double z,
            long nowEpochMillis
    ) {
        Objects.requireNonNull(uuid, "uuid cannot be null");
        Objects.requireNonNull(world, "world cannot be null");

        PlayerAfkTracker tracker = afkTrackers.get(uuid);
        if (tracker == null) {
            afkTrackers.put(uuid, new PlayerAfkTracker(world, x, y, z, nowEpochMillis));
        } else {
            tracker.update(world, x, y, z, nowEpochMillis);
        }
    }

    /**
     * Checks if a player has been idle/AFK for more than the configured afk threshold.
     *
     * @param uuid                player UUID
     * @param now                 current instant
     * @param afkThresholdSeconds threshold in seconds before being considered AFK
     * @return true if player is AFK
     */
    public boolean isAfk(@NotNull UUID uuid, @NotNull Instant now, long afkThresholdSeconds) {
        Objects.requireNonNull(now, "now cannot be null");
        return isAfk(uuid, now.toEpochMilli(), afkThresholdSeconds);
    }

    /**
     * Primitive overload checking if a player is AFK using epoch milliseconds.
     *
     * @param uuid                player UUID
     * @param nowEpochMillis      current instant in epoch milliseconds
     * @param afkThresholdSeconds threshold in seconds before being considered AFK
     * @return true if player is AFK
     */
    public boolean isAfk(@NotNull UUID uuid, long nowEpochMillis, long afkThresholdSeconds) {
        PlayerAfkTracker tracker = afkTrackers.get(uuid);
        if (tracker == null) {
            return false;
        }
        return tracker.isAfk(nowEpochMillis, afkThresholdSeconds * 1000L);
    }

    /**
     * Removes session state for a disconnected player.
     *
     * @param uuid player UUID
     */
    public void onPlayerQuit(@NotNull UUID uuid) {
        afkTrackers.remove(uuid);
    }

    /**
     * Result of ticking regeneration for a player.
     */
    public record TickResult(
            boolean lifeGained,
            int newLives,
            long currentProgressSeconds,
            long thresholdSeconds
    ) {}

    /**
     * Advances online regeneration seconds for an active player.
     *
     * @param uuid         player UUID
     * @param profile      player profile
     * @param deltaSeconds seconds elapsed since last tick
     * @param now          current instant
     * @param config       active configuration
     * @return TickResult detailing progress or gained life
     */
    @NotNull
    public TickResult tickPlayer(
            @NotNull UUID uuid,
            @NotNull LifeProfile profile,
            long deltaSeconds,
            @NotNull Instant now,
            @NotNull LifeConfig config
    ) {
        Objects.requireNonNull(uuid, "uuid cannot be null");
        Objects.requireNonNull(profile, "profile cannot be null");
        Objects.requireNonNull(now, "now cannot be null");
        Objects.requireNonNull(config, "config cannot be null");

        // If player already has maximum lives, regeneration does not tick
        if (profile.getLives() >= config.getMaxLives()) {
            return new TickResult(false, profile.getLives(), 0L, config.getRegenSecondsPerLife());
        }

        // AFK pauses regeneration progress without affecting other mechanics
        if (isAfk(uuid, now, config.getAfkSeconds())) {
            long threshold = profile.isGiftRegenActive()
                    ? config.getGiftRegenSecondsPerLife()
                    : config.getRegenSecondsPerLife();
            return new TickResult(false, profile.getLives(), profile.getOnlineSecondsSinceLastLoss(), threshold);
        }

        long threshold = profile.isGiftRegenActive()
                    ? config.getGiftRegenSecondsPerLife()
                    : config.getRegenSecondsPerLife();

        profile.addOnlineSeconds(deltaSeconds);

        if (profile.getOnlineSecondsSinceLastLoss() >= threshold) {
            profile.resetOnlineSeconds();
            int newLives = Math.min(config.getMaxLives(), profile.getLives() + 1);
            profile.setLives(newLives);

            // Reclaiming a life turns off accelerated gift regeneration
            if (profile.isGiftRegenActive()) {
                profile.setGiftRegenActive(false);
            }

            return new TickResult(true, newLives, 0L, config.getRegenSecondsPerLife());
        }

        return new TickResult(false, profile.getLives(), profile.getOnlineSecondsSinceLastLoss(), threshold);
    }

    /**
     * Proportionally recalculates progress when switching between thresholds
     * (e.g. activating gift regen, or on /life reload).
     *
     * @param currentSeconds      accumulated seconds towards current threshold
     * @param oldThresholdSeconds previous threshold in seconds
     * @param newThresholdSeconds target threshold in seconds
     * @return proportionally adjusted accumulated seconds
     */
    public static long recalculateProgressProportionally(
            long currentSeconds,
            long oldThresholdSeconds,
            long newThresholdSeconds
    ) {
        if (oldThresholdSeconds <= 0L || newThresholdSeconds <= 0L) {
            return 0L;
        }
        double ratio = (double) currentSeconds / (double) oldThresholdSeconds;
        if (ratio > 1.0) {
            ratio = 1.0;
        }
        return Math.round(ratio * (double) newThresholdSeconds);
    }

    /**
     * Adjusts in-memory online timers proportionally when reloading configuration
     * so that players never lose earned progress.
     *
     * @param profiles  active profiles
     * @param oldConfig old configuration
     * @param newConfig newly loaded configuration
     */
    public void adjustProfilesOnConfigReload(
            @NotNull Collection<LifeProfile> profiles,
            @NotNull LifeConfig oldConfig,
            @NotNull LifeConfig newConfig
    ) {
        for (LifeProfile profile : profiles) {
            if (profile.getLives() >= newConfig.getMaxLives()) {
                continue;
            }
            long oldThreshold = profile.isGiftRegenActive()
                    ? oldConfig.getGiftRegenSecondsPerLife()
                    : oldConfig.getRegenSecondsPerLife();
            long newThreshold = profile.isGiftRegenActive()
                    ? newConfig.getGiftRegenSecondsPerLife()
                    : newConfig.getRegenSecondsPerLife();

            long adjusted = recalculateProgressProportionally(
                    profile.getOnlineSecondsSinceLastLoss(),
                    oldThreshold,
                    newThreshold
            );
            profile.setOnlineSecondsSinceLastLoss(adjusted);
        }
    }
}
