// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.fabric.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.lifesmp.core.config.LifeConfig;
import io.github.lifesmp.core.domain.model.Arena;
import io.github.lifesmp.core.domain.model.LifeProfile;
import io.github.lifesmp.core.domain.service.GiftService;
import io.github.lifesmp.core.domain.service.LifeEngine;
import io.github.lifesmp.core.util.FormatUtils;
import io.github.lifesmp.fabric.FabricServerHost;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * Registers /life Brigadier commands for Fabric using standard {@link CommandDispatcher}.
 *
 * @param <S> command source type
 */
public final class FabricCommandRegistrar<S> {

    public interface SourceAdapter<S> {
        String getName(@NotNull S source);
        UUID getUuid(@NotNull S source);
        boolean isPlayer(@NotNull S source);
        boolean hasPermission(@NotNull S source, int level);
        void sendFeedback(@NotNull S source, @NotNull String message);
        String getWorldName(@NotNull S source);
        double getX(@NotNull S source);
        double getY(@NotNull S source);
        double getZ(@NotNull S source);
        Collection<String> getOnlinePlayerNames();
        UUID getPlayerUuidByName(@NotNull String name);
    }

    private final FabricServerHost server;
    private final LifeEngine lifeEngine;
    private final SourceAdapter<S> adapter;

    public FabricCommandRegistrar(
            @NotNull FabricServerHost server,
            @NotNull LifeEngine lifeEngine,
            @NotNull SourceAdapter<S> adapter
    ) {
        this.server = Objects.requireNonNull(server, "server cannot be null");
        this.lifeEngine = Objects.requireNonNull(lifeEngine, "lifeEngine cannot be null");
        this.adapter = Objects.requireNonNull(adapter, "adapter cannot be null");
    }

    /**
     * Registers the /life command hierarchy into the given dispatcher.
     *
     * @param dispatcher Brigadier command dispatcher
     */
    public void register(@NotNull CommandDispatcher<S> dispatcher) {
        LiteralArgumentBuilder<S> root = LiteralArgumentBuilder.<S>literal("life")
                .executes(ctx -> executeInfo(ctx.getSource()));

        // /life help
        root.then(LiteralArgumentBuilder.<S>literal("help")
                .executes(ctx -> executeHelp(ctx.getSource())));

        // /life gift <player>
        root.then(LiteralArgumentBuilder.<S>literal("gift")
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<S, String>argument("target", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            String self = adapter.getName(ctx.getSource());
                            for (String name : adapter.getOnlinePlayerNames()) {
                                if (!name.equalsIgnoreCase(self)) {
                                    builder.suggest(name);
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> executeGift(ctx.getSource(), StringArgumentType.getString(ctx, "target")))));

        // /life accept
        root.then(LiteralArgumentBuilder.<S>literal("accept")
                .executes(ctx -> executeAccept(ctx.getSource())));

        // /life reload (op / permission level 4)
        root.then(LiteralArgumentBuilder.<S>literal("reload")
                .requires(adminPredicate())
                .executes(ctx -> executeReload(ctx.getSource())));

        // /life set <player> <1-3> (op / permission level 4)
        root.then(LiteralArgumentBuilder.<S>literal("set")
                .requires(adminPredicate())
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<S, String>argument("target", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (String name : adapter.getOnlinePlayerNames()) {
                                builder.suggest(name);
                            }
                            return builder.buildFuture();
                        })
                        .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<S, Integer>argument("lives", IntegerArgumentType.integer(1, 3))
                                .executes(ctx -> executeSetLives(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "target"),
                                        IntegerArgumentType.getInteger(ctx, "lives")
                                )))));

        // /life pardon <player> (op / permission level 4)
        root.then(LiteralArgumentBuilder.<S>literal("pardon")
                .requires(adminPredicate())
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<S, String>argument("target", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (String name : adapter.getOnlinePlayerNames()) {
                                builder.suggest(name);
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> executePardon(ctx.getSource(), StringArgumentType.getString(ctx, "target")))));

        // /life reset <player> (op / permission level 4)
        root.then(LiteralArgumentBuilder.<S>literal("reset")
                .requires(adminPredicate())
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<S, String>argument("target", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (String name : adapter.getOnlinePlayerNames()) {
                                builder.suggest(name);
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> executeReset(ctx.getSource(), StringArgumentType.getString(ctx, "target")))));

        // /life event pvp-override <true|false> [minutes]
        root.then(LiteralArgumentBuilder.<S>literal("event")
                .requires(adminPredicate())
                .then(LiteralArgumentBuilder.<S>literal("pvp-override")
                        .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<S, Boolean>argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> executeEventPvpOverride(
                                        ctx.getSource(),
                                        BoolArgumentType.getBool(ctx, "enabled"),
                                        0
                                ))
                                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<S, Integer>argument("minutes", IntegerArgumentType.integer(1, 1440))
                                        .executes(ctx -> executeEventPvpOverride(
                                                ctx.getSource(),
                                                BoolArgumentType.getBool(ctx, "enabled"),
                                                IntegerArgumentType.getInteger(ctx, "minutes")
                                        ))))));

        // /life arena ...
        LiteralArgumentBuilder<S> arenaSub = LiteralArgumentBuilder.<S>literal("arena")
                .requires(adminPredicate());

        arenaSub.then(LiteralArgumentBuilder.<S>literal("list")
                .executes(ctx -> executeArenaList(ctx.getSource())));

        arenaSub.then(LiteralArgumentBuilder.<S>literal("remove")
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<S, String>argument("name", StringArgumentType.word())
                        .executes(ctx -> executeArenaRemove(ctx.getSource(), StringArgumentType.getString(ctx, "name")))));

        arenaSub.then(LiteralArgumentBuilder.<S>literal("enable")
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<S, String>argument("name", StringArgumentType.word())
                        .executes(ctx -> executeArenaSetEnabled(ctx.getSource(), StringArgumentType.getString(ctx, "name"), true))));

        arenaSub.then(LiteralArgumentBuilder.<S>literal("disable")
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<S, String>argument("name", StringArgumentType.word())
                        .executes(ctx -> executeArenaSetEnabled(ctx.getSource(), StringArgumentType.getString(ctx, "name"), false))));

        // /life arena create <name> [x1 y1 z1 x2 y2 z2]
        var createNameArg = com.mojang.brigadier.builder.RequiredArgumentBuilder.<S, String>argument("name", StringArgumentType.word())
                .executes(ctx -> executeArenaCreateAuto(ctx.getSource(), StringArgumentType.getString(ctx, "name")));

        var z2Arg = com.mojang.brigadier.builder.RequiredArgumentBuilder.<S, Double>argument("z2", DoubleArgumentType.doubleArg())
                .executes(ctx -> executeArenaCreateExplicit(
                        ctx.getSource(),
                        StringArgumentType.getString(ctx, "name"),
                        DoubleArgumentType.getDouble(ctx, "x1"),
                        DoubleArgumentType.getDouble(ctx, "y1"),
                        DoubleArgumentType.getDouble(ctx, "z1"),
                        DoubleArgumentType.getDouble(ctx, "x2"),
                        DoubleArgumentType.getDouble(ctx, "y2"),
                        DoubleArgumentType.getDouble(ctx, "z2")
                ));

        var y2Arg = com.mojang.brigadier.builder.RequiredArgumentBuilder.<S, Double>argument("y2", DoubleArgumentType.doubleArg()).then(z2Arg);
        var x2Arg = com.mojang.brigadier.builder.RequiredArgumentBuilder.<S, Double>argument("x2", DoubleArgumentType.doubleArg()).then(y2Arg);
        var z1Arg = com.mojang.brigadier.builder.RequiredArgumentBuilder.<S, Double>argument("z1", DoubleArgumentType.doubleArg()).then(x2Arg);
        var y1Arg = com.mojang.brigadier.builder.RequiredArgumentBuilder.<S, Double>argument("y1", DoubleArgumentType.doubleArg()).then(z1Arg);
        var x1Arg = com.mojang.brigadier.builder.RequiredArgumentBuilder.<S, Double>argument("x1", DoubleArgumentType.doubleArg()).then(y1Arg);

        createNameArg.then(x1Arg);
        arenaSub.then(LiteralArgumentBuilder.<S>literal("create").then(createNameArg));

        root.then(arenaSub);
        dispatcher.register(root);
    }

    private Predicate<S> adminPredicate() {
        return src -> adapter.hasPermission(src, 4);
    }

    private int executeInfo(S source) {
        if (!adapter.isPlayer(source)) {
            adapter.sendFeedback(source, server.getLifeConfig().formatMessage("command.players-only"));
            return 0;
        }

        UUID uuid = adapter.getUuid(source);
        LifeConfig config = server.getLifeConfig();
        LifeProfile profile = lifeEngine.getProfileCache().getCachedProfile(uuid).orElse(null);

        int lives = profile != null ? profile.getLives() : config.getMaxLives();
        String colorHex = config.getColorHex(lives);
        String hearts = FormatUtils.formatHearts(lives, config.getMaxLives(), colorHex);

        Instant now = Instant.now();
        boolean isAfk = lifeEngine.getRegenerationService().isAfk(uuid, now, config.getAfkSeconds());

        // Header
        adapter.sendFeedback(source, config.formatMessage("info.header", Map.of(
                "color", "<color:" + colorHex + ">",
                "lives", String.valueOf(lives),
                "max", String.valueOf(config.getMaxLives())
        )));

        // Lives
        adapter.sendFeedback(source, config.formatMessage("info.lives", Map.of(
                "hearts", hearts,
                "lives", String.valueOf(lives),
                "max", String.valueOf(config.getMaxLives()),
                "color", "<color:" + colorHex + ">"
        )));

        // Next life timer
        if (lives >= config.getMaxLives()) {
            adapter.sendFeedback(source, config.formatMessage("info.full-lives"));
        } else {
            long currentSec = profile != null ? profile.getOnlineSecondsSinceLastLoss() : 0L;
            long thresholdSec = (profile != null && profile.isGiftRegenActive())
                    ? config.getGiftRegenSecondsPerLife()
                    : config.getRegenSecondsPerLife();
            long remainingSec = Math.max(0L, thresholdSec - currentSec);
            String remainingTime = config.formatDuration(Duration.ofSeconds(remainingSec));

            adapter.sendFeedback(source, config.formatMessage("info.next-life", "time", remainingTime));
        }

        // Mode line
        boolean giftRegen = profile != null && profile.isGiftRegenActive();
        if (giftRegen) {
            String giftInterval = config.formatDuration(Duration.ofSeconds(config.getGiftRegenSecondsPerLife()));
            adapter.sendFeedback(source, config.formatMessage("info.mode-gift", "time", giftInterval));
        } else {
            String normalInterval = config.formatDuration(Duration.ofSeconds(config.getRegenSecondsPerLife()));
            adapter.sendFeedback(source, config.formatMessage("info.mode-normal", "time", normalInterval));
        }

        // AFK status (only if AFK)
        if (isAfk) {
            adapter.sendFeedback(source, config.formatMessage("info.afk-paused"));
        }

        // Footer
        adapter.sendFeedback(source, config.formatMessage("info.footer"));
        return 1;
    }

    private int executeHelp(S source) {
        LifeConfig config = server.getLifeConfig();
        adapter.sendFeedback(source, config.formatMessage("help.header"));
        adapter.sendFeedback(source, config.formatMessage("help.info"));
        adapter.sendFeedback(source, config.formatMessage("help.gift"));
        adapter.sendFeedback(source, config.formatMessage("help.accept"));
        if (adapter.hasPermission(source, 4)) {
            adapter.sendFeedback(source, config.formatMessage("help.admin-header"));
            adapter.sendFeedback(source, config.formatMessage("help.pardon"));
            adapter.sendFeedback(source, config.formatMessage("help.reset", "max", String.valueOf(config.getMaxLives())));
            adapter.sendFeedback(source, config.formatMessage("help.set"));
            adapter.sendFeedback(source, config.formatMessage("help.reload"));
            adapter.sendFeedback(source, config.formatMessage("help.event"));
            adapter.sendFeedback(source, config.formatMessage("help.arena"));
        }
        adapter.sendFeedback(source, config.formatMessage("help.footer"));
        return 1;
    }

    /**
     * Resolves a player name to a UUID: online players first, then the last known name stored in the database,
     * so that admin commands also reach offline and banned players.
     */
    private CompletableFuture<Optional<UUID>> resolvePlayerUuid(@NotNull String name) {
        UUID online = adapter.getPlayerUuidByName(name);
        if (online != null) {
            return CompletableFuture.completedFuture(Optional.of(online));
        }
        return lifeEngine.getProfileCache().findUuidByLastName(name);
    }

    private int executePardon(S source, String targetName) {
        LifeConfig config = server.getLifeConfig();
        resolvePlayerUuid(targetName).thenAccept(optUuid -> {
            if (optUuid.isEmpty()) {
                adapter.sendFeedback(source, config.formatMessage("admin.pardon-player-not-found", "player", targetName));
                return;
            }
            UUID targetUuid = optUuid.get();
            lifeEngine.pardon(targetUuid, Instant.now()).thenAccept(result -> {
                switch (result.status()) {
                    case SUCCESS -> {
                        adapter.sendFeedback(source, config.formatMessage(
                                "admin.pardon-success",
                                Map.of("player", targetName, "lives", "1")
                        ));
                        server.getBridge().updateVisuals(targetUuid, 1);
                    }
                    case NOT_BANNED -> adapter.sendFeedback(source, config.formatMessage(
                            "admin.pardon-not-banned", "player", targetName));
                    case NOT_FOUND -> adapter.sendFeedback(source, config.formatMessage(
                            "admin.pardon-player-not-found", "player", targetName));
                }
            });
        });
        return 1;
    }

    private int executeReset(S source, String targetName) {
        LifeConfig config = server.getLifeConfig();
        resolvePlayerUuid(targetName).thenAccept(optUuid -> {
            if (optUuid.isEmpty()) {
                adapter.sendFeedback(source, config.formatMessage("admin.reset-player-not-found", "player", targetName));
                return;
            }
            UUID targetUuid = optUuid.get();
            lifeEngine.reset(targetUuid, config).thenAccept(result -> {
                if (result.status() == LifeEngine.ResetStatus.SUCCESS) {
                    adapter.sendFeedback(source, config.formatMessage(
                            "admin.reset-success",
                            Map.of(
                                    "player", targetName,
                                    "lives", String.valueOf(config.getMaxLives()),
                                    "max", String.valueOf(config.getMaxLives())
                            )
                    ));
                    server.getBridge().updateVisuals(targetUuid, config.getMaxLives());
                } else {
                    adapter.sendFeedback(source, config.formatMessage(
                            "admin.reset-player-not-found", "player", targetName));
                }
            });
        });
        return 1;
    }

    private int executeGift(S source, String targetName) {
        LifeConfig config = server.getLifeConfig();
        if (!adapter.isPlayer(source)) {
            adapter.sendFeedback(source, config.formatMessage("command.players-only"));
            return 0;
        }

        UUID donorUuid = adapter.getUuid(source);
        UUID recipientUuid = adapter.getPlayerUuidByName(targetName);
        if (recipientUuid == null) {
            adapter.sendFeedback(source, config.formatMessage("gift.player-offline", "player", targetName));
            return 0;
        }

        LifeProfile donorProfile = lifeEngine.getProfileCache().getCachedProfile(donorUuid).orElse(null);
        LifeProfile recipientProfile = lifeEngine.getProfileCache().getCachedProfile(recipientUuid).orElse(null);

        if (donorProfile == null || recipientProfile == null) {
            adapter.sendFeedback(source, config.formatMessage("command.profile-not-loaded"));
            return 0;
        }

        GiftService.InitiateResult result = lifeEngine.getGiftService().initiateGift(
                donorUuid, donorProfile, recipientUuid, recipientProfile, Instant.now(), config
        );

        if (result.status() != GiftService.InitiateStatus.SUCCESS) {
            adapter.sendFeedback(source, result.errorMessage() != null ? result.errorMessage() : config.formatMessage("gift.failed"));
            return 0;
        }

        String timeout = config.formatDuration(Duration.ofSeconds(config.getGiftRequestTimeoutSeconds()));
        adapter.sendFeedback(source, config.formatMessage("gift.sent", Map.of("player", targetName, "time", timeout)));
        server.getBridge().sendMessage(
                recipientUuid,
                config.formatMessage("gift.received", Map.of("player", adapter.getName(source), "time", timeout)),
                false
        );
        return 1;
    }

    private int executeAccept(S source) {
        LifeConfig config = server.getLifeConfig();
        if (!adapter.isPlayer(source)) {
            adapter.sendFeedback(source, config.formatMessage("command.players-only"));
            return 0;
        }

        UUID recipientUuid = adapter.getUuid(source);
        LifeProfile recipientProfile = lifeEngine.getProfileCache().getCachedProfile(recipientUuid).orElse(null);

        if (recipientProfile == null) {
            adapter.sendFeedback(source, config.formatMessage("command.profile-not-loaded"));
            return 0;
        }

        GiftService.AcceptResult result = lifeEngine.getGiftService().acceptGift(
                recipientUuid,
                recipientProfile,
                () -> {
                    var pending = lifeEngine.getGiftService().getPendingRequest(recipientUuid, Instant.now());
                    if (pending == null) return null;
                    return lifeEngine.getProfileCache().getCachedProfile(pending.donorUuid()).orElse(null);
                },
                Instant.now(),
                config
        );

        if (result.status() != GiftService.AcceptStatus.SUCCESS) {
            adapter.sendFeedback(source, result.errorMessage() != null ? result.errorMessage() : config.formatMessage("gift.failed"));
            return 0;
        }

        UUID donorUuid = result.donorUuid();
        String donorName = donorUuid != null
                ? lifeEngine.getProfileCache().getCachedProfile(donorUuid).map(LifeProfile::getLastName).orElse(null)
                : null;
        adapter.sendFeedback(source, config.formatMessage("gift.accepted-recipient", Map.of(
                "player", donorName != null ? donorName : "?",
                "lives", String.valueOf(result.recipientNewLives())
        )));
        server.getBridge().updateVisuals(recipientUuid, result.recipientNewLives());
        lifeEngine.getProfileCache().markDirty(recipientUuid);

        if (donorUuid != null) {
            server.getBridge().sendMessage(
                    donorUuid,
                    config.formatMessage("gift.accepted-donor", "player", adapter.getName(source)),
                    false
            );
            server.getBridge().updateVisuals(donorUuid, result.donorNewLives());
            lifeEngine.getProfileCache().markDirty(donorUuid);
        }
        return 1;
    }

    private int executeReload(S source) {
        server.reloadServerConfig();
        adapter.sendFeedback(source, server.getLifeConfig().formatMessage("admin.reload-success"));
        return 1;
    }

    private int executeSetLives(S source, String targetName, int lives) {
        LifeConfig config = server.getLifeConfig();
        resolvePlayerUuid(targetName).thenAccept(optUuid -> {
            if (optUuid.isEmpty()) {
                adapter.sendFeedback(source, config.formatMessage("admin.set-player-not-found", "player", targetName));
                return;
            }
            UUID targetUuid = optUuid.get();
            lifeEngine.adminSetLives(targetUuid, lives, config).thenRun(() -> adapter.sendFeedback(
                    source,
                    config.formatMessage("admin.set-lives", Map.of("player", targetName, "lives", String.valueOf(lives)))
            ));
        });
        return 1;
    }

    private int executeEventPvpOverride(S source, boolean enabled, int minutes) {
        Instant expiresAt = (enabled && minutes > 0) ? Instant.now().plusSeconds(minutes * 60L) : null;
        lifeEngine.getArenaService().setGlobalPvpOverride(enabled, expiresAt);

        LifeConfig config = server.getLifeConfig();
        String status = config.formatMessage(enabled ? "event.status-enabled" : "event.status-disabled");
        server.getBridge().broadcast(config.formatMessage("event.pvp-override", "status", status));
        return 1;
    }

    private int executeArenaList(S source) {
        LifeConfig config = server.getLifeConfig();
        Collection<Arena> arenas = lifeEngine.getArenaService().getAllArenas();
        adapter.sendFeedback(source, config.formatMessage("arena.list-header", "count", String.valueOf(arenas.size())));
        for (Arena arena : arenas) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("name", arena.getName());
            placeholders.put("world", arena.getWorld());
            placeholders.put("x1", String.format("%.0f", arena.getMinX()));
            placeholders.put("y1", String.format("%.0f", arena.getMinY()));
            placeholders.put("z1", String.format("%.0f", arena.getMinZ()));
            placeholders.put("x2", String.format("%.0f", arena.getMaxX()));
            placeholders.put("y2", String.format("%.0f", arena.getMaxY()));
            placeholders.put("z2", String.format("%.0f", arena.getMaxZ()));
            placeholders.put("status", config.formatMessage(arena.isEnabled() ? "arena.status-enabled" : "arena.status-disabled"));
            adapter.sendFeedback(source, config.formatMessage("arena.list-entry", placeholders));
        }
        return 1;
    }

    private int executeArenaRemove(S source, String name) {
        LifeConfig config = server.getLifeConfig();
        lifeEngine.getArenaService().removeArena(name).thenAccept(success ->
                adapter.sendFeedback(source, config.formatMessage(success ? "arena.removed" : "arena.not-found", "name", name)));
        return 1;
    }

    private int executeArenaSetEnabled(S source, String name, boolean enabled) {
        LifeConfig config = server.getLifeConfig();
        lifeEngine.getArenaService().setArenaEnabled(name, enabled).thenAccept(success -> {
            if (success) {
                String status = config.formatMessage(enabled ? "arena.status-enabled" : "arena.status-disabled");
                adapter.sendFeedback(source, config.formatMessage("arena.status-updated", Map.of("name", name, "status", status)));
            } else {
                adapter.sendFeedback(source, config.formatMessage("arena.not-found", "name", name));
            }
        });
        return 1;
    }

    private int executeArenaCreateAuto(S source, String name) {
        if (!adapter.isPlayer(source)) {
            adapter.sendFeedback(source, server.getLifeConfig().formatMessage("arena.players-only"));
            return 0;
        }
        double x = adapter.getX(source);
        double y = adapter.getY(source);
        double z = adapter.getZ(source);
        return executeArenaCreateExplicit(source, name, x - 25, y - 10, z - 25, x + 25, y + 30, z + 25);
    }

    private int executeArenaCreateExplicit(
            S source, String name,
            double x1, double y1, double z1,
            double x2, double y2, double z2
    ) {
        String world = adapter.getWorldName(source);
        lifeEngine.getArenaService().createArena(name, world, x1, y1, z1, x2, y2, z2).thenAccept(arena -> {
            adapter.sendFeedback(source, server.getLifeConfig().formatMessage("arena.created", "name", name));
        });
        return 1;
    }
}
