// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.fabric.platform;

import io.github.lifesmp.core.config.LifeConfig;
import io.github.lifesmp.core.domain.model.DeadEntry;
import io.github.lifesmp.fabric.scoreboard.FabricScoreboardManager;
import io.github.lifesmp.fabric.util.McCompat;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FabricTabPresenterTest {

    private MinecraftServer server;
    private FabricScoreboardManager scoreboardManager;
    private LifeConfig config;
    private List<Packet<?>> broadcastedPackets;
    private List<SinglePacketDelivery> sentToPlayerPackets;
    private FabricTabPresenter presenter;

    record SinglePacketDelivery(UUID targetUuid, Packet<?> packet) {}

    @BeforeEach
    void setUp() {
        server = mock(MinecraftServer.class);
        scoreboardManager = mock(FabricScoreboardManager.class);
        when(server.isSameThread()).thenReturn(true);

        broadcastedPackets = new ArrayList<>();
        sentToPlayerPackets = new ArrayList<>();

        Consumer<Packet<?>> broadcaster = broadcastedPackets::add;
        BiConsumer<UUID, Packet<?>> playerSender = (uuid, packet) -> sentToPlayerPackets.add(new SinglePacketDelivery(uuid, packet));

        config = LifeConfig.createDefault();
        presenter = new FabricTabPresenter(() -> server, () -> config, scoreboardManager, broadcaster, playerSender);
    }

    @Test
    @DisplayName("showDead dispatches update packet with dead player and adds to zz_dead team")
    void showDeadDispatchesPacket() {
        UUID deadUuid = UUID.randomUUID();
        DeadEntry entry = new DeadEntry(deadUuid, "DeadPlayer", Instant.now().plusSeconds(3600));

        presenter.showDead(entry);

        verify(scoreboardManager).addToDeadTeam("DeadPlayer");
        assertThat(broadcastedPackets).hasSize(1);

        Packet<?> sent = broadcastedPackets.get(0);
        assertThat(sent).isInstanceOf(ClientboundPlayerInfoUpdatePacket.class);
        ClientboundPlayerInfoUpdatePacket updatePacket = (ClientboundPlayerInfoUpdatePacket) sent;
        assertThat(updatePacket.entries()).hasSize(1);
        assertThat(updatePacket.entries().get(0).profileId()).isEqualTo(deadUuid);
        assertThat(McCompat.name(updatePacket.entries().get(0).profile())).isEqualTo("DeadPlayer");
        assertThat(updatePacket.entries().get(0).listed()).isTrue();
    }

    @Test
    @DisplayName("hideDead removes from dead team and dispatches remove packet")
    void hideDeadDispatchesPacket() {
        UUID deadUuid = UUID.randomUUID();
        DeadEntry entry = new DeadEntry(deadUuid, "DeadPlayer", Instant.now().plusSeconds(3600));
        presenter.showDead(entry);

        presenter.hideDead(deadUuid);

        verify(scoreboardManager).removeFromDeadTeam("DeadPlayer");
        assertThat(broadcastedPackets).hasSize(2);

        Packet<?> secondPacket = broadcastedPackets.get(1);
        assertThat(secondPacket).isInstanceOf(ClientboundPlayerInfoRemovePacket.class);
        ClientboundPlayerInfoRemovePacket removePacket = (ClientboundPlayerInfoRemovePacket) secondPacket;
        assertThat(removePacket.profileIds()).containsExactly(deadUuid);
    }

    @Test
    @DisplayName("showAllTo sends batched packet to joining viewer")
    void showAllToBatched() {
        UUID viewerUuid = UUID.randomUUID();

        DeadEntry d1 = new DeadEntry(UUID.randomUUID(), "Dead1", Instant.now().plusSeconds(100));
        DeadEntry d2 = new DeadEntry(UUID.randomUUID(), "Dead2", Instant.now().plusSeconds(200));

        presenter.showAllTo(viewerUuid, List.of(d1, d2));

        verify(scoreboardManager).addToDeadTeam("Dead1");
        verify(scoreboardManager).addToDeadTeam("Dead2");

        assertThat(sentToPlayerPackets).hasSize(1);
        SinglePacketDelivery delivery = sentToPlayerPackets.get(0);
        assertThat(delivery.targetUuid()).isEqualTo(viewerUuid);

        Packet<?> sent = delivery.packet();
        assertThat(sent).isInstanceOf(ClientboundPlayerInfoUpdatePacket.class);
        ClientboundPlayerInfoUpdatePacket packet = (ClientboundPlayerInfoUpdatePacket) sent;
        assertThat(packet.entries()).hasSize(2);
        assertThat(McCompat.name(packet.entries().get(0).profile())).isEqualTo("Dead1");
        assertThat(McCompat.name(packet.entries().get(1).profile())).isEqualTo("Dead2");
    }

    @Test
    @DisplayName("refreshAll sends UPDATE_DISPLAY_NAME packet to online players")
    void refreshAllDispatchesUpdate() {
        DeadEntry d1 = new DeadEntry(UUID.randomUUID(), "Dead1", Instant.now().plusSeconds(100));

        presenter.refreshAll(List.of(d1));

        assertThat(broadcastedPackets).hasSize(1);
        Packet<?> sent = broadcastedPackets.get(0);
        assertThat(sent).isInstanceOf(ClientboundPlayerInfoUpdatePacket.class);
        ClientboundPlayerInfoUpdatePacket packet = (ClientboundPlayerInfoUpdatePacket) sent;
        assertThat(packet.actions()).containsExactly(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME);
        assertThat(packet.entries()).hasSize(1);
    }

    @Test
    @DisplayName("When dead-tab is disabled, showDead does not send packets")
    void disabledDeadTabDoesNothing() {
        LifeConfig disabledConfig = new LifeConfig(
                config.getPrefix(),
                config.getMaxLives(),
                config.getBanHours(),
                config.getRestoredLivesAfterBan(),
                config.getRegenHoursPerLife(),
                config.getGiftRegenHoursPerLife(),
                config.getAfkMinutes(),
                config.getRetaliationWindowSeconds(),
                config.getGiftRequestTimeoutSeconds(),
                config.getAutosaveMinutes(),
                false, // deadTabEnabled = false
                config.getDeadTabFormat(),
                config.getDeadTabColor(),
                config.isDeadTabShowTime(),
                config.getDeadTabMaxEntries(),
                config.isDeadTabSortLast(),
                config.getLifeColors(),
                config.getMessages()
        );

        FabricTabPresenter disabledPresenter = new FabricTabPresenter(
                () -> server,
                () -> disabledConfig,
                scoreboardManager,
                broadcastedPackets::add,
                (uuid, packet) -> sentToPlayerPackets.add(new SinglePacketDelivery(uuid, packet))
        );

        disabledPresenter.showDead(new DeadEntry(UUID.randomUUID(), "DeadPlayer", Instant.now().plusSeconds(100)));

        assertThat(broadcastedPackets).isEmpty();
        verify(scoreboardManager, never()).addToDeadTeam(any());
    }

    @Test
    @DisplayName("showDead waits for the grace period and is skipped when the entry was hidden meanwhile")
    void showDeadIsDeferredAndGuarded() {
        List<Runnable> deferred = new ArrayList<>();
        FabricTabPresenter deferring = new FabricTabPresenter(
                () -> server, () -> config, scoreboardManager,
                broadcastedPackets::add,
                (uuid, packet) -> sentToPlayerPackets.add(new SinglePacketDelivery(uuid, packet)),
                deferred::add
        );

        DeadEntry stillDead = new DeadEntry(UUID.randomUUID(), "StillDead", Instant.now().plusSeconds(3600));
        DeadEntry pardoned = new DeadEntry(UUID.randomUUID(), "Pardoned", Instant.now().plusSeconds(3600));
        deferring.showDead(stillDead);
        deferring.showDead(pardoned);
        deferring.hideDead(pardoned.uuid());
        int packetsBeforeGracePeriod = broadcastedPackets.size();

        deferred.forEach(Runnable::run);

        assertThat(deferred).hasSize(2);
        // the remove packet of hideDead, then only the entry that is still dead
        assertThat(packetsBeforeGracePeriod).isEqualTo(1);
        assertThat(broadcastedPackets).hasSize(2);
        assertThat(broadcastedPackets.get(1)).isInstanceOf(net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.class);
    }
}
