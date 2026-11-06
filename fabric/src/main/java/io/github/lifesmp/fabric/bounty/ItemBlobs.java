// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.fabric.bounty;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** Item stacks to and from compressed NBT bytes, the format bounty stakes are stored in. */
final class ItemBlobs {

    private ItemBlobs() {
    }

    /** Puts the stack in the inventory; what does not fit drops at the player's feet. */
    static void giveOrDrop(@NotNull ServerPlayer player, @NotNull ItemStack item) {
        if (!player.getInventory().add(item) && !item.isEmpty()) {
            player.drop(item, false);
        }
    }

    static byte @Nullable [] encode(@NotNull List<ItemStack> items, @NotNull HolderLookup.Provider registries) {
        if (items.isEmpty()) {
            return null;
        }
        Tag list = ItemStack.CODEC.listOf()
                .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), items)
                .getOrThrow();
        CompoundTag root = new CompoundTag();
        root.put("items", list);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            NbtIo.writeCompressed(root, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    @NotNull
    static List<ItemStack> decode(byte @Nullable [] blob, @NotNull HolderLookup.Provider registries) {
        if (blob == null) {
            return List.of();
        }
        try {
            CompoundTag root = NbtIo.readCompressed(new ByteArrayInputStream(blob), NbtAccounter.unlimitedHeap());
            return ItemStack.CODEC.listOf()
                    .parse(registries.createSerializationContext(NbtOps.INSTANCE), root.get("items"))
                    .getOrThrow();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
