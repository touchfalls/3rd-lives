// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.paper.platform;

import io.github.lifesmp.core.ports.NotificationService;
import io.github.lifesmp.paper.scoreboard.PaperScoreboardManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

/**
 * Paper implementation of {@link NotificationService}.
 */
public final class PaperNotificationService implements NotificationService {

    private final Plugin plugin;
    private final PaperScoreboardManager scoreboardManager;

    public PaperNotificationService(@NotNull Plugin plugin, @NotNull PaperScoreboardManager scoreboardManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.scoreboardManager = Objects.requireNonNull(scoreboardManager, "scoreboardManager cannot be null");
    }

    private final java.util.Map<UUID, java.util.List<String>> pendingMessages = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void sendMessage(@NotNull UUID targetUuid, @NotNull String rawMessage) {
        Player player = Bukkit.getPlayer(targetUuid);
        if (player != null && player.isOnline()) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(rawMessage));
        } else {
            pendingMessages.computeIfAbsent(targetUuid, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(rawMessage);
        }
    }

    /**
     * Flushes and delivers any pending messages queued for the player while offline/logging in.
     */
    public void deliverPendingMessages(@NotNull Player player) {
        java.util.List<String> queued = pendingMessages.remove(player.getUniqueId());
        if (queued != null) {
            for (String msg : queued) {
                player.sendMessage(MiniMessage.miniMessage().deserialize(msg));
            }
        }
    }

    @Override
    public void sendActionBar(@NotNull UUID targetUuid, @NotNull String rawMessage) {
        Player player = Bukkit.getPlayer(targetUuid);
        if (player != null && player.isOnline()) {
            player.sendActionBar(MiniMessage.miniMessage().deserialize(rawMessage));
        }
    }

    @Override
    public void broadcast(@NotNull String rawMessage) {
        Bukkit.broadcast(MiniMessage.miniMessage().deserialize(rawMessage));
    }

    @Override
    public void kickPlayer(@NotNull UUID targetUuid, @NotNull String reason) {
        Runnable kickAction = () -> {
            Player player = Bukkit.getPlayer(targetUuid);
            if (player != null && player.isOnline()) {
                player.kick(MiniMessage.miniMessage().deserialize(reason));
            }
        };

        if (Bukkit.isPrimaryThread()) {
            kickAction.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, kickAction);
        }
    }

    @Override
    public void updateDisplayNameAndTab(@NotNull UUID playerUuid, int lives) {
        Runnable updateAction = () -> {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                scoreboardManager.updatePlayerVisuals(player, lives);
            }
        };

        if (Bukkit.isPrimaryThread()) {
            updateAction.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, updateAction);
        }
    }
}
