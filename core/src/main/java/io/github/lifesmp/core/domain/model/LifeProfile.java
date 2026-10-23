// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.core.domain.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Domain model representing a player's life status, ban state,
 * and online regeneration progress in the Life SMP system.
 * <p>
 * Implemented with thread-safe atomic primitives to ensure thread-safety
 * across asynchronous tick, event, and database synchronization pipelines.
 */
public final class LifeProfile {

    private final UUID uuid;
    private final AtomicInteger lives;
    private final AtomicLong onlineSecondsSinceLastLoss;
    private final AtomicReference<Instant> bannedUntil;
    private final AtomicReference<Instant> lastSeen;
    private final AtomicBoolean giftRegenActive;
    private final AtomicReference<String> lastName;

    /**
     * Constructs a new LifeProfile.
     *
     * @param uuid                       unique identifier of the player
     * @param lives                      current remaining lives (typically 0-3)
     * @param onlineSecondsSinceLastLoss online seconds accumulated towards the next regenerated life
     * @param bannedUntil                timestamp until which the player is banned, or null if not banned
     * @param lastSeen                   timestamp when the player was last seen
     * @param giftRegenActive            whether accelerated gift regeneration is active
     * @param lastName                   last known username of the player (or null)
     */
    public LifeProfile(
            @NotNull UUID uuid,
            int lives,
            long onlineSecondsSinceLastLoss,
            @Nullable Instant bannedUntil,
            @NotNull Instant lastSeen,
            boolean giftRegenActive,
            @Nullable String lastName
    ) {
        this.uuid = Objects.requireNonNull(uuid, "uuid cannot be null");
        this.lives = new AtomicInteger(lives);
        this.onlineSecondsSinceLastLoss = new AtomicLong(Math.max(0L, onlineSecondsSinceLastLoss));
        this.bannedUntil = new AtomicReference<>(bannedUntil);
        this.lastSeen = new AtomicReference<>(Objects.requireNonNull(lastSeen, "lastSeen cannot be null"));
        this.giftRegenActive = new AtomicBoolean(giftRegenActive);
        this.lastName = new AtomicReference<>(lastName);
    }

    /**
     * Backward-compatible constructor for LifeProfile without explicit last name.
     */
    public LifeProfile(
            @NotNull UUID uuid,
            int lives,
            long onlineSecondsSinceLastLoss,
            @Nullable Instant bannedUntil,
            @NotNull Instant lastSeen,
            boolean giftRegenActive
    ) {
        this(uuid, lives, onlineSecondsSinceLastLoss, bannedUntil, lastSeen, giftRegenActive, null);
    }

    /**
     * Factory method creating a brand new default profile with maximum starting lives and known username.
     *
     * @param uuid     unique identifier of the player
     * @param maxLives default number of starting lives (typically 3)
     * @param now      current instant
     * @param lastName player username (optional)
     * @return initialized LifeProfile
     */
    public static LifeProfile createDefault(@NotNull UUID uuid, int maxLives, @NotNull Instant now, @Nullable String lastName) {
        return new LifeProfile(uuid, maxLives, 0L, null, now, false, lastName);
    }

    /**
     * Factory method creating a brand new default profile with maximum starting lives.
     *
     * @param uuid     unique identifier of the player
     * @param maxLives default number of starting lives (typically 3)
     * @param now      current instant
     * @return initialized LifeProfile
     */
    public static LifeProfile createDefault(@NotNull UUID uuid, int maxLives, @NotNull Instant now) {
        return createDefault(uuid, maxLives, now, null);
    }

    @NotNull
    public UUID getUuid() {
        return uuid;
    }

    public int getLives() {
        return lives.get();
    }

    public void setLives(int newLives) {
        this.lives.set(newLives);
    }

    public int decrementLives() {
        return lives.decrementAndGet();
    }

    public int incrementLives() {
        return lives.incrementAndGet();
    }

    public long getOnlineSecondsSinceLastLoss() {
        return onlineSecondsSinceLastLoss.get();
    }

    public void setOnlineSecondsSinceLastLoss(long seconds) {
        this.onlineSecondsSinceLastLoss.set(Math.max(0L, seconds));
    }

    public long addOnlineSeconds(long seconds) {
        return this.onlineSecondsSinceLastLoss.addAndGet(Math.max(0L, seconds));
    }

    public void resetOnlineSeconds() {
        this.onlineSecondsSinceLastLoss.set(0L);
    }

    @Nullable
    public Instant getBannedUntil() {
        return bannedUntil.get();
    }

    public void setBannedUntil(@Nullable Instant bannedUntil) {
        this.bannedUntil.set(bannedUntil);
    }

    @NotNull
    public Instant getLastSeen() {
        return lastSeen.get();
    }

    public void setLastSeen(@NotNull Instant lastSeen) {
        this.lastSeen.set(Objects.requireNonNull(lastSeen, "lastSeen cannot be null"));
    }

    public boolean isGiftRegenActive() {
        return giftRegenActive.get();
    }

    public void setGiftRegenActive(boolean active) {
        this.giftRegenActive.set(active);
    }

    @Nullable
    public String getLastName() {
        return lastName.get();
    }

    public void setLastName(@Nullable String name) {
        this.lastName.set(name);
    }

    /**
     * Checks if the player is currently under an active death ban.
     *
     * @param now current instant
     * @return true if player is banned and ban timestamp has not expired
     */
    public boolean isBanned(@NotNull Instant now) {
        Instant ban = bannedUntil.get();
        return ban != null && now.isBefore(ban);
    }

    /**
     * Calculates remaining duration of the death ban, or Duration.ZERO if not banned.
     *
     * @param now current instant
     * @return remaining ban duration
     */
    @NotNull
    public Duration getRemainingBanDuration(@NotNull Instant now) {
        Instant ban = bannedUntil.get();
        if (ban == null || !now.isBefore(ban)) {
            return Duration.ZERO;
        }
        return Duration.between(now, ban);
    }

    /**
     * Checks whether the player is currently on their last life ("red" player).
     *
     * @return true if player has exactly 1 life
     */
    public boolean isRed() {
        return lives.get() == 1;
    }

    /**
     * Checks whether the player has 0 lives (dead/banned).
     *
     * @return true if lives <= 0
     */
    public boolean isDead() {
        return lives.get() <= 0;
    }

    /**
     * Creates an independent immutable snapshot copy of this profile.
     *
     * @return new LifeProfile snapshot
     */
    public LifeProfile snapshot() {
        return new LifeProfile(
                uuid,
                lives.get(),
                onlineSecondsSinceLastLoss.get(),
                bannedUntil.get(),
                lastSeen.get(),
                giftRegenActive.get(),
                lastName.get()
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LifeProfile that = (LifeProfile) o;
        return uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }

    @Override
    public String toString() {
        return "LifeProfile{" +
                "uuid=" + uuid +
                ", lives=" + lives.get() +
                ", onlineSeconds=" + onlineSecondsSinceLastLoss.get() +
                ", bannedUntil=" + bannedUntil.get() +
                ", lastSeen=" + lastSeen.get() +
                ", giftRegenActive=" + giftRegenActive.get() +
                ", lastName='" + lastName.get() + '\'' +
                '}';
    }
}
