// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) <FILL IN: year> <FILL IN: author>

package io.github.lifesmp.core.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.lifesmp.core.util.NamedThreadFactory;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Manages the HikariCP connection pool and shared asynchronous I/O executor for SQLite storage,
 * configuring Write-Ahead Logging (WAL) and synchronous pragmas for optimal concurrency.
 */
public final class DatabaseManager implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseManager.class);

    private final HikariDataSource dataSource;
    private final ExecutorService ioExecutor;

    public DatabaseManager(@NotNull Path databaseFile) {
        this(databaseFile, Executors.newFixedThreadPool(2, new NamedThreadFactory("lifesmp-io")));
    }

    public DatabaseManager(@NotNull Path databaseFile, @NotNull ExecutorService ioExecutor) {
        Objects.requireNonNull(databaseFile, "databaseFile cannot be null");
        this.ioExecutor = Objects.requireNonNull(ioExecutor, "ioExecutor cannot be null");

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            LOGGER.error("SQLite JDBC driver class 'org.sqlite.JDBC' not found on classpath", e);
            throw new IllegalStateException("SQLite JDBC driver missing from classpath", e);
        }

        HikariConfig config = new HikariConfig();
        config.setPoolName("LifeSMP-SQLite-Pool");
        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl("jdbc:sqlite:" + databaseFile.toAbsolutePath());
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        // SQLite PRAGMA optimization
        config.addDataSourceProperty("busy_timeout", "5000");

        this.dataSource = new HikariDataSource(config);

        // Apply WAL and performance pragmas
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode = WAL;");
            stmt.execute("PRAGMA synchronous = NORMAL;");
            stmt.execute("PRAGMA foreign_keys = ON;");
        } catch (SQLException e) {
            LOGGER.error("Failed to initialize SQLite database pragmas", e);
            throw new RuntimeException("Database initialization failed", e);
        }

        LOGGER.info("Initialized SQLite database at {}", databaseFile.toAbsolutePath());
    }

    /**
     * Obtains an active connection from the pool.
     *
     * @return pooled SQL Connection
     * @throws SQLException if a connection cannot be acquired
     */
    @NotNull
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Returns the shared ExecutorService for asynchronous database operations.
     *
     * @return shared I/O executor
     */
    @NotNull
    public ExecutorService getIoExecutor() {
        return ioExecutor;
    }

    @Override
    public void close() {
        LOGGER.info("Shutting down database I/O executor...");
        ioExecutor.shutdown();
        try {
            if (!ioExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                LOGGER.warn("Database I/O executor did not terminate within 10s, forcing shutdown");
                ioExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            LOGGER.warn("Database shutdown interrupted, forcing shutdown");
            ioExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        if (!dataSource.isClosed()) {
            dataSource.close();
            LOGGER.info("Closed SQLite connection pool");
        }
    }
}
