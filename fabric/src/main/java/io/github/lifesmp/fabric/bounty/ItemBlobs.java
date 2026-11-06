// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.fabric.bounty;

import com.mojang.serialization.Dynamic;
import io.github.lifesmp.fabric.util.McCompat;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.List;

/**
 * Item stacks to and from compressed NBT bytes, the format bounty stakes are stored in.
 * The game's data version is stored too, so items saved by an older Minecraft are upgraded when read.
 */
final class ItemBlobs {

    private static final Logger LOGGER = LoggerFactory.getLogger(ItemBlobs.class);
    private static final String ITEMS = "items";
    private static final String DATA_VERSION = "DataVersion";

    private ItemBlobs() {
    }

    /** Puts the stack in the inventory; what does not fit drops at the player's feet. */
    static void giveOrDrop(@NotNull ServerPlayer player, @NotNull ItemStack item) {
        if (!player.getInventory().add(item) && !item.isEmpty()) {
            // An ItemEntity, not player.drop(): drop's signature differs between Minecraft versions
            ItemEntity dropped = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), item);
            dropped.setDefaultPickUpDelay();
            player.level().addFreshEntity(dropped);
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
        root.put(ITEMS, list);
        root.putInt(DATA_VERSION, McCompat.dataVersion());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            NbtIo.writeCompressed(root, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /**
     * Reads stake items. Never throws: unreadable data is logged in Base64, so an admin can recover it,
     * and an empty list is returned, so a death or command that pays a stake still completes.
     */
    @NotNull
    static List<ItemStack> decode(byte @Nullable [] blob, @NotNull HolderLookup.Provider registries) {
        if (blob == null) {
            return List.of();
        }
        try {
            CompoundTag root = NbtIo.readCompressed(new ByteArrayInputStream(blob), NbtAccounter.unlimitedHeap());
            Tag items = root.get(ITEMS);
            int saved = McCompat.getInt(root, DATA_VERSION);
            int current = McCompat.dataVersion();
            if (saved < current && items instanceof ListTag list) {
                ListTag upgraded = new ListTag();
                for (Tag item : list) {
                    upgraded.add(DataFixers.getDataFixer()
                            .update(References.ITEM_STACK, new Dynamic<>(NbtOps.INSTANCE, item), saved, current)
                            .getValue());
                }
                items = upgraded;
            }
            return ItemStack.CODEC.listOf()
                    .parse(registries.createSerializationContext(NbtOps.INSTANCE), items)
                    .getOrThrow();
        } catch (Exception e) {
            LOGGER.error("Could not read bounty stake items, they are lost. Raw data (Base64): {}",
                    Base64.getEncoder().encodeToString(blob), e);
            return List.of();
        }
    }
}
