// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) <FILL IN: year> <FILL IN: author>

package io.github.lifesmp.paper;

import io.github.lifesmp.core.config.LifeConfig;
import io.github.lifesmp.core.domain.service.*;
import io.github.lifesmp.core.ports.EconomyIntegration;
import io.github.lifesmp.core.ports.PlatformScheduler;
import io.github.lifesmp.core.storage.*;
import io.github.lifesmp.paper.command.PaperCommandRegistrar;
import io.github.lifesmp.paper.config.PaperConfigAdapter;
import io.github.lifesmp.paper.listener.*;
import io.github.lifesmp.paper.platform.PaperNotificationService;
import io.github.lifesmp.paper.platform.PaperPlatformScheduler;
import io.github.lifesmp.paper.scoreboard.PaperScoreboardManager;
import io.github.lifesmp.paper.task.AutosaveTask;
import io.github.lifesmp.paper.task.RegenerationTask;
import io.github.lifesmp.paper.util.AttackerAttributor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Main Paper plugin entrypoint for Life SMP.
 * Initializes the hexagonal core, SQLite connection pool, listeners, tasks, and commands.
 */
public final class LifeSmpPaperPlugin extends JavaPlugin {

    private volatile LifeConfig lifeConfig;

    private DatabaseManager databaseManager;
    private SqlitePlayerRepository playerRepository;
    private SqliteArenaRepository arenaRepository;
    private LifeProfileCache profileCache;

    private ArenaService arenaService;
    private PvPPolicyEngine pvpPolicyEngine;
    private RegenerationService regenerationService;
    private GiftService giftService;
    private LifeEngine lifeEngine;

    private PaperScoreboardManager scoreboardManager;
    private PaperNotificationService notificationService;
    private PaperPlatformScheduler platformScheduler;
    private AttackerAttributor attackerAttributor;
    private io.github.lifesmp.paper.platform.PaperTabPresenter tabPresenter;
    private DeadTabService deadTabService;

    private PlatformScheduler.CancellableTask regenTask;
    private PlatformScheduler.CancellableTask autosaveTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.lifeConfig = PaperConfigAdapter.load(getConfig());

        // Initialize SQLite database
        Path dataFolder = getDataFolder().toPath();
        dataFolder.toFile().mkdirs();
        Path dbFile = dataFolder.resolve("lifesmp.db");

        this.databaseManager = new DatabaseManager(dbFile);
        new SqliteSchemaMigrator(databaseManager).migrate();

        this.playerRepository = new SqlitePlayerRepository(databaseManager);
        this.arenaRepository = new SqliteArenaRepository(databaseManager);
        this.profileCache = new LifeProfileCache(playerRepository);

        this.arenaService = new ArenaService(arenaRepository);
        this.arenaService.loadArenas().join();

        this.pvpPolicyEngine = new PvPPolicyEngine();
        this.regenerationService = new RegenerationService();
        this.giftService = new GiftService(EconomyIntegration.noop());

        this.scoreboardManager = new PaperScoreboardManager();
        this.notificationService = new PaperNotificationService(this, scoreboardManager);
        this.platformScheduler = new PaperPlatformScheduler(this);
        this.attackerAttributor = new AttackerAttributor();
        this.tabPresenter = new io.github.lifesmp.paper.platform.PaperTabPresenter(this, this::getLifeConfig, scoreboardManager);
        this.deadTabService = new DeadTabService(tabPresenter, playerRepository, this::getLifeConfig);
        this.deadTabService.loadInitialDeadEntries(java.time.Instant.now().getEpochSecond());

        this.lifeEngine = new LifeEngine(
                profileCache,
                arenaService,
                pvpPolicyEngine,
                regenerationService,
                giftService,
                notificationService,
                this::getLifeConfig,
                deadTabService
        );

        // Register listeners
        var pm = Bukkit.getPluginManager();
        pm.registerEvents(new CombatDamageListener(lifeEngine, attackerAttributor, this::getLifeConfig), this);
        pm.registerEvents(new PlayerDeathListener(lifeEngine, attackerAttributor, this::getLifeConfig), this);
        pm.registerEvents(new PlayerJoinQuitListener(lifeEngine, scoreboardManager, attackerAttributor, this::getLifeConfig), this);
        pm.registerEvents(new PlayerMoveListener(regenerationService), this);
        pm.registerEvents(new ChatListener(profileCache, this::getLifeConfig), this);

        // Register Brigadier commands
        new PaperCommandRegistrar(this, lifeEngine).registerCommands();

        // Start tasks
        startTasks();

        getLogger().info("Life SMP Paper plugin enabled successfully.");
    }

    private void startTasks() {
        // Regeneration task: runs every 20 ticks (1s)
        this.regenTask = platformScheduler.runRepeatingSync(
                new RegenerationTask(
                        profileCache,
                        regenerationService,
                        pvpPolicyEngine,
                        giftService,
                        notificationService,
                        deadTabService,
                        this::getLifeConfig
                ),
                20L,
                20L
        );

        // Autosave task: runs every autosave-minutes
        long autosaveTicks = Math.max(1200L, Math.round(lifeConfig.getAutosaveSeconds() * 20.0));
        this.autosaveTask = platformScheduler.runRepeatingAsync(
                new AutosaveTask(profileCache),
                autosaveTicks,
                autosaveTicks
        );
    }

    /**
     * Reloads configuration from config.yml without breaking players' current regeneration progress.
     */
    public synchronized void reloadPluginConfig() {
        reloadConfig();
        LifeConfig oldConfig = this.lifeConfig;
        LifeConfig newConfig = PaperConfigAdapter.load(getConfig());
        this.lifeConfig = newConfig;

        // Proportional progress preservation
        regenerationService.adjustProfilesOnConfigReload(
                profileCache.getAllCachedProfiles(),
                oldConfig,
                newConfig
        );

        if (deadTabService != null) {
            deadTabService.onConfigReload(oldConfig, newConfig);
        }

        // Reschedule autosave task if interval changed
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        long autosaveTicks = Math.max(1200L, Math.round(newConfig.getAutosaveSeconds() * 20.0));
        this.autosaveTask = platformScheduler.runRepeatingAsync(
                new AutosaveTask(profileCache),
                autosaveTicks,
                autosaveTicks
        );

        getLogger().info("Life SMP configuration reloaded.");
    }

    @Override
    public void onDisable() {
        if (regenTask != null) {
            regenTask.cancel();
        }
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }

        if (deadTabService != null) {
            deadTabService.shutdown();
        }
        if (scoreboardManager != null) {
            scoreboardManager.clearAndRemoveDeadTeam();
        }

        // Graceful shutdown: flush in-memory dirty profiles and wait for pending DB writes
        if (profileCache != null) {
            profileCache.gracefulShutdown(10L);
        }

        if (playerRepository != null) {
            playerRepository.close();
        }
        if (arenaRepository != null) {
            arenaRepository.close();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }

        getLogger().info("Life SMP Paper plugin disabled cleanly.");
    }

    @NotNull
    public LifeConfig getLifeConfig() {
        return lifeConfig;
    }

    @NotNull
    public LifeEngine getLifeEngine() {
        return lifeEngine;
    }

    @NotNull
    public PaperScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    @NotNull
    public DeadTabService getDeadTabService() {
        return deadTabService;
    }
}
