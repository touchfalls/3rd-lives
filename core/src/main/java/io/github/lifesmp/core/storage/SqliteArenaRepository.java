// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.core.storage;

import io.github.lifesmp.core.domain.model.Arena;
import io.github.lifesmp.core.ports.ArenaRepository;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * SQLite implementation of {@link ArenaRepository}.
 */
public final class SqliteArenaRepository implements ArenaRepository, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqliteArenaRepository.class);

    private final DatabaseManager databaseManager;
    private final ExecutorService ioExecutor;

    public SqliteArenaRepository(@NotNull DatabaseManager databaseManager) {
        this(databaseManager, databaseManager.getIoExecutor());
    }

    public SqliteArenaRepository(@NotNull DatabaseManager databaseManager, @NotNull ExecutorService ioExecutor) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager cannot be null");
        this.ioExecutor = Objects.requireNonNull(ioExecutor, "ioExecutor cannot be null");
    }

    @Override
    public CompletableFuture<List<Arena>> loadAllArenas() {
        return CompletableFuture.supplyAsync(() -> {
            List<Arena> list = new ArrayList<>();
            String sql = "SELECT name, world, x1, y1, z1, x2, y2, z2, enabled FROM arenas;";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    String world = rs.getString("world");
                    double x1 = rs.getDouble("x1");
                    double y1 = rs.getDouble("y1");
                    double z1 = rs.getDouble("z1");
                    double x2 = rs.getDouble("x2");
                    double y2 = rs.getDouble("y2");
                    double z2 = rs.getDouble("z2");
                    boolean enabled = rs.getInt("enabled") != 0;

                    list.add(new Arena(name, world, x1, y1, z1, x2, y2, z2, enabled));
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to load arenas from database", e);
                throw new RuntimeException("Error loading arenas", e);
            }
            return list;
        }, ioExecutor);
    }

    @Override
    public CompletableFuture<Void> saveArena(@NotNull Arena arena) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT INTO arenas (name, world, x1, y1, z1, x2, y2, z2, enabled)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(name) DO UPDATE SET
                        world = excluded.world,
                        x1 = excluded.x1,
                        y1 = excluded.y1,
                        z1 = excluded.z1,
                        x2 = excluded.x2,
                        y2 = excluded.y2,
                        z2 = excluded.z2,
                        enabled = excluded.enabled;
                    """;
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, arena.getName());
                ps.setString(2, arena.getWorld());
                ps.setDouble(3, arena.getMinX());
                ps.setDouble(4, arena.getMinY());
                ps.setDouble(5, arena.getMinZ());
                ps.setDouble(6, arena.getMaxX());
                ps.setDouble(7, arena.getMaxY());
                ps.setDouble(8, arena.getMaxZ());
                ps.setInt(9, arena.isEnabled() ? 1 : 0);

                ps.executeUpdate();
            } catch (SQLException e) {
                LOGGER.error("Failed to save arena {}", arena.getName(), e);
                throw new RuntimeException("Error saving arena: " + arena.getName(), e);
            }
        }, ioExecutor);
    }

    @Override
    public CompletableFuture<Void> deleteArena(@NotNull String name) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM arenas WHERE name = ?;";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name);
                ps.executeUpdate();
            } catch (SQLException e) {
                LOGGER.error("Failed to delete arena {}", name, e);
                throw new RuntimeException("Error deleting arena: " + name, e);
            }
        }, ioExecutor);
    }

    @Override
    public void close() {
        // Shared executor lifecycle is managed by DatabaseManager
    }
}
