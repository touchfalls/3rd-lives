// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.core.domain.service;

import io.github.lifesmp.core.config.LifeConfig;
import io.github.lifesmp.core.domain.model.DeadEntry;
import io.github.lifesmp.core.ports.PlayerRepository;
import io.github.lifesmp.core.ports.TabPresenter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Domain service managing fake dead player tab-list entries.
 * <p>
 * Maintains an in-memory cache of currently banned players, handles initial async load from SQLite,
 * enforces max-entries and name validity rules, prunes expired bans in the common per-second tick routine,
 * and delegates packet delivery to the platform {@link TabPresenter} port.
 */
public final class DeadTabService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeadTabService.class);

    private final TabPresenter tabPresenter;
    private final PlayerRepository playerRepository;
    private final Supplier<LifeConfig> configSupplier;

    private final Map<UUID, DeadEntry> deadEntries = new ConcurrentHashMap<>();
    private long lastTimerRefreshEpochSec = 0L;

    public DeadTabService(
            @NotNull TabPresenter tabPresenter,
            @NotNull PlayerRepository playerRepository,
            @NotNull Supplier<LifeConfig> configSupplier
    ) {
        this.tabPresenter = Objects.requireNonNull(tabPresenter, "tabPresenter cannot be null");
        this.playerRepository = Objects.requireNonNull(playerRepository, "playerRepository cannot be null");
        this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier cannot be null");
    }

    /**
     * Asynchronously loads currently active banned players from persistent storage upon server startup.
     *
     * @param nowEpochSecond current timestamp in epoch seconds
     * @return CompletableFuture completing when initial load and presentation finishes
     */
    @NotNull
    public CompletableFuture<Void> loadInitialDeadEntries(long nowEpochSecond) {
        LifeConfig config = configSupplier.get();
        if (!config.isDeadTabEnabled()) {
            LOGGER.debug("Dead TAB feature is disabled in config; skipping startup load");
            return CompletableFuture.completedFuture(null);
        }

        return playerRepository.loadActiveDeadEntries(nowEpochSecond).thenAccept(entries -> {
            deadEntries.clear();
            int maxEntries = config.getDeadTabMaxEntries();

            // Sort by latest ban expiry descending and limit to maxEntries
            List<DeadEntry> prioritized = entries.stream()
                    .sorted((a, b) -> b.bannedUntil().compareTo(a.bannedUntil()))
                    .limit(maxEntries)
                    .toList();

            for (DeadEntry entry : prioritized) {
                deadEntries.put(entry.uuid(), entry);
            }

            // refreshAll only renames entries clients already have; online players need the entries added
            // (this runs at startup and again when dead-tab is switched on by /life reload).
            for (DeadEntry entry : deadEntries.values()) {
                tabPresenter.showDead(entry);
            }
            LOGGER.info("Loaded {} active dead TAB entries into cache from database", deadEntries.size());
        });
    }

    /**
     * Records a death ban for a player, adding them to the TAB list if eligible.
     *
     * @param uuid        player UUID
     * @param name        player last known name
     * @param bannedUntil ban expiration timestamp
     */
    public void recordDeathBan(@NotNull UUID uuid, @Nullable String name, @NotNull Instant bannedUntil) {
        Objects.requireNonNull(uuid, "uuid cannot be null");
        Objects.requireNonNull(bannedUntil, "bannedUntil cannot be null");

        LifeConfig config = configSupplier.get();
        if (!config.isDeadTabEnabled()) {
            return;
        }

        // Edge case: Validate name
        if (name == null || name.isBlank()) {
            LOGGER.debug("Skipping dead TAB entry for {}: last_name is null or empty", uuid);
            return;
        }
        if (name.length() > 16) {
            LOGGER.debug("Skipping dead TAB entry for {}: name '{}' exceeds 16 characters", uuid, name);
            return;
        }

        // Edge case: Prevent duplicate usernames
        for (DeadEntry existing : deadEntries.values()) {
            if (existing.name().equalsIgnoreCase(name) && !existing.uuid().equals(uuid)) {
                LOGGER.debug("Skipping dead TAB entry for {} because name '{}' is already displayed for {}", uuid, name, existing.uuid());
                return;
            }
        }

        // Edge case: Max entries capacity check
        int max = config.getDeadTabMaxEntries();
        if (deadEntries.size() >= max && !deadEntries.containsKey(uuid)) {
            Map.Entry<UUID, DeadEntry> earliest = null;
            for (Map.Entry<UUID, DeadEntry> e : deadEntries.entrySet()) {
                if (earliest == null || e.getValue().bannedUntil().isBefore(earliest.getValue().bannedUntil())) {
                    earliest = e;
                }
            }

            if (earliest != null) {
                if (bannedUntil.isBefore(earliest.getValue().bannedUntil())) {
                    LOGGER.debug("Max dead TAB capacity ({}) reached; discarding entry {} with earlier expiration", max, uuid);
                    return;
                }
                deadEntries.remove(earliest.getKey());
                tabPresenter.hideDead(earliest.getKey());
            }
        }

        DeadEntry entry = new DeadEntry(uuid, name, bannedUntil);
        deadEntries.put(uuid, entry);
        tabPresenter.showDead(entry);
    }

    /**
     * Removes a player from the dead TAB list (e.g. on /life pardon, /life reset, or ban expiration).
     * Idempotent: does nothing when the player has no dead entry, so no packet is sent for the UUID
     * of a living player (clients drop the player's TAB entry on a remove packet and then stop showing them).
     *
     * @param uuid player UUID
     */
    public void removeDead(@NotNull UUID uuid) {
        Objects.requireNonNull(uuid, "uuid cannot be null");
        if (deadEntries.remove(uuid) != null) {
            tabPresenter.hideDead(uuid);
        }
    }

    /**
     * Invoked when a player joins the server.
     * Guarantees that the connecting viewer is not displayed as dead,
     * and sends the current collection of dead entries to them as a single batch.
     *
     * @param viewerUuid UUID of the online joining player
     */
    public void onPlayerJoin(@NotNull UUID viewerUuid) {
        Objects.requireNonNull(viewerUuid, "viewerUuid cannot be null");

        // Edge case / conflict: Never display a fake dead entry for an online player
        if (deadEntries.containsKey(viewerUuid)) {
            removeDead(viewerUuid);
        }

        LifeConfig config = configSupplier.get();
        if (!config.isDeadTabEnabled() || deadEntries.isEmpty()) {
            return;
        }

        tabPresenter.showAllTo(viewerUuid, deadEntries.values());
    }

    /**
     * Periodic tick invoked every second by the common platform scheduler.
     * Evaluates natural ban expiration without any allocations when cache is empty.
     *
     * @param now current instant
     */
    public void tick(@NotNull Instant now) {
        // Fast path: zero allocation when dead cache is empty
        if (deadEntries.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, DeadEntry>> it = deadEntries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, DeadEntry> entry = it.next();
            if (entry.getValue().bannedUntil().isBefore(now)) {
                it.remove();
                tabPresenter.hideDead(entry.getKey());
            }
        }

        // Periodic 60-second display update if dynamic remaining time is enabled
        LifeConfig config = configSupplier.get();
        if (config.isDeadTabShowTime() && !deadEntries.isEmpty()) {
            long nowSec = now.getEpochSecond();
            if (nowSec - lastTimerRefreshEpochSec >= 60L) {
                lastTimerRefreshEpochSec = nowSec;
                tabPresenter.refreshAll(deadEntries.values());
            }
        }
    }

    /**
     * Handles live configuration reload (/life reload).
     *
     * @param oldConfig previous configuration
     * @param newConfig new configuration
     */
    public void onConfigReload(@NotNull LifeConfig oldConfig, @NotNull LifeConfig newConfig) {
        if (oldConfig.isDeadTabEnabled() && !newConfig.isDeadTabEnabled()) {
            for (UUID uuid : deadEntries.keySet()) {
                tabPresenter.hideDead(uuid);
            }
            deadEntries.clear();
        } else if (!oldConfig.isDeadTabEnabled() && newConfig.isDeadTabEnabled()) {
            loadInitialDeadEntries(Instant.now().getEpochSecond());
        } else if (newConfig.isDeadTabEnabled() && !deadEntries.isEmpty()) {
            tabPresenter.refreshAll(deadEntries.values());
        }
    }

    /**
     * Performs graceful cleanup on server stop or plugin disable,
     * removing all fake entries from all online players' client state.
     */
    public void shutdown() {
        for (UUID uuid : deadEntries.keySet()) {
            tabPresenter.hideDead(uuid);
        }
        deadEntries.clear();
    }

    @NotNull
    public Optional<DeadEntry> getDeadEntry(@NotNull UUID uuid) {
        return Optional.ofNullable(deadEntries.get(uuid));
    }

    @NotNull
    public Collection<DeadEntry> getAllDeadEntries() {
        return Collections.unmodifiableCollection(deadEntries.values());
    }

    public int getDeadCount() {
        return deadEntries.size();
    }
}
