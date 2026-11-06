// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.paper.bounty;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.github.lifesmp.core.config.LifeConfig;
import io.github.lifesmp.core.domain.model.Bounty;
import io.github.lifesmp.core.domain.service.BountyService;
import io.github.lifesmp.paper.LifeSmpPaperPlugin;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@code /bounty set|cancel|buyout|info} and the stake chest.
 */
public final class PaperBountyCommands implements Listener {

    private final LifeSmpPaperPlugin plugin;
    private final BountyService bounties;

    public PaperBountyCommands(@NotNull LifeSmpPaperPlugin plugin, @NotNull BountyService bounties) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.bounties = Objects.requireNonNull(bounties, "bounties cannot be null");
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            RequiredArgumentBuilder<CommandSourceStack, Integer> buyout = Commands.argument("buyout", IntegerArgumentType.integer(0))
                    .executes(ctx -> set(ctx.getSource(), StringArgumentType.getString(ctx, "target"),
                            IntegerArgumentType.getInteger(ctx, "xp"), IntegerArgumentType.getInteger(ctx, "lives"),
                            IntegerArgumentType.getInteger(ctx, "buyout")));
            var root = Commands.literal("bounty")
                    .executes(ctx -> usage(ctx.getSource().getSender()))
                    .then(Commands.literal("set")
                            .then(Commands.argument("target", StringArgumentType.word())
                                    .suggests((ctx, b) -> {
                                        Bukkit.getOnlinePlayers().forEach(p -> b.suggest(p.getName()));
                                        return b.buildFuture();
                                    })
                                    .then(Commands.argument("xp", IntegerArgumentType.integer(0))
                                            .executes(ctx -> set(ctx.getSource(), StringArgumentType.getString(ctx, "target"),
                                                    IntegerArgumentType.getInteger(ctx, "xp"), 0, -1))
                                            .then(Commands.argument("lives", IntegerArgumentType.integer(0))
                                                    .executes(ctx -> set(ctx.getSource(), StringArgumentType.getString(ctx, "target"),
                                                            IntegerArgumentType.getInteger(ctx, "xp"),
                                                            IntegerArgumentType.getInteger(ctx, "lives"), -1))
                                                    .then(buyout)))))
                    .then(Commands.literal("cancel").executes(ctx -> cancel(ctx.getSource())))
                    .then(Commands.literal("buyout").executes(ctx -> buyout(ctx.getSource())))
                    .then(Commands.literal("info")
                            .then(Commands.argument("target", StringArgumentType.word())
                                    .suggests((ctx, b) -> {
                                        Bukkit.getOnlinePlayers().forEach(p -> b.suggest(p.getName()));
                                        return b.buildFuture();
                                    })
                                    .executes(ctx -> info(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "target")))));
            event.registrar().register(root.build(), "Bounties on players' heads", List.of());
        });
    }

    private LifeConfig config() {
        return plugin.getLifeConfig();
    }

    private void send(CommandSender to, String raw) {
        to.sendMessage(MiniMessage.miniMessage().deserialize(raw));
    }

    private int usage(CommandSender sender) {
        send(sender, config().formatMessage("bounty.usage"));
        return 1;
    }

    /** Buyout defaults to 150% of the XP stake, so a forgotten argument does not make the bounty cheap. */
    private int set(CommandSourceStack source, String targetName, int xp, int lives, int buyout) {
        if (!(source.getSender() instanceof Player issuer)) {
            return 0;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            send(issuer, config().formatMessage("gift.player-offline", "player", targetName));
            return 0;
        }
        int price = buyout >= 0 ? buyout : Math.max(bounties.getSettings().minBuyout(), xp * 3 / 2);
        BountyService.Status status = bounties.validatePlace(
                issuer.getUniqueId(), target.getUniqueId(), xp, lives, price, issuer.getLevel());
        if (status != BountyService.Status.OK) {
            send(issuer, bounties.statusMessage(status, config()));
            return 0;
        }
        BountyMenu menu = new BountyMenu(target.getUniqueId(), target.getName(), xp, lives, price);
        menu.inventory = Bukkit.createInventory(menu, 27,
                MiniMessage.miniMessage().deserialize(config().formatMessage("bounty.menu-title", "player", target.getName())));
        issuer.openInventory(menu.inventory);
        return 1;
    }

    @EventHandler
    public void onMenuClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof BountyMenu menu) || !(event.getPlayer() instanceof Player issuer)) {
            return;
        }
        Inventory inv = event.getInventory();
        List<ItemStack> items = Arrays.stream(inv.getContents()).filter(i -> i != null && !i.getType().isAir()).toList();
        inv.clear();
        byte[] blob = items.isEmpty() ? null : ItemStack.serializeItemsAsBytes(items);
        BountyService.Status status = bounties.place(issuer.getUniqueId(), issuer.getName(), menu.targetName, menu.target,
                blob, menu.xp, menu.lives, menu.buyout, issuer.getLevel(), Instant.now(), config());
        if (status == BountyService.Status.OK) {
            issuer.setLevel(issuer.getLevel() - menu.xp);
            return;
        }
        // Not placed: hand the items back
        issuer.getInventory().addItem(items.toArray(ItemStack[]::new)).values()
                .forEach(left -> issuer.getWorld().dropItemNaturally(issuer.getLocation(), left));
        send(issuer, bounties.statusMessage(status, config()));
    }

    /** Closes open stake chests so their items go back before the plugin stops. */
    public void closeOpenMenus() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof BountyMenu) {
                p.closeInventory();
            }
        }
    }

    private int cancel(CommandSourceStack source) {
        if (!(source.getSender() instanceof Player player)) {
            return 0;
        }
        BountyService.Status status = bounties.cancel(player.getUniqueId(), config());
        if (status != BountyService.Status.OK) {
            send(player, bounties.statusMessage(status, config()));
            return 0;
        }
        return 1;
    }

    private int buyout(CommandSourceStack source) {
        if (!(source.getSender() instanceof Player player)) {
            return 0;
        }
        int price = bounties.getBounty(player.getUniqueId()).map(Bounty::buyoutLevels).orElse(0);
        BountyService.Status status = bounties.buyout(player.getUniqueId(), player.getLevel(), config());
        if (status != BountyService.Status.OK) {
            send(player, bounties.statusMessage(status, config()));
            return 0;
        }
        player.setLevel(player.getLevel() - price);
        return 1;
    }

    private int info(CommandSender sender, String name) {
        Player online = Bukkit.getPlayerExact(name);
        OfflinePlayer target = online != null ? online : Bukkit.getOfflinePlayerIfCached(name);
        if (target == null) {
            send(sender, config().formatMessage("bounty.info-none", "player", name));
            return 0;
        }
        String shownName = target.getName() != null ? target.getName() : name;
        long duration = bounties.getSettings().durationSeconds();
        Bounty bounty = bounties.getBounty(target.getUniqueId()).orElse(null);
        boolean wanted = bounties.isWanted(target.getUniqueId());
        if (bounty == null && !wanted) {
            send(sender, config().formatMessage("bounty.info-none", "player", shownName));
            return 1;
        }
        if (bounty != null) {
            send(sender, config().formatMessage("bounty.info", Map.of(
                    "player", shownName,
                    "issuer", bounty.issuerName(),
                    "xp", String.valueOf(bounty.xpLevels()),
                    "lives", String.valueOf(bounty.lives()),
                    "items", String.valueOf(PaperRewardGateway.decode(bounty.items()).length),
                    "buyout", String.valueOf(bounty.buyoutLevels()),
                    "remaining", config().formatDuration(Duration.ofSeconds(Math.max(0, duration - bounty.survivedSeconds())))
            )));
            int lives = plugin.getLifeEngine().getProfileCache().getCachedProfile(target.getUniqueId())
                    .map(p -> p.getLives()).orElse(2);
            if (lives <= 1) {
                send(sender, config().formatMessage("bounty.info-frozen"));
            }
        }
        if (wanted) {
            long left = Math.max(0, duration - bounties.getWantedSurvivedSeconds(target.getUniqueId()));
            send(sender, config().formatMessage("bounty.info-wanted", Map.of(
                    "player", shownName, "remaining", config().formatDuration(Duration.ofSeconds(left)))));
        }
        return 1;
    }
}
