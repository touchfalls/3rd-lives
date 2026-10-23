// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.fabric.platform;

import io.github.lifesmp.core.ports.NotificationService;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

/**
 * Fabric implementation of {@link NotificationService} bridging messages
 * to Fabric player instances via {@link FabricPlayerBridge}.
 */
public final class FabricNotificationService implements NotificationService {

    private final FabricPlayerBridge playerBridge;

    public FabricNotificationService(@NotNull FabricPlayerBridge playerBridge) {
        this.playerBridge = Objects.requireNonNull(playerBridge, "playerBridge cannot be null");
    }

    @Override
    public void sendMessage(@NotNull UUID targetUuid, @NotNull String rawMessage) {
        playerBridge.sendMessage(targetUuid, rawMessage, false);
    }

    @Override
    public void sendActionBar(@NotNull UUID targetUuid, @NotNull String rawMessage) {
        playerBridge.sendMessage(targetUuid, rawMessage, true);
    }

    @Override
    public void broadcast(@NotNull String rawMessage) {
        playerBridge.broadcast(rawMessage);
    }

    @Override
    public void kickPlayer(@NotNull UUID targetUuid, @NotNull String reason) {
        playerBridge.kickPlayer(targetUuid, reason);
    }

    @Override
    public void updateDisplayNameAndTab(@NotNull UUID playerUuid, int lives) {
        playerBridge.updateVisuals(playerUuid, lives);
    }
}
