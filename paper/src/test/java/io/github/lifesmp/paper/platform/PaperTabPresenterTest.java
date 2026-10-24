// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.paper.platform;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.world.level.GameType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaperTabPresenterTest {

    @Test
    void tabEntryFillsEveryField() {
        UUID uuid = UUID.randomUUID();
        GameProfile profile = new GameProfile(uuid, "DeadPlayer");
        Component name = Component.literal("[DEAD] DeadPlayer");

        ClientboundPlayerInfoUpdatePacket.Entry entry = PaperTabPresenter.tabEntry(uuid, profile, name);

        assertThat(entry.profileId()).isEqualTo(uuid);
        assertThat(entry.profile()).isSameAs(profile);
        assertThat(entry.listed()).isTrue();
        assertThat(entry.latency()).isZero();
        assertThat(entry.gameMode()).isEqualTo(GameType.SURVIVAL);
        assertThat(entry.displayName()).isSameAs(name);
        assertThat(entry.chatSession()).isNull();
    }
}
