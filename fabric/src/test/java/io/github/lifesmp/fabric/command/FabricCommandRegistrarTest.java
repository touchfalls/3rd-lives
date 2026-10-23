// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.fabric.command;

import com.mojang.brigadier.CommandDispatcher;
import io.github.lifesmp.core.config.LifeConfig;
import io.github.lifesmp.core.domain.model.Arena;
import io.github.lifesmp.core.domain.model.LifeProfile;
import io.github.lifesmp.core.domain.service.ArenaService;
import io.github.lifesmp.core.domain.service.GiftService;
import io.github.lifesmp.core.domain.service.LifeEngine;
import io.github.lifesmp.core.domain.service.PvPPolicyEngine;
import io.github.lifesmp.core.domain.service.RegenerationService;
import io.github.lifesmp.core.ports.ArenaRepository;
import io.github.lifesmp.core.ports.EconomyIntegration;
import io.github.lifesmp.core.ports.NotificationService;
import io.github.lifesmp.core.ports.PlayerRepository;
import io.github.lifesmp.core.storage.LifeProfileCache;
import io.github.lifesmp.fabric.FabricServerHost;
import io.github.lifesmp.fabric.platform.FabricServerBridge;
import io.github.lifesmp.fabric.scoreboard.FabricScoreboardManager;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class FabricCommandRegistrarTest {

    static class TestSource {
        final String name;
        final UUID uuid;
        final boolean isPlayer;
        final int permissionLevel;
        final List<String> feedback = new ArrayList<>();

        TestSource(String name, UUID uuid, boolean isPlayer, int permissionLevel) {
            this.name = name;
            this.uuid = uuid;
            this.isPlayer = isPlayer;
            this.permissionLevel = permissionLevel;
        }
    }

    private CommandDispatcher<TestSource> dispatcher;
    private LifeEngine lifeEngine;
    private LifeProfileCache profileCache;
    private Map<String, UUID> onlinePlayers;
    private boolean reloadCalled;

    @BeforeEach
    void setUp() {
        dispatcher = new CommandDispatcher<>();
        onlinePlayers = new HashMap<>();
        reloadCalled = false;

        PlayerRepository playerRepo = new PlayerRepository() {
            @Override
            public CompletableFuture<Optional<LifeProfile>> loadPlayer(@NotNull UUID uuid) {
                return CompletableFuture.completedFuture(Optional.empty());
            }

            @Override
            public CompletableFuture<Void> savePlayer(@NotNull LifeProfile profile) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<Void> saveAll(@NotNull Collection<LifeProfile> profiles) {
                return CompletableFuture.completedFuture(null);
            }
        };

        ArenaRepository arenaRepo = new ArenaRepository() {
            @Override
            public CompletableFuture<List<Arena>> loadAllArenas() {
                return CompletableFuture.completedFuture(new ArrayList<>());
            }

            @Override
            public CompletableFuture<Void> saveArena(@NotNull Arena arena) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<Void> deleteArena(@NotNull String name) {
                return CompletableFuture.completedFuture(null);
            }
        };

        NotificationService notificationService = new NotificationService() {
            @Override
            public void sendMessage(@NotNull UUID targetUuid, @NotNull String message) {}

            @Override
            public void sendActionBar(@NotNull UUID targetUuid, @NotNull String message) {}

            @Override
            public void broadcast(@NotNull String message) {}

            @Override
            public void kickPlayer(@NotNull UUID targetUuid, @NotNull String reason) {}

            @Override
            public void updateDisplayNameAndTab(@NotNull UUID playerUuid, int lives) {}
        };

        profileCache = new LifeProfileCache(playerRepo);
        ArenaService arenaService = new ArenaService(arenaRepo);
        PvPPolicyEngine pvpPolicyEngine = new PvPPolicyEngine();
        RegenerationService regenerationService = new RegenerationService();
        GiftService giftService = new GiftService(EconomyIntegration.noop());

        lifeEngine = new LifeEngine(
                profileCache,
                arenaService,
                pvpPolicyEngine,
                regenerationService,
                giftService,
                notificationService
        );

        FabricServerHost serverHost = new FabricServerHost() {
            @Override
            public @NotNull LifeConfig getLifeConfig() {
                return LifeConfig.createDefault();
            }

            @Override
            public @NotNull LifeEngine getLifeEngine() {
                return lifeEngine;
            }

            @Override
            public @NotNull FabricServerBridge getBridge() {
                return new FabricServerBridge(new FabricScoreboardManager());
            }

            @Override
            public void reloadServerConfig() {
                reloadCalled = true;
            }
        };

        FabricCommandRegistrar.SourceAdapter<TestSource> adapter = new FabricCommandRegistrar.SourceAdapter<>() {
            @Override
            public String getName(TestSource source) {
                return source.name;
            }

            @Override
            public UUID getUuid(TestSource source) {
                return source.uuid;
            }

            @Override
            public boolean isPlayer(TestSource source) {
                return source.isPlayer;
            }

            @Override
            public boolean hasPermission(TestSource source, int level) {
                return source.permissionLevel >= level;
            }

            @Override
            public void sendFeedback(TestSource source, String message) {
                source.feedback.add(message);
            }

            @Override
            public String getWorldName(TestSource source) {
                return "world";
            }

            @Override
            public double getX(TestSource source) {
                return 0.0;
            }

            @Override
            public double getY(TestSource source) {
                return 64.0;
            }

            @Override
            public double getZ(TestSource source) {
                return 0.0;
            }

            @Override
            public Collection<String> getOnlinePlayerNames() {
                return onlinePlayers.keySet();
            }

            @Override
            public UUID getPlayerUuidByName(String name) {
                return onlinePlayers.get(name);
            }
        };

        FabricCommandRegistrar<TestSource> registrar = new FabricCommandRegistrar<>(serverHost, lifeEngine, adapter);
        registrar.register(dispatcher);
    }

    @Test
    @DisplayName("Brigadier dispatcher registers /life with subcommands")
    void dispatcherRegistersLifeCommands() {
        assertThat(dispatcher.getRoot().getChild("life")).isNotNull();
        assertThat(dispatcher.getRoot().getChild("life").getChild("help")).isNotNull();
        assertThat(dispatcher.getRoot().getChild("life").getChild("gift")).isNotNull();
        assertThat(dispatcher.getRoot().getChild("life").getChild("accept")).isNotNull();
        assertThat(dispatcher.getRoot().getChild("life").getChild("reload")).isNotNull();
        assertThat(dispatcher.getRoot().getChild("life").getChild("set")).isNotNull();
        assertThat(dispatcher.getRoot().getChild("life").getChild("pardon")).isNotNull();
        assertThat(dispatcher.getRoot().getChild("life").getChild("reset")).isNotNull();
        assertThat(dispatcher.getRoot().getChild("life").getChild("event")).isNotNull();
        assertThat(dispatcher.getRoot().getChild("life").getChild("arena")).isNotNull();
    }

    @Test
    @DisplayName("Executing /life shows status and progress to player")
    void executeInfoCommand() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        TestSource playerSource = new TestSource("Steve", playerUuid, true, 0);

        // Preload profile
        profileCache.getOrLoadProfile(playerUuid, 3, Instant.now()).join();

        int result = dispatcher.execute("life", playerSource);
        assertThat(result).isEqualTo(1);
        assertThat(playerSource.feedback).isNotEmpty();
        String output = String.join("\n", playerSource.feedback);
        assertThat(output).contains("Life SMP");
        assertThat(output).contains("3/3");
    }

    @Test
    @DisplayName("Admin command /life reload executes for permission level 4 and fails for level 0")
    void executeReloadPermissionCheck() throws Exception {
        TestSource adminSource = new TestSource("Admin", UUID.randomUUID(), true, 4);
        int adminResult = dispatcher.execute("life reload", adminSource);
        assertThat(adminResult).isEqualTo(1);
        assertThat(reloadCalled).isTrue();

        TestSource nonAdminSource = new TestSource("Player", UUID.randomUUID(), true, 0);
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> {
            dispatcher.execute("life reload", nonAdminSource);
        });
    }

    @Test
    @DisplayName("Admin command /life pardon executes for banned player and fails for unknown player")
    void executePardonCommand() throws Exception {
        UUID bannedUuid = UUID.randomUUID();
        onlinePlayers.put("BannedPlayer", bannedUuid);

        // Preload banned profile
        LifeProfile profile = profileCache.getOrLoadProfile(bannedUuid, 3, Instant.now()).join();
        profile.setLives(0);
        profile.setBannedUntil(Instant.now().plusSeconds(3600));

        TestSource adminSource = new TestSource("Admin", UUID.randomUUID(), true, 4);
        int result = dispatcher.execute("life pardon BannedPlayer", adminSource);
        assertThat(result).isEqualTo(1);
        assertThat(profile.getLives()).isEqualTo(1);
        assertThat(profile.getBannedUntil()).isNull();

        adminSource.feedback.clear();
        dispatcher.execute("life pardon UnknownPlayer", adminSource);
        assertThat(String.join("\n", adminSource.feedback)).contains("UnknownPlayer");
    }

    @Test
    @DisplayName("Admin command /life pardon finds an offline banned player by last known name")
    void executePardonForOfflinePlayer() throws Exception {
        UUID bannedUuid = UUID.randomUUID();
        LifeProfile profile = profileCache.getOrLoadProfile(bannedUuid, 3, Instant.now()).join();
        profile.setLastName("OfflinePlayer");
        profile.setLives(0);
        profile.setBannedUntil(Instant.now().plusSeconds(3600));

        TestSource adminSource = new TestSource("Admin", UUID.randomUUID(), true, 4);
        dispatcher.execute("life pardon OfflinePlayer", adminSource);

        assertThat(profile.getLives()).isEqualTo(1);
        assertThat(profile.getBannedUntil()).isNull();
        assertThat(String.join("\n", adminSource.feedback)).contains("OfflinePlayer");
    }

    @Test
    @DisplayName("Admin command /life set changes lives of an offline player by last known name")
    void executeSetForOfflinePlayer() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        LifeProfile profile = profileCache.getOrLoadProfile(playerUuid, 3, Instant.now()).join();
        profile.setLastName("Sleeper");

        TestSource adminSource = new TestSource("Admin", UUID.randomUUID(), true, 4);
        dispatcher.execute("life set Sleeper 2", adminSource);

        assertThat(profile.getLives()).isEqualTo(2);
    }

    @Test
    @DisplayName("Admin command /life reset restores lives to maximum")
    void executeResetCommand() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        onlinePlayers.put("DamagedPlayer", playerUuid);

        LifeProfile profile = profileCache.getOrLoadProfile(playerUuid, 3, Instant.now()).join();
        profile.setLives(1);

        TestSource adminSource = new TestSource("Admin", UUID.randomUUID(), true, 4);
        int result = dispatcher.execute("life reset DamagedPlayer", adminSource);
        assertThat(result).isEqualTo(1);
        assertThat(profile.getLives()).isEqualTo(3);
    }
}
