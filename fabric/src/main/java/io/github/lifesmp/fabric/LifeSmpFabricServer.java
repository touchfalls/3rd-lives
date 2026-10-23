// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.fabric;

import io.github.lifesmp.core.config.LifeConfig;
import io.github.lifesmp.core.domain.model.LifeProfile;
import io.github.lifesmp.core.domain.model.PvPDecision;
import io.github.lifesmp.core.domain.service.ArenaService;
import io.github.lifesmp.core.domain.service.DeadTabService;
import io.github.lifesmp.core.domain.service.GiftService;
import io.github.lifesmp.core.domain.service.LifeEngine;
import io.github.lifesmp.core.domain.service.PvPPolicyEngine;
import io.github.lifesmp.core.domain.service.RegenerationService;
import io.github.lifesmp.core.ports.EconomyIntegration;
import io.github.lifesmp.core.ports.PlatformScheduler;
import io.github.lifesmp.core.storage.DatabaseManager;
import io.github.lifesmp.core.storage.LifeProfileCache;
import io.github.lifesmp.core.storage.SqliteArenaRepository;
import io.github.lifesmp.core.storage.SqlitePlayerRepository;
import io.github.lifesmp.core.storage.SqliteSchemaMigrator;
import io.github.lifesmp.fabric.command.FabricCommandRegistrar;
import io.github.lifesmp.fabric.config.FabricConfigAdapter;
import io.github.lifesmp.fabric.platform.FabricNotificationService;
import io.github.lifesmp.fabric.platform.FabricPlatformScheduler;
import io.github.lifesmp.fabric.platform.FabricServerBridge;
import io.github.lifesmp.fabric.platform.FabricTabPresenter;
import io.github.lifesmp.fabric.scoreboard.FabricScoreboardManager;
import io.github.lifesmp.fabric.util.FabricMessageFormatter;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Server-only Fabric entrypoint for Life SMP.
 * Initializes storage, domain services, bridges, scheduled routines, and event handlers.
 */
public final class LifeSmpFabricServer implements DedicatedServerModInitializer, FabricServerHost {

    private static final Logger LOGGER = LoggerFactory.getLogger(LifeSmpFabricServer.class);
    private static LifeSmpFabricServer instance;

    private Path configPath;
    private volatile LifeConfig lifeConfig;

    private DatabaseManager databaseManager;
    private SqlitePlayerRepository playerRepository;
    private SqliteArenaRepository arenaRepository;
    private LifeProfileCache profileCache;

    private ArenaService arenaService;
    private PvPPolicyEngine pvpPolicyEngine;
    private RegenerationService regenerationService;
    private GiftService giftService;
    private DeadTabService deadTabService;
    private LifeEngine lifeEngine;

    private FabricScoreboardManager scoreboardManager;
    private FabricTabPresenter tabPresenter;
    private FabricServerBridge bridge;
    private FabricNotificationService notificationService;
    private FabricPlatformScheduler scheduler;

    private PlatformScheduler.CancellableTask regenTask;
    private PlatformScheduler.CancellableTask autosaveTask;

    @Nullable
    private Executor serverExecutor;

    public static LifeSmpFabricServer getInstance() {
        return instance;
    }

    @Override
    public void onInitializeServer() {
        instance = this;
        LOGGER.info("Initializing Life SMP Fabric server adapter...");

        try {
            checkCriticalDependencies();

            Path configDir = FabricLoader.getInstance().getConfigDir().resolve("lifesmp");
            configDir.toFile().mkdirs();
            this.configPath = configDir.resolve("config.json");
            this.lifeConfig = FabricConfigAdapter.loadOrCreate(configPath);

            // Initialize SQLite
            Path dbFile = configDir.resolve("lifesmp.db");
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

            this.scoreboardManager = new FabricScoreboardManager(() -> bridge.getServer());
            this.bridge = new FabricServerBridge(scoreboardManager);
            this.notificationService = new FabricNotificationService(bridge);
            this.scheduler = new FabricPlatformScheduler(() -> serverExecutor);

            this.tabPresenter = new FabricTabPresenter(
                    () -> bridge.getServer(),
                    () -> this.lifeConfig,
                    this.scoreboardManager
            );
            this.deadTabService = new DeadTabService(
                    this.tabPresenter,
                    this.playerRepository,
                    () -> this.lifeConfig
            );

            this.lifeEngine = new LifeEngine(
                    profileCache,
                    arenaService,
                    pvpPolicyEngine,
                    regenerationService,
                    giftService,
                    notificationService
            );
            this.lifeEngine.setDeadTabService(this.deadTabService);

            registerLifecycleEvents();
            registerConnectionEvents();
            registerCombatEvents();
            registerChatEvents();
            registerCommands();
            startTasks();

            LOGGER.info("Life SMP Fabric server adapter initialized successfully.");
        } catch (Throwable t) {
            LOGGER.error("CRITICAL: Failed to initialize Life SMP Fabric adapter: {}", t.getMessage(), t);
            throw new RuntimeException("Life SMP initialization failed: " + t.getMessage(), t);
        }
    }

    private void checkCriticalDependencies() {
        try {
            Class.forName("com.zaxxer.hikari.HikariConfig");
        } catch (ClassNotFoundException e) {
            String errorMsg = "Critical dependency missing: HikariCP ('com.zaxxer.hikari.HikariConfig') could not be loaded. " +
                    "Ensure HikariCP is included via Fabric Jar-in-Jar.";
            LOGGER.error(errorMsg);
            throw new IllegalStateException(errorMsg, e);
        }

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            String errorMsg = "Critical dependency missing: SQLite JDBC driver ('org.sqlite.JDBC') could not be loaded. " +
                    "Ensure sqlite-jdbc is included via Fabric Jar-in-Jar.";
            LOGGER.error(errorMsg);
            throw new IllegalStateException(errorMsg, e);
        }
    }

    private void registerLifecycleEvents() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            this.serverExecutor = server;
            this.bridge.setServer(server);
            if (deadTabService != null) {
                deadTabService.loadInitialDeadEntries(Instant.now().getEpochSecond());
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            this.shutdown();
            this.bridge.setServer(null);
        });
    }

    private void registerConnectionEvents() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            UUID uuid = player.getUUID();
            String playerName = player.getGameProfile().getName();
            Instant now = Instant.now();
            LifeConfig config = this.lifeConfig;

            lifeEngine.handlePlayerJoin(uuid, playerName, now, config).thenAccept(eval -> {
                if (!eval.allowed()) {
                    String reason = eval.kickReason() != null ? eval.kickReason() : config.getRawMessage("death.ban-kick");
                    handler.disconnect(FabricMessageFormatter.parse(reason));
                } else {
                    bridge.updateVisuals(uuid, eval.profile().getLives());
                    bridge.deliverPendingMessages(player);
                    if (deadTabService != null) {
                        deadTabService.onPlayerJoin(uuid);
                    }
                }
            });
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.player;
            if (player != null) {
                lifeEngine.handlePlayerQuit(player.getUUID(), Instant.now());
                if (deadTabService != null) {
                    deadTabService.getDeadEntry(player.getUUID()).ifPresent(entry -> {
                        tabPresenter.showDead(entry);
                    });
                }
            }
        });
    }

    private void registerCombatEvents() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer victim)) {
                return true;
            }
            if (!(source.getEntity() instanceof ServerPlayer attacker)) {
                return true;
            }
            if (victim.getUUID().equals(attacker.getUUID())) {
                return true;
            }

            Instant now = Instant.now();
            LifeConfig config = this.lifeConfig;
            String worldName = victim.level().dimension().location().toString();

            boolean inActiveArena = arenaService.isInsideActiveArena(
                    worldName, victim.getX(), victim.getY(), victim.getZ()
            );
            boolean globalOverride = arenaService.isGlobalPvpOverrideActive(now);

            LifeProfile attackerProfile = profileCache.getCachedProfile(attacker.getUUID()).orElse(null);
            LifeProfile victimProfile = profileCache.getCachedProfile(victim.getUUID()).orElse(null);

            PvPDecision decision = pvpPolicyEngine.evaluate(
                    attacker.getUUID(),
                    attackerProfile,
                    victim.getUUID(),
                    victimProfile,
                    inActiveArena,
                    globalOverride,
                    now,
                    config
            );

            if (!decision.allowed()) {
                if (decision.message() != null && !decision.message().isBlank()) {
                    bridge.sendMessage(attacker.getUUID(), decision.message(), true);
                }
                return false;
            }
            return true;
        });
    }

    private void registerChatEvents() {
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            bridge.handleChatMessage(sender, message);
        });
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            FabricCommandRegistrar<CommandSourceStack> registrar = new FabricCommandRegistrar<>(
                    this,
                    lifeEngine,
                    new FabricCommandRegistrar.SourceAdapter<>() {
                        @Override
                        public String getName(@NotNull CommandSourceStack source) {
                            return source.getTextName();
                        }

                        @Override
                        public UUID getUuid(@NotNull CommandSourceStack source) {
                            return source.getPlayer() != null ? source.getPlayer().getUUID() : null;
                        }

                        @Override
                        public boolean isPlayer(@NotNull CommandSourceStack source) {
                            return source.isPlayer();
                        }

                        @Override
                        public boolean hasPermission(@NotNull CommandSourceStack source, int level) {
                            return source.hasPermission(level);
                        }

                        @Override
                        public void sendFeedback(@NotNull CommandSourceStack source, @NotNull String message) {
                            source.sendSuccess(() -> FabricMessageFormatter.parse(message), false);
                        }

                        @Override
                        public Collection<String> getOnlinePlayerNames() {
                            if (bridge.getServer() == null) return List.of();
                            return Arrays.asList(bridge.getServer().getPlayerList().getPlayerNamesArray());
                        }

                        @Override
                        @Nullable
                        public UUID getPlayerUuidByName(@NotNull String name) {
                            if (bridge.getServer() == null) return null;
                            ServerPlayer player = bridge.getServer().getPlayerList().getPlayerByName(name);
                            return player != null ? player.getUUID() : null;
                        }

                        @Override
                        public double getX(@NotNull CommandSourceStack source) {
                            return source.getPosition().x;
                        }

                        @Override
                        public double getY(@NotNull CommandSourceStack source) {
                            return source.getPosition().y;
                        }

                        @Override
                        public double getZ(@NotNull CommandSourceStack source) {
                            return source.getPosition().z;
                        }

                        @Override
                        public String getWorldName(@NotNull CommandSourceStack source) {
                            return source.getLevel().dimension().location().toString();
                        }
                    }
            );
            registrar.register(dispatcher);
        });
    }

    public void setServerExecutor(@NotNull Executor executor) {
        this.serverExecutor = executor;
    }

    private void startTasks() {
        // Regeneration and dead tab tick task (every 1s = 20 ticks)
        this.regenTask = scheduler.runRepeatingSync(() -> {
            if (bridge.getServer() == null) return;
            Instant now = Instant.now();
            LifeConfig config = this.lifeConfig;

            for (ServerPlayer player : bridge.getServer().getPlayerList().getPlayers()) {
                UUID uuid = player.getUUID();
                profileCache.getCachedProfile(uuid).ifPresent(profile -> {
                    RegenerationService.TickResult result = regenerationService.tickPlayer(
                            uuid, profile, 1L, now, config
                    );
                    if (result.lifeGained()) {
                        profileCache.markDirty(uuid);
                        bridge.updateVisuals(uuid, result.newLives());
                        String msg = config.formatMessage("regen.life-gained", "lives", String.valueOf(result.newLives()));
                        bridge.sendMessage(uuid, msg, false);
                    }
                });
            }

            if (deadTabService != null) {
                deadTabService.tick(now);
            }
        }, 20L, 20L);

        // Autosave task
        long autosaveTicks = Math.max(1200L, Math.round(lifeConfig.getAutosaveSeconds() * 20.0));
        this.autosaveTask = scheduler.runRepeatingAsync(
                profileCache::flushDirtyProfiles,
                autosaveTicks,
                autosaveTicks
        );
    }

    /**
     * Reloads configuration from config.json with proportional online progress recalculation.
     */
    public synchronized void reloadServerConfig() {
        LifeConfig oldConfig = this.lifeConfig;
        LifeConfig newConfig = FabricConfigAdapter.loadOrCreate(configPath);
        this.lifeConfig = newConfig;

        regenerationService.adjustProfilesOnConfigReload(
                profileCache.getAllCachedProfiles(),
                oldConfig,
                newConfig
        );

        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        long autosaveTicks = Math.max(1200L, Math.round(newConfig.getAutosaveSeconds() * 20.0));
        this.autosaveTask = scheduler.runRepeatingAsync(
                profileCache::flushDirtyProfiles,
                autosaveTicks,
                autosaveTicks
        );

        if (deadTabService != null) {
            deadTabService.onConfigReload(oldConfig, newConfig);
        }

        LOGGER.info("Life SMP Fabric configuration reloaded.");
    }

    /**
     * Performs clean shutdown and flushes dirty cache to SQLite.
     */
    public void shutdown() {
        if (regenTask != null) regenTask.cancel();
        if (autosaveTask != null) autosaveTask.cancel();

        if (deadTabService != null) {
            deadTabService.shutdown();
        }
        if (scoreboardManager != null) {
            scoreboardManager.clearAndRemoveDeadTeam();
        }
        if (profileCache != null) {
            profileCache.gracefulShutdown(10L);
        }
        if (playerRepository != null) playerRepository.close();
        if (arenaRepository != null) arenaRepository.close();
        if (databaseManager != null) databaseManager.close();
        if (scheduler != null) scheduler.close();

        LOGGER.info("Life SMP Fabric adapter disabled cleanly.");
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
    public FabricServerBridge getBridge() {
        return bridge;
    }

    @NotNull
    public FabricScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    @Nullable
    public DeadTabService getDeadTabService() {
        return deadTabService;
    }

    @Nullable
    public FabricTabPresenter getTabPresenter() {
        return tabPresenter;
    }
}
