// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.fabric.bounty;

import io.github.lifesmp.core.ports.RewardGateway;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Supplier;

/** Gives bounty stakes to online Fabric players; items that do not fit drop at the player's feet. */
public final class FabricRewardGateway implements RewardGateway {

    private final Supplier<MinecraftServer> server;

    public FabricRewardGateway(@NotNull Supplier<MinecraftServer> server) {
        this.server = server;
    }

    @Override
    public boolean give(@NotNull UUID uuid, byte @Nullable [] items, int itemPercent, int xpLevels) {
        MinecraftServer srv = server.get();
        ServerPlayer player = srv == null ? null : srv.getPlayerList().getPlayer(uuid);
        if (player == null) {
            return false;
        }
        for (ItemStack item : ItemBlobs.decode(items, player.registryAccess())) {
            int amount = item.getCount() * itemPercent / 100;
            if (amount > 0) {
                item.setCount(amount);
                ItemBlobs.giveOrDrop(player, item);
            }
        }
        if (xpLevels > 0) {
            player.giveExperienceLevels(xpLevels);
        }
        return true;
    }
}
