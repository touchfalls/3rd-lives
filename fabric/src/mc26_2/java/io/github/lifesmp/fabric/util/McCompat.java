// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.fabric.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.scores.TeamColor;

import java.util.Optional;
import java.util.UUID;

/** Minecraft calls whose API differs between 1.21.1 and 26.x. The 1.21.1 copy lives in src/mc1_21_1. */
public final class McCompat {

    private McCompat() {
    }

    public static String name(GameProfile profile) {
        return profile.name();
    }

    public static String dimensionId(Level level) {
        return level.dimension().identifier().toString();
    }

    public static boolean hasPermission(CommandSourceStack source, int level) {
        return source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(level)));
    }

    public static void setGray(PlayerTeam team) {
        team.setColor(Optional.of(TeamColor.GRAY));
    }

    public static ClickEvent runCommand(String command) {
        return new ClickEvent.RunCommand(command);
    }

    /** A listed survival TAB entry with zero latency. */
    public static ClientboundPlayerInfoUpdatePacket.Entry tabEntry(UUID uuid, GameProfile profile, Component displayName) {
        return new ClientboundPlayerInfoUpdatePacket.Entry(uuid, profile, true, 0, GameType.SURVIVAL, displayName, true, 0, null);
    }
}
