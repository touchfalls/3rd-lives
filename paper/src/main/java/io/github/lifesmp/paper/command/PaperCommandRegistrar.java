// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.paper.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.github.lifesmp.core.config.LifeConfig;
import io.github.lifesmp.core.domain.model.Arena;
import io.github.lifesmp.core.domain.model.LifeProfile;
import io.github.lifesmp.core.domain.service.AdminService;
import io.github.lifesmp.core.domain.service.GiftService;
import io.github.lifesmp.core.domain.service.LifeEngine;
import io.github.lifesmp.core.util.FormatUtils;
import io.github.lifesmp.paper.LifeSmpPaperPlugin;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Registers modern Brigadier commands for Life SMP via Paper LifecycleEvents.COMMANDS.
 * Implements /life, /life gift, /life accept, /life reload, /life set,
 * /life pardon, /life reset, /life event, /life arena, and /life help.
 */
public final class PaperCommandRegistrar {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([a-z0-9_-]+)}");

    private final LifeSmpPaperPlugin plugin;
    private final LifeEngine lifeEngine;

    public PaperCommandRegistrar(
            @NotNull LifeSmpPaperPlugin plugin,
            @NotNull LifeEngine lifeEngine
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.lifeEngine = Objects.requireNonNull(lifeEngine, "lifeEngine cannot be null");
    }

    private Component render(@NotNull String key, @NotNull TagResolver... resolvers) {
        return render(plugin.getLifeConfig(), key, resolvers);
    }

    private Component render(@NotNull LifeConfig config, @NotNull String key, @NotNull TagResolver... resolvers) {
        return renderTemplate(config, config.getRawMessage(key), resolvers);
    }

    /**
     * Renders a MiniMessage template. Message files write placeholders as {@code {name}};
     * they are rewritten to {@code <name>} so MiniMessage placeholder resolvers can fill them.
     */
    private Component renderTemplate(@NotNull LifeConfig config, @NotNull String rawTemplate, @NotNull TagResolver... resolvers) {
        String template = PLACEHOLDER_PATTERN.matcher(rawTemplate).replaceAll("<$1>");
        TagResolver prefixResolver = Placeholder.parsed("prefix", config.getPrefix());
        TagResolver combined = TagResolver.builder()
                .resolver(prefixResolver)
                .resolvers(resolvers)
                .build();
        return MiniMessage.miniMessage().deserialize(template, combined);
    }

    /**
     * Registers commands in Paper's lifecycle event manager.
     */
    public void registerCommands() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();
            LiteralCommandNode<CommandSourceStack> rootNode = buildRootNode();
            registrar.register(rootNode, "Life SMP management command", java.util.List.of("lives", "lsmp"));
        });
    }

    private LiteralCommandNode<CommandSourceStack> buildRootNode() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("life")
                .executes(ctx -> executeInfo(ctx.getSource()));

        // /life help
        root.then(Commands.literal("help")
                .executes(ctx -> executeHelp(ctx.getSource())));

        // /life gift <player>
        root.then(Commands.literal("gift")
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            String senderName = ctx.getSource().getSender().getName();
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                if (!p.getName().equalsIgnoreCase(senderName)) {
                                    builder.suggest(p.getName());
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> executeGift(ctx.getSource(), StringArgumentType.getString(ctx, "target")))));

        // /life accept
        root.then(Commands.literal("accept")
                .executes(ctx -> executeAccept(ctx.getSource())));

        // /life reload (lifesmp.admin)
        root.then(Commands.literal("reload")
                .requires(src -> src.getSender().hasPermission("lifesmp.admin"))
                .executes(ctx -> executeReload(ctx.getSource())));

        // /life set <player> <1-3> (lifesmp.admin)
        root.then(Commands.literal("set")
                .requires(src -> src.getSender().hasPermission("lifesmp.admin"))
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                builder.suggest(p.getName());
                            }
                            return builder.buildFuture();
                        })
                        .then(Commands.argument("lives", IntegerArgumentType.integer(1, 3))
                                .executes(ctx -> executeSetLives(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "target"),
                                        IntegerArgumentType.getInteger(ctx, "lives")
                                )))));

        // /life pardon <player> (lifesmp.admin)
        root.then(Commands.literal("pardon")
                .requires(src -> src.getSender().hasPermission("lifesmp.admin"))
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            long nowSec = Instant.now().getEpochSecond();
                            return lifeEngine.getProfileCache().getRepository().getBannedPlayerUuids(nowSec)
                                    .thenApply(bannedUuids -> {
                                        for (UUID u : bannedUuids) {
                                            OfflinePlayer op = Bukkit.getOfflinePlayer(u);
                                            if (op.getName() != null) {
                                                builder.suggest(op.getName());
                                            }
                                        }
                                        return builder.build();
                                    });
                        })
                        .executes(ctx -> executePardon(ctx.getSource(), StringArgumentType.getString(ctx, "target")))));

        // /life reset <player> (lifesmp.admin)
        root.then(Commands.literal("reset")
                .requires(src -> src.getSender().hasPermission("lifesmp.admin"))
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                builder.suggest(p.getName());
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> executeReset(ctx.getSource(), StringArgumentType.getString(ctx, "target")))));

        // /life event pvp-override <true|false> [minutes] (lifesmp.admin)
        root.then(Commands.literal("event")
                .requires(src -> src.getSender().hasPermission("lifesmp.admin"))
                .then(Commands.literal("pvp-override")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> executeEventPvpOverride(
                                        ctx.getSource(),
                                        BoolArgumentType.getBool(ctx, "enabled"),
                                        0
                                ))
                                .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 1440))
                                        .executes(ctx -> executeEventPvpOverride(
                                                ctx.getSource(),
                                                BoolArgumentType.getBool(ctx, "enabled"),
                                                IntegerArgumentType.getInteger(ctx, "minutes")
                                        ))))));

        // /life arena ... (lifesmp.admin)
        LiteralArgumentBuilder<CommandSourceStack> arenaSub = Commands.literal("arena")
                .requires(src -> src.getSender().hasPermission("lifesmp.admin"));

        // /life arena list
        arenaSub.then(Commands.literal("list")
                .executes(ctx -> executeArenaList(ctx.getSource())));

        // /life arena remove <name>
        arenaSub.then(Commands.literal("remove")
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (Arena a : lifeEngine.getArenaService().getAllArenas()) {
                                builder.suggest(a.getName());
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> executeArenaRemove(ctx.getSource(), StringArgumentType.getString(ctx, "name")))));

        // /life arena enable <name>
        arenaSub.then(Commands.literal("enable")
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (Arena a : lifeEngine.getArenaService().getAllArenas()) {
                                builder.suggest(a.getName());
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> executeArenaSetEnabled(ctx.getSource(), StringArgumentType.getString(ctx, "name"), true))));

        // /life arena disable <name>
        arenaSub.then(Commands.literal("disable")
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (Arena a : lifeEngine.getArenaService().getAllArenas()) {
                                builder.suggest(a.getName());
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> executeArenaSetEnabled(ctx.getSource(), StringArgumentType.getString(ctx, "name"), false))));

        // /life arena create <name> [x1 y1 z1 x2 y2 z2]
        var createNameArg = Commands.argument("name", StringArgumentType.word())
                .executes(ctx -> executeArenaCreateAuto(ctx.getSource(), StringArgumentType.getString(ctx, "name")));

        var z2Arg = Commands.argument("z2", DoubleArgumentType.doubleArg())
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

        var y2Arg = Commands.argument("y2", DoubleArgumentType.doubleArg()).then(z2Arg);
        var x2Arg = Commands.argument("x2", DoubleArgumentType.doubleArg()).then(y2Arg);
        var z1Arg = Commands.argument("z1", DoubleArgumentType.doubleArg()).then(x2Arg);
        var y1Arg = Commands.argument("y1", DoubleArgumentType.doubleArg()).then(z1Arg);
        var x1Arg = Commands.argument("x1", DoubleArgumentType.doubleArg()).then(y1Arg);

        createNameArg.then(x1Arg);
        arenaSub.then(Commands.literal("create").then(createNameArg));

        root.then(arenaSub);
        return root.build();
    }

    private int executeInfo(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(render("command.players-only"));
            return 0;
        }

        LifeConfig config = plugin.getLifeConfig();
        UUID uuid = player.getUniqueId();
        LifeProfile profile = lifeEngine.getProfileCache().getCachedProfile(uuid).orElse(null);
        int lives = profile != null ? profile.getLives() : config.getMaxLives();
        String colorHex = config.getColorHex(lives);
        String hearts = FormatUtils.formatHearts(lives, config.getMaxLives(), colorHex);

        Instant now = Instant.now();
        boolean isAfk = lifeEngine.getRegenerationService().isAfk(uuid, now, config.getAfkSeconds());

        // Header
        player.sendMessage(render(config, "info.header",
                Placeholder.parsed("color", "<color:" + colorHex + ">"),
                Placeholder.unparsed("lives", String.valueOf(lives)),
                Placeholder.unparsed("max", String.valueOf(config.getMaxLives()))
        ));

        // Lives
        player.sendMessage(render(config, "info.lives",
                Placeholder.parsed("hearts", hearts),
                Placeholder.unparsed("lives", String.valueOf(lives)),
                Placeholder.unparsed("max", String.valueOf(config.getMaxLives())),
                Placeholder.parsed("color", "<color:" + colorHex + ">")
        ));

        // Next life timer
        if (lives >= config.getMaxLives()) {
            player.sendMessage(render(config, "info.full-lives"));
        } else {
            long currentSec = profile != null ? profile.getOnlineSecondsSinceLastLoss() : 0L;
            long thresholdSec = (profile != null && profile.isGiftRegenActive())
                    ? config.getGiftRegenSecondsPerLife()
                    : config.getRegenSecondsPerLife();
            long remainingSec = Math.max(0L, thresholdSec - currentSec);
            String remainingTime = config.formatDuration(Duration.ofSeconds(remainingSec));

            player.sendMessage(render(config, "info.next-life",
                    Placeholder.unparsed("time", remainingTime)
            ));
        }

        // Mode line
        boolean giftRegen = profile != null && profile.isGiftRegenActive();
        if (giftRegen) {
            String giftInterval = config.formatDuration(Duration.ofSeconds(config.getGiftRegenSecondsPerLife()));
            player.sendMessage(render(config, "info.mode-gift",
                    Placeholder.unparsed("time", giftInterval)
            ));
        } else {
            String normalInterval = config.formatDuration(Duration.ofSeconds(config.getRegenSecondsPerLife()));
            player.sendMessage(render(config, "info.mode-normal",
                    Placeholder.unparsed("time", normalInterval)
            ));
        }

        // AFK status (only if AFK)
        if (isAfk) {
            player.sendMessage(render(config, "info.afk-paused"));
        }

        // Footer
        player.sendMessage(render(config, "info.footer"));
        return 1;
    }

    private int executeHelp(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        LifeConfig config = plugin.getLifeConfig();
        TagResolver max = Placeholder.unparsed("max", String.valueOf(config.getMaxLives()));

        sender.sendMessage(render(config, "help.header"));
        sender.sendMessage(render(config, "help.info"));
        sender.sendMessage(render(config, "help.gift"));
        sender.sendMessage(render(config, "help.accept"));

        if (sender.hasPermission("lifesmp.admin")) {
            sender.sendMessage(render(config, "help.admin-header"));
            sender.sendMessage(render(config, "help.pardon"));
            sender.sendMessage(render(config, "help.reset", max));
            sender.sendMessage(render(config, "help.set"));
            sender.sendMessage(render(config, "help.reload"));
            sender.sendMessage(render(config, "help.event"));
            sender.sendMessage(render(config, "help.arena"));
        }

        sender.sendMessage(render(config, "help.footer"));
        return 1;
    }

    private int executeGift(CommandSourceStack source, String targetName) {
        CommandSender sender = source.getSender();
        if (!(sender instanceof Player donor)) {
            sender.sendMessage(render("command.players-only"));
            return 0;
        }

        Player recipient = Bukkit.getPlayerExact(targetName);
        if (recipient == null || !recipient.isOnline()) {
            donor.sendMessage(render("gift.player-offline", Placeholder.unparsed("player", targetName)));
            return 0;
        }

        LifeConfig config = plugin.getLifeConfig();
        LifeProfile donorProfile = lifeEngine.getProfileCache().getCachedProfile(donor.getUniqueId()).orElse(null);
        LifeProfile recipientProfile = lifeEngine.getProfileCache().getCachedProfile(recipient.getUniqueId()).orElse(null);

        if (donorProfile == null || recipientProfile == null) {
            donor.sendMessage(render("command.profile-not-loaded"));
            return 0;
        }

        GiftService.InitiateResult result = lifeEngine.getGiftService().initiateGift(
                donor.getUniqueId(),
                donorProfile,
                recipient.getUniqueId(),
                recipientProfile,
                Instant.now(),
                config
        );

        if (result.status() != GiftService.InitiateStatus.SUCCESS) {
            donor.sendMessage(renderTemplate(config,
                    result.errorMessage() != null ? result.errorMessage() : config.getRawMessage("gift.failed")
            ));
            return 0;
        }

        String timeoutFormatted = config.formatDuration(Duration.ofSeconds(config.getGiftRequestTimeoutSeconds()));

        donor.sendMessage(render(
                config,
                "gift.sent",
                Placeholder.unparsed("player", recipient.getName()),
                Placeholder.unparsed("time", timeoutFormatted)
        ));

        recipient.sendMessage(render(
                config,
                "gift.received",
                Placeholder.unparsed("player", donor.getName()),
                Placeholder.unparsed("time", timeoutFormatted)
        ));

        return 1;
    }

    private int executeAccept(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        if (!(sender instanceof Player recipient)) {
            sender.sendMessage(render("command.players-only"));
            return 0;
        }

        LifeConfig config = plugin.getLifeConfig();
        LifeProfile recipientProfile = lifeEngine.getProfileCache().getCachedProfile(recipient.getUniqueId()).orElse(null);
        if (recipientProfile == null) {
            recipient.sendMessage(render(config, "command.profile-not-loaded"));
            return 0;
        }

        GiftService.AcceptResult result = lifeEngine.getGiftService().acceptGift(
                recipient.getUniqueId(),
                recipientProfile,
                () -> {
                    var pending = lifeEngine.getGiftService().getPendingRequest(recipient.getUniqueId(), Instant.now());
                    if (pending == null) {
                        return null;
                    }
                    Player donorPlayer = Bukkit.getPlayer(pending.donorUuid());
                    if (donorPlayer == null || !donorPlayer.isOnline()) {
                        return null;
                    }
                    return lifeEngine.getProfileCache().getCachedProfile(pending.donorUuid()).orElse(null);
                },
                Instant.now(),
                config
        );

        if (result.status() != GiftService.AcceptStatus.SUCCESS) {
            recipient.sendMessage(renderTemplate(config,
                    result.errorMessage() != null ? result.errorMessage() : config.getRawMessage("gift.failed")
            ));
            return 0;
        }

        UUID donorUuid = result.donorUuid();
        Player donorPlayer = donorUuid != null ? Bukkit.getPlayer(donorUuid) : null;
        String donorName = donorPlayer != null ? donorPlayer.getName() : "?";

        recipient.sendMessage(render(
                config,
                "gift.accepted-recipient",
                Placeholder.unparsed("player", donorName),
                Placeholder.unparsed("lives", String.valueOf(result.recipientNewLives()))
        ));

        if (donorPlayer != null && donorPlayer.isOnline()) {
            donorPlayer.sendMessage(render(
                    config,
                    "gift.accepted-donor",
                    Placeholder.unparsed("player", recipient.getName())
            ));
            plugin.getScoreboardManager().updatePlayerVisuals(donorPlayer, result.donorNewLives());
        }

        plugin.getScoreboardManager().updatePlayerVisuals(recipient, result.recipientNewLives());
        // Store donor and recipient in one transaction so a crash cannot keep only half of the transfer.
        List<LifeProfile> changed = new ArrayList<>(List.of(recipientProfile));
        if (donorUuid != null) {
            lifeEngine.getProfileCache().getCachedProfile(donorUuid).ifPresent(changed::add);
        }
        lifeEngine.getProfileCache().saveProfilesImmediately(changed);

        return 1;
    }

    private int executeReload(CommandSourceStack source) {
        plugin.reloadPluginConfig();
        source.getSender().sendMessage(render("admin.reload-success"));
        return 1;
    }

    private int executeSetLives(CommandSourceStack source, String targetName, int lives) {
        CommandSender sender = source.getSender();
        LifeConfig config = plugin.getLifeConfig();

        resolvePlayerUuid(targetName).thenAccept(optUuid -> {
            if (optUuid.isEmpty()) {
                sender.sendMessage(render(config, "admin.set-player-not-found", Placeholder.unparsed("player", targetName)));
                return;
            }
            UUID targetUuid = optUuid.get();
            lifeEngine.getAdminService().setLives(targetUuid, lives, config).thenRun(() -> {
                sender.sendMessage(render(
                        config,
                        "admin.set-lives",
                        Placeholder.unparsed("player", targetName),
                        Placeholder.unparsed("lives", String.valueOf(lives))
                ));
                Player onlinePlayer = Bukkit.getPlayer(targetUuid);
                if (onlinePlayer != null && onlinePlayer.isOnline()) {
                    plugin.getScoreboardManager().updatePlayerVisuals(onlinePlayer, lives);
                }
            });
        });
        return 1;
    }

    private CompletableFuture<Optional<UUID>> resolvePlayerUuid(@NotNull String name) {
        Player onlinePlayer = Bukkit.getPlayerExact(name);
        return lifeEngine.getAdminService().resolveUuid(name, onlinePlayer != null ? onlinePlayer.getUniqueId() : null);
    }

    private int executePardon(CommandSourceStack source, String targetName) {
        CommandSender sender = source.getSender();
        LifeConfig config = plugin.getLifeConfig();

        resolvePlayerUuid(targetName).thenAccept(optUuid -> {
            if (optUuid.isEmpty()) {
                sender.sendMessage(render(config, "admin.pardon-player-not-found", Placeholder.unparsed("player", targetName)));
                return;
            }
            UUID targetUuid = optUuid.get();
            lifeEngine.getAdminService().pardon(targetUuid, Instant.now()).thenAccept(result -> {
                switch (result.status()) {
                    case SUCCESS -> {
                        sender.sendMessage(render(
                                config,
                                "admin.pardon-success",
                                Placeholder.unparsed("player", targetName),
                                Placeholder.unparsed("lives", "1")
                        ));
                        Player onlinePlayer = Bukkit.getPlayer(targetUuid);
                        if (onlinePlayer != null && onlinePlayer.isOnline()) {
                            plugin.getScoreboardManager().updatePlayerVisuals(onlinePlayer, 1);
                        }
                    }
                    case NOT_BANNED -> sender.sendMessage(render(
                            config,
                            "admin.pardon-not-banned",
                            Placeholder.unparsed("player", targetName)
                    ));
                    case NOT_FOUND -> sender.sendMessage(render(
                            config,
                            "admin.pardon-player-not-found",
                            Placeholder.unparsed("player", targetName)
                    ));
                }
            });
        });
        return 1;
    }

    private int executeReset(CommandSourceStack source, String targetName) {
        CommandSender sender = source.getSender();
        LifeConfig config = plugin.getLifeConfig();

        resolvePlayerUuid(targetName).thenAccept(optUuid -> {
            if (optUuid.isEmpty()) {
                sender.sendMessage(render(config, "admin.reset-player-not-found", Placeholder.unparsed("player", targetName)));
                return;
            }
            UUID targetUuid = optUuid.get();
            lifeEngine.getAdminService().reset(targetUuid, config).thenAccept(result -> {
                if (result.status() == AdminService.ResetStatus.SUCCESS) {
                    sender.sendMessage(render(
                            config,
                            "admin.reset-success",
                            Placeholder.unparsed("player", targetName),
                            Placeholder.unparsed("lives", String.valueOf(config.getMaxLives())),
                            Placeholder.unparsed("max", String.valueOf(config.getMaxLives()))
                    ));
                    Player onlinePlayer = Bukkit.getPlayer(targetUuid);
                    if (onlinePlayer != null && onlinePlayer.isOnline()) {
                        plugin.getScoreboardManager().updatePlayerVisuals(onlinePlayer, config.getMaxLives());
                    }
                } else {
                    sender.sendMessage(render(
                            config,
                            "admin.reset-player-not-found",
                            Placeholder.unparsed("player", targetName)
                    ));
                }
            });
        });
        return 1;
    }

    private int executeEventPvpOverride(CommandSourceStack source, boolean enabled, int minutes) {
        Instant expiresAt = (enabled && minutes > 0) ? Instant.now().plusSeconds(minutes * 60L) : null;
        lifeEngine.getArenaService().setGlobalPvpOverride(enabled, expiresAt);

        String status = plugin.getLifeConfig().getRawMessage(enabled ? "event.status-enabled" : "event.status-disabled");
        Bukkit.broadcast(render(
                "event.pvp-override",
                Placeholder.parsed("status", status)
        ));
        return 1;
    }

    private int executeArenaList(CommandSourceStack source) {
        Collection<Arena> arenas = lifeEngine.getArenaService().getAllArenas();
        source.getSender().sendMessage(render(
                "arena.list-header",
                Placeholder.unparsed("count", String.valueOf(arenas.size()))
        ));

        for (Arena arena : arenas) {
            String status = plugin.getLifeConfig().getRawMessage(arena.isEnabled() ? "arena.status-enabled" : "arena.status-disabled");
            source.getSender().sendMessage(render(
                    "arena.list-entry",
                    Placeholder.unparsed("name", arena.getName()),
                    Placeholder.unparsed("world", arena.getWorld()),
                    Placeholder.unparsed("x1", String.format("%.0f", arena.getMinX())),
                    Placeholder.unparsed("y1", String.format("%.0f", arena.getMinY())),
                    Placeholder.unparsed("z1", String.format("%.0f", arena.getMinZ())),
                    Placeholder.unparsed("x2", String.format("%.0f", arena.getMaxX())),
                    Placeholder.unparsed("y2", String.format("%.0f", arena.getMaxY())),
                    Placeholder.unparsed("z2", String.format("%.0f", arena.getMaxZ())),
                    Placeholder.parsed("status", status)
            ));
        }
        return 1;
    }

    private int executeArenaRemove(CommandSourceStack source, String name) {
        lifeEngine.getArenaService().removeArena(name).thenAccept(success -> {
            if (success) {
                source.getSender().sendMessage(render(
                        "arena.removed",
                        Placeholder.unparsed("name", name)
                ));
            } else {
                source.getSender().sendMessage(render(
                        "arena.not-found",
                        Placeholder.unparsed("name", name)
                ));
            }
        });
        return 1;
    }

    private int executeArenaSetEnabled(CommandSourceStack source, String name, boolean enabled) {
        lifeEngine.getArenaService().setArenaEnabled(name, enabled).thenAccept(success -> {
            if (success) {
                source.getSender().sendMessage(render(
                        "arena.status-updated",
                        Placeholder.unparsed("name", name),
                        Placeholder.parsed("status", plugin.getLifeConfig().getRawMessage(enabled ? "arena.status-enabled" : "arena.status-disabled"))
                ));
            } else {
                source.getSender().sendMessage(render(
                        "arena.not-found",
                        Placeholder.unparsed("name", name)
                ));
            }
        });
        return 1;
    }

    private int executeArenaCreateAuto(CommandSourceStack source, String name) {
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(render("arena.players-only"));
            return 0;
        }

        Location loc = player.getLocation();
        double radius = 25.0;
        return executeArenaCreateExplicit(
                source,
                name,
                loc.getX() - radius, loc.getY() - 10, loc.getZ() - radius,
                loc.getX() + radius, loc.getY() + 30, loc.getZ() + radius
        );
    }

    private int executeArenaCreateExplicit(
            CommandSourceStack source,
            String name,
            double x1, double y1, double z1,
            double x2, double y2, double z2
    ) {
        String worldName = source.getLocation().getWorld().getName();
        lifeEngine.getArenaService().createArena(name, worldName, x1, y1, z1, x2, y2, z2).thenAccept(created ->
                source.getSender().sendMessage(render(
                        created ? "arena.created" : "arena.already-exists",
                        Placeholder.unparsed("name", name)
                )));
        return 1;
    }
}
