// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.paper.bounty;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Chest the issuer fills with stake items; the bounty is placed when it closes.
 */
final class BountyMenu implements InventoryHolder {

    final UUID target;
    final String targetName;
    final int xp;
    final int lives;
    final int buyout;
    Inventory inventory;

    BountyMenu(UUID target, String targetName, int xp, int lives, int buyout) {
        this.target = target;
        this.targetName = targetName;
        this.xp = xp;
        this.lives = lives;
        this.buyout = buyout;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
