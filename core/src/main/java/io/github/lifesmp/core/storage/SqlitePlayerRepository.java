// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.core.storage;

import io.github.lifesmp.core.domain.model.LifeProfile;
import io.github.lifesmp.core.ports.PlayerRepository;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * SQLite implementation of {@link PlayerRepository} using HikariCP
 * and an asynchronous worker pool to ensure zero I/O on the platform main thread.
 */
public final class SqlitePlayerRepository implements PlayerRepository, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqlitePlayerRepository.class);

    private final DatabaseManager databaseManager;
    private final ExecutorService ioExecutor;

    public SqlitePlayerRepository(@NotNull DatabaseManager databaseManager) {
        this(databaseManager, databaseManager.getIoExecutor());
    }

    public SqlitePlayerRepository(@NotNull DatabaseManager databaseManager, @NotNull ExecutorService ioExecutor) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager cannot be null");
        this.ioExecutor = Objects.requireNonNull(ioExecutor, "ioExecutor cannot be null");
    }

    @Override
    public CompletableFuture<Optional<LifeProfile>> loadPlayer(@NotNull UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT lives, online_seconds_since_last_loss, banned_until, last_seen, gift_regen_active, last_name, pardon_notice FROM players WHERE uuid = ?;";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int lives = rs.getInt("lives");
                        long onlineSeconds = rs.getLong("online_seconds_since_last_loss");
                        long bannedUntilSec = rs.getLong("banned_until");
                        Instant bannedUntil = rs.wasNull() ? null : Instant.ofEpochSecond(bannedUntilSec);
                        Instant lastSeen = Instant.ofEpochSecond(rs.getLong("last_seen"));
                        boolean giftRegenActive = rs.getInt("gift_regen_active") != 0;
                        String lastName = rs.getString("last_name");

                        LifeProfile profile = new LifeProfile(
                                uuid,
                                lives,
                                onlineSeconds,
                                bannedUntil,
                                lastSeen,
                                giftRegenActive,
                                lastName
                        );
                        profile.setPardonNoticePending(rs.getInt("pardon_notice") != 0);
                        return Optional.of(profile);
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to load player profile for {}", uuid, e);
                throw new RuntimeException("Error loading player profile: " + uuid, e);
            }
            return Optional.empty();
        }, ioExecutor);
    }

    @Override
    public CompletableFuture<Void> savePlayer(@NotNull LifeProfile profile) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT INTO players (uuid, lives, online_seconds_since_last_loss, banned_until, last_seen, gift_regen_active, last_name, pardon_notice)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(uuid) DO UPDATE SET
                        lives = excluded.lives,
                        online_seconds_since_last_loss = excluded.online_seconds_since_last_loss,
                        banned_until = excluded.banned_until,
                        last_seen = excluded.last_seen,
                        gift_regen_active = excluded.gift_regen_active,
                        last_name = excluded.last_name,
                        pardon_notice = excluded.pardon_notice;
                    """;
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                bindProfile(ps, profile);
                ps.executeUpdate();
            } catch (SQLException e) {
                LOGGER.error("Failed to save player profile for {}", profile.getUuid(), e);
                throw new RuntimeException("Error saving player profile: " + profile.getUuid(), e);
            }
        }, ioExecutor);
    }

    @Override
    public CompletableFuture<Void> saveAll(@NotNull Collection<LifeProfile> profiles) {
        if (profiles.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT INTO players (uuid, lives, online_seconds_since_last_loss, banned_until, last_seen, gift_regen_active, last_name, pardon_notice)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(uuid) DO UPDATE SET
                        lives = excluded.lives,
                        online_seconds_since_last_loss = excluded.online_seconds_since_last_loss,
                        banned_until = excluded.banned_until,
                        last_seen = excluded.last_seen,
                        gift_regen_active = excluded.gift_regen_active,
                        last_name = excluded.last_name,
                        pardon_notice = excluded.pardon_notice;
                    """;
            try (Connection conn = databaseManager.getConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    for (LifeProfile profile : profiles) {
                        bindProfile(ps, profile);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                    conn.commit();
                } catch (SQLException ex) {
                    conn.rollback();
                    throw ex;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to execute batch save for {} profiles", profiles.size(), e);
                throw new RuntimeException("Error executing batch player profiles save", e);
            }
        }, ioExecutor);
    }

    @Override
    public CompletableFuture<List<UUID>> getBannedPlayerUuids(long nowEpochSecond) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT uuid FROM players WHERE banned_until IS NOT NULL AND banned_until > ?;";
            List<UUID> list = new ArrayList<>();
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, nowEpochSecond);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(UUID.fromString(rs.getString("uuid")));
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to query banned player UUIDs", e);
                throw new RuntimeException("Error querying banned player UUIDs", e);
            }
            return list;
        }, ioExecutor);
    }

    @Override
    public CompletableFuture<Optional<UUID>> findUuidByLastName(@NotNull String lastName) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT uuid FROM players WHERE LOWER(last_name) = LOWER(?) LIMIT 1;";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, lastName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(UUID.fromString(rs.getString("uuid")));
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to query UUID for last_name {}", lastName, e);
                throw new RuntimeException("Error querying UUID for last_name: " + lastName, e);
            }
            return Optional.empty();
        }, ioExecutor);
    }

    @Override
    public CompletableFuture<List<io.github.lifesmp.core.domain.model.DeadEntry>> loadActiveDeadEntries(long nowEpochSecond) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT uuid, last_name, banned_until FROM players WHERE lives = 0 AND banned_until IS NOT NULL AND banned_until > ? AND last_name IS NOT NULL;";
            List<io.github.lifesmp.core.domain.model.DeadEntry> list = new ArrayList<>();
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, nowEpochSecond);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String name = rs.getString("last_name");
                        if (name == null || name.isBlank() || name.length() > 16) {
                            LOGGER.debug("Skipping dead entry with invalid name: {}", name);
                            continue;
                        }
                        UUID uuid = UUID.fromString(rs.getString("uuid"));
                        Instant bannedUntil = Instant.ofEpochSecond(rs.getLong("banned_until"));
                        list.add(new io.github.lifesmp.core.domain.model.DeadEntry(uuid, name, bannedUntil));
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to query active dead entries", e);
                throw new RuntimeException("Error querying active dead entries", e);
            }
            return list;
        }, ioExecutor);
    }

    private void bindProfile(PreparedStatement ps, LifeProfile profile) throws SQLException {
        ps.setString(1, profile.getUuid().toString());
        ps.setInt(2, profile.getLives());
        ps.setLong(3, profile.getOnlineSecondsSinceLastLoss());

        Instant bannedUntil = profile.getBannedUntil();
        if (bannedUntil != null) {
            ps.setLong(4, bannedUntil.getEpochSecond());
        } else {
            ps.setNull(4, Types.INTEGER);
        }

        ps.setLong(5, profile.getLastSeen().getEpochSecond());
        ps.setInt(6, profile.isGiftRegenActive() ? 1 : 0);

        if (profile.getLastName() != null) {
            ps.setString(7, profile.getLastName());
        } else {
            ps.setNull(7, Types.VARCHAR);
        }
        ps.setInt(8, profile.isPardonNoticePending() ? 1 : 0);
    }

    @Override
    public void close() {
        // Shared executor lifecycle is managed by DatabaseManager
    }
}
