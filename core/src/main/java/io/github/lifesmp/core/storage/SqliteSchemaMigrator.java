// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.core.storage;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * Handles versioned database migrations for Life SMP SQLite schema.
 */
public final class SqliteSchemaMigrator {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqliteSchemaMigrator.class);
    private static final int CURRENT_VERSION = 4;

    private final DatabaseManager databaseManager;

    public SqliteSchemaMigrator(@NotNull DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager cannot be null");
    }

    /**
     * Executes pending migrations up to {@link #CURRENT_VERSION}.
     */
    public void migrate() {
        try (Connection conn = databaseManager.getConnection()) {
            ensureVersionTable(conn);
            int current = getCurrentVersion(conn);

            LOGGER.info("Current database schema version: {}", current);

            if (current < 1) {
                applyV1(conn);
                recordVersion(conn, 1);
                LOGGER.info("Applied migration V1: created players and arenas tables");
            }

            if (current < 2) {
                applyV2(conn);
                recordVersion(conn, 2);
                LOGGER.info("Applied migration V2: created idx_players_banned_until index");
            }

            if (current < 3) {
                applyV3(conn);
                recordVersion(conn, 3);
                LOGGER.info("Applied migration V3: added last_name column to players and idx_players_last_name");
            }

            if (current < 4) {
                applyV4(conn);
                recordVersion(conn, 4);
                LOGGER.info("Applied migration V4: added pardon_notice column to players");
            }

            LOGGER.info("Database schema is up to date at version {}", CURRENT_VERSION);
        } catch (SQLException e) {
            LOGGER.error("Failed to run SQLite database migrations", e);
            throw new RuntimeException("Database migration failed", e);
        }
    }

    private void ensureVersionTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS schema_version (
                        version INTEGER PRIMARY KEY,
                        applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    );
                    """);
        }
    }

    private int getCurrentVersion(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT MAX(version) FROM schema_version;")) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        }
    }

    private void recordVersion(Connection conn, int version) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO schema_version (version) VALUES (" + version + ");");
        }
    }

    private void applyV1(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Players table
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS players (
                        uuid TEXT PRIMARY KEY,
                        lives INTEGER NOT NULL,
                        online_seconds_since_last_loss INTEGER NOT NULL DEFAULT 0,
                        banned_until INTEGER,
                        last_seen INTEGER NOT NULL,
                        gift_regen_active INTEGER NOT NULL DEFAULT 0,
                        last_name TEXT
                    );
                    """);

            // Arenas table
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS arenas (
                        name TEXT PRIMARY KEY,
                        world TEXT NOT NULL,
                        x1 REAL NOT NULL,
                        y1 REAL NOT NULL,
                        z1 REAL NOT NULL,
                        x2 REAL NOT NULL,
                        y2 REAL NOT NULL,
                        z2 REAL NOT NULL,
                        enabled INTEGER NOT NULL DEFAULT 1
                    );
                    """);
        }
    }

    private void applyV2(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_players_banned_until ON players(banned_until);");
        }
    }

    private void applyV3(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            boolean hasLastName = false;
            try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(players);")) {
                while (rs.next()) {
                    if ("last_name".equalsIgnoreCase(rs.getString("name"))) {
                        hasLastName = true;
                        break;
                    }
                }
            }
            if (!hasLastName) {
                stmt.execute("ALTER TABLE players ADD COLUMN last_name TEXT;");
            }
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_players_last_name ON players(last_name);");
        }
    }

    private void applyV4(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(players);")) {
                while (rs.next()) {
                    if ("pardon_notice".equalsIgnoreCase(rs.getString("name"))) {
                        return;
                    }
                }
            }
            stmt.execute("ALTER TABLE players ADD COLUMN pardon_notice INTEGER NOT NULL DEFAULT 0;");
        }
    }
}
