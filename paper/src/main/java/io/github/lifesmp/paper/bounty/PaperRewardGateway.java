// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.paper.bounty;

import io.github.lifesmp.core.ports.RewardGateway;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Gives bounty stakes to online Paper players; items that do not fit drop at the player's feet.
 */
public final class PaperRewardGateway implements RewardGateway {

    @Override
    public boolean give(@NotNull UUID uuid, byte @Nullable [] items, int itemPercent, int xpLevels) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return false;
        }
        for (ItemStack item : decode(items)) {
            int amount = item.getAmount() * itemPercent / 100;
            if (amount <= 0) {
                continue;
            }
            item.setAmount(amount);
            player.getInventory().addItem(item).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
        if (xpLevels > 0) {
            player.giveExpLevels(xpLevels);
        }
        return true;
    }

    @NotNull
    static ItemStack[] decode(byte @Nullable [] items) {
        return items == null ? new ItemStack[0] : ItemStack.deserializeItemsFromBytes(items);
    }
}
