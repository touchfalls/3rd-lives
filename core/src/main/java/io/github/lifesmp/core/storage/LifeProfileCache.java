// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.core.storage;

import io.github.lifesmp.core.domain.model.LifeProfile;
import io.github.lifesmp.core.ports.PlayerRepository;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory state cache for {@link LifeProfile} records.
 * <p>
 * Ensures zero I/O on the main server thread, maintains atomic dirty versioning for periodic
 * autosave flushing, supports immediate saves on quit/ban, graceful shutdown flushing,
 * and resilient retries without memory loss during database outages.
 */
public final class LifeProfileCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(LifeProfileCache.class);

    private final PlayerRepository repository;
    private final Map<UUID, LifeProfile> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> dirtyVersions = new ConcurrentHashMap<>();
    private final AtomicLong changeSequence = new AtomicLong();

    public LifeProfileCache(@NotNull PlayerRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
    }

    /**
     * Gets a cached profile or loads it asynchronously from repository.
     * If player joins for the first time, initializes a default profile with maxLives.
     *
     * @param uuid     player UUID
     * @param maxLives default lives for first join
     * @param now      current instant
     * @return CompletableFuture completing with the profile
     */
    @NotNull
    public CompletableFuture<LifeProfile> getOrLoadProfile(
            @NotNull UUID uuid,
            int maxLives,
            @NotNull Instant now
    ) {
        LifeProfile existing = cache.get(uuid);
        if (existing != null) {
            return CompletableFuture.completedFuture(existing);
        }

        return repository.loadPlayer(uuid).thenApply(opt -> {
            LifeProfile profile = opt.orElseGet(() -> {
                LifeProfile fresh = LifeProfile.createDefault(uuid, maxLives, now);
                markDirty(uuid);
                return fresh;
            });
            cache.put(uuid, profile);
            return profile;
        });
    }

    /**
     * Retrieves an already cached profile if present.
     *
     * @param uuid player UUID
     * @return optional cached profile
     */
    @NotNull
    public Optional<LifeProfile> getCachedProfile(@NotNull UUID uuid) {
        return Optional.ofNullable(cache.get(uuid));
    }

    /**
     * Finds a player's UUID by username, consulting memory cache first, then repository.
     *
     * @param name player username
     * @return CompletableFuture completing with optional UUID
     */
    @NotNull
    public CompletableFuture<Optional<UUID>> findUuidByLastName(@NotNull String name) {
        for (LifeProfile profile : cache.values()) {
            if (name.equalsIgnoreCase(profile.getLastName())) {
                return CompletableFuture.completedFuture(Optional.of(profile.getUuid()));
            }
        }
        return repository.findUuidByLastName(name);
    }

    /**
     * Marks a profile as modified, incrementing its change version and queuing it for autosave.
     *
     * @param uuid player UUID
     */
    public void markDirty(@NotNull UUID uuid) {
        dirtyVersions.put(uuid, changeSequence.incrementAndGet());
    }

    /**
     * Persists a profile immediately (used on player quit or death ban).
     *
     * @param profile profile to persist
     * @return CompletableFuture completing when write finishes
     */
    public CompletableFuture<Void> saveProfileImmediately(@NotNull LifeProfile profile) {
        UUID uuid = profile.getUuid();
        long currentVersion = dirtyVersions.getOrDefault(uuid, 0L);

        return repository.savePlayer(profile)
                .thenRun(() -> {
                    if (currentVersion > 0L) {
                        dirtyVersions.remove(uuid, currentVersion);
                    } else {
                        dirtyVersions.remove(uuid);
                    }
                })
                .exceptionally(ex -> {
                    LOGGER.error("Immediate save failed for {}. State remains in memory and will be retried.",
                            uuid, ex);
                    markDirty(uuid);
                    return null;
                });
    }

    /**
     * Persists several profiles in one database transaction, for changes that must be stored together
     * (for example a life moving from one player to another).
     *
     * @param profiles profiles to persist
     * @return CompletableFuture completing when the write finishes
     */
    public CompletableFuture<Void> saveProfilesImmediately(@NotNull Collection<LifeProfile> profiles) {
        Map<UUID, Long> versions = new HashMap<>();
        List<LifeProfile> snapshots = new ArrayList<>(profiles.size());
        for (LifeProfile profile : profiles) {
            markDirty(profile.getUuid());
            versions.put(profile.getUuid(), dirtyVersions.get(profile.getUuid()));
            snapshots.add(profile.snapshot());
        }
        return repository.saveAll(snapshots)
                .thenRun(() -> versions.forEach(dirtyVersions::remove))
                .exceptionally(ex -> {
                    LOGGER.error("Immediate batch save failed for {} profiles. They stay dirty and will be retried.",
                            snapshots.size(), ex);
                    return null;
                });
    }

    /**
     * Flushes all currently dirty profiles to the database via batch write.
     * Resilient against DB failures: retains dirty entries in memory on failure.
     * Uses atomic version checking to ensure concurrent modifications during save are never lost.
     *
     * @return CompletableFuture completing when flush attempt finishes
     */
    public CompletableFuture<Void> flushDirtyProfiles() {
        if (dirtyVersions.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        Map<UUID, Long> batch = new HashMap<>(dirtyVersions);
        List<LifeProfile> toSave = new ArrayList<>(batch.size());

        for (UUID uuid : batch.keySet()) {
            LifeProfile p = cache.get(uuid);
            if (p != null) {
                toSave.add(p.snapshot());
            }
        }

        if (toSave.isEmpty()) {
            for (Map.Entry<UUID, Long> entry : batch.entrySet()) {
                dirtyVersions.remove(entry.getKey(), entry.getValue());
            }
            return CompletableFuture.completedFuture(null);
        }

        return repository.saveAll(toSave)
                .thenRun(() -> {
                    for (Map.Entry<UUID, Long> entry : batch.entrySet()) {
                        dirtyVersions.remove(entry.getKey(), entry.getValue());
                    }
                    LOGGER.debug("Successfully autosaved {} dirty profiles to database", toSave.size());
                })
                .exceptionally(ex -> {
                    LOGGER.error("Failed to flush dirty profiles batch (size {}). Retaining in memory for retry.",
                            toSave.size(), ex);
                    return null;
                });
    }

    /**
     * Executes a graceful shutdown: synchronously waits for all pending and dirty writes to persist.
     *
     * @param timeoutSeconds maximum wait duration in seconds
     */
    public void gracefulShutdown(long timeoutSeconds) {
        LOGGER.info("Starting graceful shutdown of LifeProfileCache (flushing {} dirty profiles)...", dirtyVersions.size());
        try {
            flushDirtyProfiles().get(timeoutSeconds, TimeUnit.SECONDS);
            LOGGER.info("Graceful shutdown flush completed successfully.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Graceful shutdown interrupted.");
        } catch (ExecutionException | TimeoutException e) {
            LOGGER.error("Graceful shutdown timed out or encountered an error while saving profiles", e);
        }
    }

    @NotNull
    public Collection<LifeProfile> getAllCachedProfiles() {
        return Collections.unmodifiableCollection(cache.values());
    }

    public void evictIfClean(@NotNull UUID uuid) {
        if (!dirtyVersions.containsKey(uuid)) {
            cache.remove(uuid);
        }
    }

    @NotNull
    public PlayerRepository getRepository() {
        return repository;
    }

    @NotNull
    public CompletableFuture<Optional<LifeProfile>> loadPlayerOffline(@NotNull UUID uuid) {
        LifeProfile cached = cache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(Optional.of(cached));
        }
        return repository.loadPlayer(uuid);
    }
}
