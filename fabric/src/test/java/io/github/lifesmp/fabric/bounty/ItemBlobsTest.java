// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.fabric.bounty;

import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ItemBlobsTest {

    private static HolderLookup.Provider registries;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    /**
     * 26.x binds item components only when a server loads its data packs, so a plain unit test
     * cannot create items there; the round trip then runs on 1.21.1 only.
     */
    private static boolean canCreateItems() {
        try {
            new ItemStack(Items.DIAMOND);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Test
    void itemsSurviveRoundTrip() {
        assumeTrue(canCreateItems(), "item components are not bound outside a server on this version");
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.set(DataComponents.CUSTOM_NAME, Component.literal("Headhunter"));
        ItemStack diamonds = new ItemStack(Items.DIAMOND, 37);

        List<ItemStack> decoded = ItemBlobs.decode(ItemBlobs.encode(List.of(sword, diamonds), registries), registries);

        assertThat(decoded).hasSize(2);
        assertThat(ItemStack.matches(decoded.get(0), sword)).isTrue();
        assertThat(ItemStack.matches(decoded.get(1), diamonds)).isTrue();
    }

    @Test
    void emptyAndBrokenDataGiveNoItems() {
        assertThat(ItemBlobs.encode(List.of(), registries)).isNull();
        assertThat(ItemBlobs.decode(null, registries)).isEmpty();
        assertThat(ItemBlobs.decode(new byte[]{1, 2, 3}, registries)).isEmpty();
    }
}
