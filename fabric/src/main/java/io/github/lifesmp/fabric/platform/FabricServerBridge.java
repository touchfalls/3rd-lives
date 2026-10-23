// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.fabric.platform;

import io.github.lifesmp.fabric.scoreboard.FabricScoreboardManager;
import io.github.lifesmp.fabric.util.FabricMessageFormatter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;

/**
 * Concrete bridge connecting Mixin callbacks, MinecraftServer operations,
 * and the core domain engine.
 */
public final class FabricServerBridge implements FabricPlayerBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger(FabricServerBridge.class);

    private final FabricScoreboardManager scoreboardManager;
    @Nullable
    private volatile MinecraftServer server;

    public FabricServerBridge(@NotNull FabricScoreboardManager scoreboardManager) {
        this.scoreboardManager = Objects.requireNonNull(scoreboardManager, "scoreboardManager cannot be null");
    }

    public void setServer(@Nullable MinecraftServer server) {
        this.server = server;
    }

    @Nullable
    public MinecraftServer getServer() {
        return server;
    }

    private final java.util.Map<UUID, java.util.List<String>> pendingMessages = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void sendMessage(@NotNull UUID playerUuid, @NotNull String rawText, boolean actionBar) {
        MinecraftServer srv = server;
        if (srv == null) {
            if (!actionBar) {
                pendingMessages.computeIfAbsent(playerUuid, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(rawText);
            }
            LOGGER.debug("[Fabric-MSG] (Server offline) {} -> {}: {}", actionBar ? "ACTIONBAR" : "CHAT", playerUuid, rawText);
            return;
        }
        // Callers include database-thread callbacks; MinecraftServer.execute runs inline when already on the server thread.
        srv.execute(() -> {
            ServerPlayer player = srv.getPlayerList().getPlayer(playerUuid);
            if (player != null) {
                Component component = FabricMessageFormatter.parse(rawText);
                if (actionBar) {
                    player.sendSystemMessage(component, true);
                } else {
                    player.sendSystemMessage(component);
                }
            } else if (!actionBar) {
                pendingMessages.computeIfAbsent(playerUuid, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(rawText);
            }
        });
    }

    /**
     * Flushes and delivers queued messages when player joins.
     */
    public void deliverPendingMessages(@NotNull ServerPlayer player) {
        java.util.List<String> queued = pendingMessages.remove(player.getUUID());
        if (queued != null) {
            for (String msg : queued) {
                player.sendSystemMessage(FabricMessageFormatter.parse(msg));
            }
        }
    }

    @Override
    public void broadcast(@NotNull String rawText) {
        MinecraftServer srv = server;
        if (srv == null) {
            LOGGER.info("[Fabric-BROADCAST] (Server offline) {}", rawText);
            return;
        }
        srv.execute(() -> srv.getPlayerList().broadcastSystemMessage(FabricMessageFormatter.parse(rawText), false));
    }

    @Override
    public void kickPlayer(@NotNull UUID playerUuid, @NotNull String reason) {
        MinecraftServer srv = server;
        if (srv == null) {
            LOGGER.warn("[Fabric-KICK] Player {} kicked (server offline): {}", playerUuid, reason);
            return;
        }
        srv.execute(() -> {
            ServerPlayer player = srv.getPlayerList().getPlayer(playerUuid);
            if (player != null && player.connection != null) {
                player.connection.disconnect(FabricMessageFormatter.parse(reason));
            }
        });
    }

    @Override
    public void updateVisuals(@NotNull UUID playerUuid, int lives) {
        MinecraftServer srv = server;
        if (srv == null) return;
        srv.execute(() -> {
            ServerPlayer player = srv.getPlayerList().getPlayer(playerUuid);
            if (player != null) {
                srv.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
                        player
                ));
            }
        });
    }

    /**
     * Called when a player broadcasts a chat message.
     */
    public void handleChatMessage(@NotNull ServerPlayer sender, @NotNull PlayerChatMessage message) {
        LOGGER.debug("Chat message from {}: {}", sender.getScoreboardName(), message.decoratedContent().getString());
    }
}
