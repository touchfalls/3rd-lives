// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) <FILL IN: year> <FILL IN: author>

package io.github.lifesmp.core.domain.service;

import io.github.lifesmp.core.domain.model.Arena;
import io.github.lifesmp.core.ports.ArenaRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service managing arena definitions, active spatial lookups,
 * and global event PvP overrides.
 */
public final class ArenaService {

    private final ArenaRepository arenaRepository;
    private final Map<String, Arena> arenas = new ConcurrentHashMap<>();

    private final AtomicBoolean globalPvpOverride = new AtomicBoolean(false);
    private final AtomicReference<Instant> globalPvpOverrideExpiresAt = new AtomicReference<>(null);

    public ArenaService(@NotNull ArenaRepository arenaRepository) {
        this.arenaRepository = Objects.requireNonNull(arenaRepository, "arenaRepository cannot be null");
    }

    /**
     * Loads all arenas from repository into cache.
     *
     * @return CompletableFuture completing when loaded
     */
    public CompletableFuture<Void> loadArenas() {
        return arenaRepository.loadAllArenas().thenAccept(list -> {
            arenas.clear();
            for (Arena arena : list) {
                arenas.put(arena.getName().toLowerCase(Locale.ROOT), arena);
            }
        });
    }

    /**
     * Creates and persists a new arena.
     *
     * @param name  unique name
     * @param world world identifier
     * @param x1    corner 1 X
     * @param y1    corner 1 Y
     * @param z1    corner 1 Z
     * @param x2    corner 2 X
     * @param y2    corner 2 Y
     * @param z2    corner 2 Z
     * @return CompletableFuture completing with created arena
     */
    public CompletableFuture<Arena> createArena(
            @NotNull String name,
            @NotNull String world,
            double x1, double y1, double z1,
            double x2, double y2, double z2
    ) {
        Arena arena = new Arena(name, world, x1, y1, z1, x2, y2, z2, true);
        arenas.put(name.toLowerCase(Locale.ROOT), arena);
        return arenaRepository.saveArena(arena).thenApply(v -> arena);
    }

    /**
     * Removes an arena by name.
     *
     * @param name unique arena name
     * @return CompletableFuture completing with true if deleted, false if not found
     */
    public CompletableFuture<Boolean> removeArena(@NotNull String name) {
        Arena removed = arenas.remove(name.toLowerCase(Locale.ROOT));
        if (removed == null) {
            return CompletableFuture.completedFuture(false);
        }
        return arenaRepository.deleteArena(name).thenApply(v -> true);
    }

    /**
     * Enables or disables an arena.
     *
     * @param name    arena name
     * @param enabled enabled state
     * @return CompletableFuture completing with true if updated, false if not found
     */
    public CompletableFuture<Boolean> setArenaEnabled(@NotNull String name, boolean enabled) {
        Arena arena = arenas.get(name.toLowerCase(Locale.ROOT));
        if (arena == null) {
            return CompletableFuture.completedFuture(false);
        }
        arena.setEnabled(enabled);
        return arenaRepository.saveArena(arena).thenApply(v -> true);
    }

    @Nullable
    public Arena getArena(@NotNull String name) {
        return arenas.get(name.toLowerCase(Locale.ROOT));
    }

    @NotNull
    public Collection<Arena> getAllArenas() {
        return Collections.unmodifiableCollection(arenas.values());
    }

    /**
     * Finds an enabled arena containing the given spatial coordinates.
     *
     * @param world world name
     * @param x     X coordinate
     * @param y     Y coordinate
     * @param z     Z coordinate
     * @return optional containing active arena if matched
     */
    @NotNull
    public Optional<Arena> findActiveArenaContaining(@NotNull String world, double x, double y, double z) {
        for (Arena arena : arenas.values()) {
            if (arena.isEnabled() && arena.contains(world, x, y, z)) {
                return Optional.of(arena);
            }
        }
        return Optional.empty();
    }

    /**
     * Checks if coordinates fall within an enabled arena.
     *
     * @param world world name
     * @param x     X coordinate
     * @param y     Y coordinate
     * @param z     Z coordinate
     * @return true if inside an active enabled arena
     */
    public boolean isInsideActiveArena(@NotNull String world, double x, double y, double z) {
        return findActiveArenaContaining(world, x, y, z).isPresent();
    }

    /**
     * Activates or deactivates the server-wide PvP override event.
     *
     * @param enabled   whether enabled
     * @param expiresAt optional expiration instant
     */
    public void setGlobalPvpOverride(boolean enabled, @Nullable Instant expiresAt) {
        globalPvpOverride.set(enabled);
        globalPvpOverrideExpiresAt.set(expiresAt);
    }

    /**
     * Checks if server-wide PvP override event is active.
     *
     * @param now current instant
     * @return true if active and not expired
     */
    public boolean isGlobalPvpOverrideActive(@NotNull Instant now) {
        if (!globalPvpOverride.get()) {
            return false;
        }
        Instant expiresAt = globalPvpOverrideExpiresAt.get();
        if (expiresAt != null && now.isAfter(expiresAt)) {
            globalPvpOverride.set(false);
            globalPvpOverrideExpiresAt.set(null);
            return false;
        }
        return true;
    }
}
