// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.fabric.bounty;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.lifesmp.core.config.LifeConfig;
import io.github.lifesmp.core.domain.model.Bounty;
import io.github.lifesmp.core.domain.service.BountyService;
import io.github.lifesmp.core.storage.LifeProfileCache;
import io.github.lifesmp.fabric.util.FabricMessageFormatter;
import io.github.lifesmp.fabric.util.McCompat;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** {@code /bounty set|cancel|buyout|info} and the stake chest on Fabric. */
public final class FabricBountyCommands {

    private final BountyService bounties;
    private final LifeProfileCache profiles;
    private final Supplier<LifeConfig> config;
    private final Supplier<MinecraftServer> server;

    public FabricBountyCommands(@NotNull BountyService bounties, @NotNull LifeProfileCache profiles,
                                @NotNull Supplier<LifeConfig> config, @NotNull Supplier<MinecraftServer> server) {
        this.bounties = bounties;
        this.profiles = profiles;
        this.config = config;
        this.server = server;
    }

    /** Chest the issuer fills with stake items; the bounty is placed when it closes. */
    private final class StakeMenu extends ChestMenu {
        private final UUID target;
        private final String targetName;
        private final int xp;
        private final int lives;
        private final int buyout;
        private final Container stake;
        private boolean handled;

        StakeMenu(int id, Inventory inv, Container stake, UUID target, String targetName, int xp, int lives, int buyout) {
            super(MenuType.GENERIC_9x3, id, inv, stake, 3);
            this.stake = stake;
            this.target = target;
            this.targetName = targetName;
            this.xp = xp;
            this.lives = lives;
            this.buyout = buyout;
        }

        @Override
        public void removed(@NotNull Player player) {
            super.removed(player);
            if (handled || !(player instanceof ServerPlayer issuer)) {
                return;
            }
            handled = true;
            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < stake.getContainerSize(); i++) {
                ItemStack item = stake.removeItemNoUpdate(i);
                if (!item.isEmpty()) {
                    items.add(item);
                }
            }
            byte[] blob = ItemBlobs.encode(items, issuer.registryAccess());
            BountyService.Status status = bounties.place(issuer.getUUID(), McCompat.name(issuer.getGameProfile()),
                    targetName, target, blob, xp, lives, buyout, issuer.experienceLevel, Instant.now(), config.get());
            if (status == BountyService.Status.OK) {
                issuer.giveExperienceLevels(-xp);
                return;
            }
            // Not placed: hand the items back
            items.forEach(item -> ItemBlobs.giveOrDrop(issuer, item));
            send(issuer, bounties.statusMessage(status, config.get()));
        }
    }

    public void register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
        var buyoutArg = Commands.argument("buyout", IntegerArgumentType.integer(0))
                .executes(ctx -> set(ctx.getSource(), StringArgumentType.getString(ctx, "target"),
                        IntegerArgumentType.getInteger(ctx, "xp"), IntegerArgumentType.getInteger(ctx, "lives"),
                        IntegerArgumentType.getInteger(ctx, "buyout")));
        dispatcher.register(Commands.literal("bounty")
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> FabricMessageFormatter.parse(config.get().formatMessage("bounty.usage")), false);
                    return 1;
                })
                .then(Commands.literal("set")
                        .then(Commands.argument("target", StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                    for (String name : ctx.getSource().getServer().getPlayerList().getPlayerNamesArray()) {
                                        b.suggest(name);
                                    }
                                    return b.buildFuture();
                                })
                                .then(Commands.argument("xp", IntegerArgumentType.integer(0))
                                        .executes(ctx -> set(ctx.getSource(), StringArgumentType.getString(ctx, "target"),
                                                IntegerArgumentType.getInteger(ctx, "xp"), 0, -1))
                                        .then(Commands.argument("lives", IntegerArgumentType.integer(0))
                                                .executes(ctx -> set(ctx.getSource(), StringArgumentType.getString(ctx, "target"),
                                                        IntegerArgumentType.getInteger(ctx, "xp"),
                                                        IntegerArgumentType.getInteger(ctx, "lives"), -1))
                                                .then(buyoutArg)))))
                .then(Commands.literal("cancel").executes(ctx -> cancel(ctx.getSource())))
                .then(Commands.literal("buyout").executes(ctx -> buyout(ctx.getSource())))
                .then(Commands.literal("info")
                        .then(Commands.argument("target", StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                    for (String name : ctx.getSource().getServer().getPlayerList().getPlayerNamesArray()) {
                                        b.suggest(name);
                                    }
                                    return b.buildFuture();
                                })
                                .executes(ctx -> info(ctx.getSource(), StringArgumentType.getString(ctx, "target"))))));
    }

    private void send(ServerPlayer player, String raw) {
        player.sendSystemMessage(FabricMessageFormatter.parse(raw));
    }

    private int set(CommandSourceStack source, String targetName, int xp, int lives, int buyout) {
        ServerPlayer issuer = source.getPlayer();
        if (issuer == null) {
            return 0;
        }
        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            send(issuer, config.get().formatMessage("gift.player-offline", "player", targetName));
            return 0;
        }
        int price = bounties.buyoutOrDefault(buyout, xp);
        BountyService.Status status = bounties.validatePlace(
                issuer.getUUID(), target.getUUID(), xp, lives, price, issuer.experienceLevel);
        if (status != BountyService.Status.OK) {
            send(issuer, bounties.statusMessage(status, config.get()));
            return 0;
        }
        UUID targetId = target.getUUID();
        String name = McCompat.name(target.getGameProfile());
        issuer.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new StakeMenu(id, inv, new SimpleContainer(27), targetId, name, xp, lives, price),
                FabricMessageFormatter.parse(config.get().formatMessage("bounty.menu-title", "player", name))));
        return 1;
    }

    /** Closes a stake chest so its items go back; called on disconnect and before shutdown. */
    public static void closeStakeMenu(@NotNull ServerPlayer player) {
        if (player.containerMenu instanceof StakeMenu) {
            player.doCloseContainer();
        }
    }

    private int cancel(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        BountyService.Status status = bounties.cancel(player.getUUID(), config.get());
        if (status != BountyService.Status.OK) {
            send(player, bounties.statusMessage(status, config.get()));
            return 0;
        }
        return 1;
    }

    private int buyout(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        int price = bounties.getBounty(player.getUUID()).map(Bounty::buyoutLevels).orElse(0);
        BountyService.Status status = bounties.buyout(player.getUUID(), player.experienceLevel, config.get());
        if (status != BountyService.Status.OK) {
            send(player, bounties.statusMessage(status, config.get()));
            return 0;
        }
        player.giveExperienceLevels(-price);
        return 1;
    }

    private int info(CommandSourceStack source, String name) {
        ServerPlayer online = source.getServer().getPlayerList().getPlayerByName(name);
        if (online != null) {
            showInfo(source, online.getUUID(), McCompat.name(online.getGameProfile()));
            return 1;
        }
        MinecraftServer srv = source.getServer();
        profiles.findUuidByLastName(name).thenAcceptAsync(uuid -> {
            if (uuid.isPresent()) {
                showInfo(source, uuid.get(), name);
            } else {
                source.sendSuccess(() -> FabricMessageFormatter.parse(
                        config.get().formatMessage("bounty.info-none", "player", name)), false);
            }
        }, srv);
        return 1;
    }

    private void showInfo(CommandSourceStack source, UUID uuid, String name) {
        bounties.infoMessages(uuid, name, blob -> ItemBlobs.decode(blob, source.registryAccess()).size(), config.get())
                .forEach(line -> source.sendSuccess(() -> FabricMessageFormatter.parse(line), false));
    }
}
