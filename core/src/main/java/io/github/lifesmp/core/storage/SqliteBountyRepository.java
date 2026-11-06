// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.core.storage;

import io.github.lifesmp.core.domain.model.Bounty;
import io.github.lifesmp.core.ports.BountyRepository;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * SQLite implementation of {@link BountyRepository}. Writes run in submission order.
 */
public final class SqliteBountyRepository implements BountyRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqliteBountyRepository.class);

    private final DatabaseManager databaseManager;
    private final ExecutorService ioExecutor;
    // The shared executor has several threads; chaining keeps a delete from overtaking an earlier save
    private CompletableFuture<Void> lastWrite = CompletableFuture.completedFuture(null);

    public SqliteBountyRepository(@NotNull DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager cannot be null");
        this.ioExecutor = databaseManager.getIoExecutor();
    }

    @FunctionalInterface
    private interface SqlWork {
        void run(Connection conn) throws SQLException;
    }

    private synchronized CompletableFuture<Void> write(String what, SqlWork work) {
        lastWrite = lastWrite.exceptionally(e -> null).thenRunAsync(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                work.run(conn);
            } catch (SQLException e) {
                LOGGER.error("Failed to {}", what, e);
                throw new RuntimeException("Error: " + what, e);
            }
        }, ioExecutor);
        return lastWrite;
    }

    @Override
    public synchronized CompletableFuture<Void> flush() {
        return lastWrite.exceptionally(e -> null);
    }

    @Override
    public CompletableFuture<Snapshot> loadAll() {
        return CompletableFuture.supplyAsync(() -> {
            List<Bounty> bounties = new ArrayList<>();
            Map<UUID, Long> wanted = new HashMap<>();
            Map<String, Instant> cooldowns = new HashMap<>();
            try (Connection conn = databaseManager.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement("""
                        SELECT target, issuer, issuer_name, items, xp, lives, buyout, survived_seconds, created_at
                        FROM bounties;""");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        bounties.add(new Bounty(
                                UUID.fromString(rs.getString("target")),
                                UUID.fromString(rs.getString("issuer")),
                                rs.getString("issuer_name"),
                                rs.getBytes("items"),
                                rs.getInt("xp"),
                                rs.getInt("lives"),
                                rs.getInt("buyout"),
                                rs.getLong("survived_seconds"),
                                Instant.ofEpochMilli(rs.getLong("created_at"))
                        ));
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement("SELECT uuid, survived_seconds FROM wanted;");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        wanted.put(UUID.fromString(rs.getString("uuid")), rs.getLong("survived_seconds"));
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT hunter, target, until FROM bounty_cooldowns WHERE until > ?;")) {
                    ps.setLong(1, System.currentTimeMillis());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            cooldowns.put(rs.getString("hunter") + ":" + rs.getString("target"),
                                    Instant.ofEpochMilli(rs.getLong("until")));
                        }
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to load bounties", e);
                throw new RuntimeException("Error loading bounties", e);
            }
            return new Snapshot(bounties, wanted, cooldowns);
        }, ioExecutor);
    }

    @Override
    public CompletableFuture<Void> saveBounty(@NotNull Bounty b) {
        return write("save bounty on " + b.target(), conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT OR REPLACE INTO bounties
                        (target, issuer, issuer_name, items, xp, lives, buyout, survived_seconds, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);""")) {
                ps.setString(1, b.target().toString());
                ps.setString(2, b.issuer().toString());
                ps.setString(3, b.issuerName());
                ps.setBytes(4, b.items());
                ps.setInt(5, b.xpLevels());
                ps.setInt(6, b.lives());
                ps.setInt(7, b.buyoutLevels());
                ps.setLong(8, b.survivedSeconds());
                ps.setLong(9, b.createdAt().toEpochMilli());
                ps.executeUpdate();
            }
        });
    }

    @Override
    public CompletableFuture<Void> deleteBounty(@NotNull UUID target) {
        return write("delete bounty on " + target, conn -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM bounties WHERE target = ?;")) {
                ps.setString(1, target.toString());
                ps.executeUpdate();
            }
        });
    }

    @Override
    public CompletableFuture<Void> saveWanted(@NotNull UUID player, long survivedSeconds) {
        return write("save wanted " + player, conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR REPLACE INTO wanted (uuid, survived_seconds) VALUES (?, ?);")) {
                ps.setString(1, player.toString());
                ps.setLong(2, survivedSeconds);
                ps.executeUpdate();
            }
        });
    }

    @Override
    public CompletableFuture<Void> deleteWanted(@NotNull UUID player) {
        return write("delete wanted " + player, conn -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM wanted WHERE uuid = ?;")) {
                ps.setString(1, player.toString());
                ps.executeUpdate();
            }
        });
    }

    @Override
    public CompletableFuture<Void> saveCooldown(@NotNull UUID hunter, @NotNull UUID target, @NotNull Instant until) {
        return write("save bounty cooldown", conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR REPLACE INTO bounty_cooldowns (hunter, target, until) VALUES (?, ?, ?);")) {
                ps.setString(1, hunter.toString());
                ps.setString(2, target.toString());
                ps.setLong(3, until.toEpochMilli());
                ps.executeUpdate();
            }
        });
    }
}
