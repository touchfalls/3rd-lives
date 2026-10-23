// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.fabric.platform;

import com.mojang.authlib.GameProfile;
import io.github.lifesmp.core.config.LifeConfig;
import io.github.lifesmp.core.domain.model.DeadEntry;
import io.github.lifesmp.core.ports.TabPresenter;
import io.github.lifesmp.fabric.mixin.ClientboundPlayerInfoUpdatePacketAccessor;
import io.github.lifesmp.fabric.scoreboard.FabricScoreboardManager;
import io.github.lifesmp.fabric.util.FabricMessageFormatter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Fabric implementation of {@link TabPresenter} using native Mojang-mapped packets
 * (ClientboundPlayerInfoUpdatePacket and ClientboundPlayerInfoRemovePacket).
 * <p>
 * Thread-safety: Dispatches packet transmission and scoreboard updates to the MinecraftServer
 * main thread via {@link MinecraftServer#execute(Runnable)}.
 */
public final class FabricTabPresenter implements TabPresenter {

    private static final sun.misc.Unsafe UNSAFE;
    private static final long ENTRIES_FIELD_OFFSET;

    static {
        sun.misc.Unsafe u = null;
        long offset = -1L;
        try {
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            u = (sun.misc.Unsafe) unsafeField.get(null);

            for (Field f : ClientboundPlayerInfoUpdatePacket.class.getDeclaredFields()) {
                if (List.class.isAssignableFrom(f.getType())) {
                    offset = u.objectFieldOffset(f);
                    break;
                }
            }
        } catch (Throwable ignored) {
        }
        UNSAFE = u;
        ENTRIES_FIELD_OFFSET = offset;
    }

    private final Supplier<MinecraftServer> serverSupplier;
    private final Supplier<LifeConfig> configSupplier;
    private final FabricScoreboardManager scoreboardManager;
    private final Consumer<Packet<?>> packetBroadcaster;
    private final BiConsumer<UUID, Packet<?>> playerPacketSender;

    private final Map<UUID, String> deadPlayerNames = new ConcurrentHashMap<>();

    public FabricTabPresenter(
            @NotNull Supplier<MinecraftServer> serverSupplier,
            @NotNull Supplier<LifeConfig> configSupplier,
            @NotNull FabricScoreboardManager scoreboardManager
    ) {
        this(
                serverSupplier,
                configSupplier,
                scoreboardManager,
                packet -> {
                    MinecraftServer server = serverSupplier.get();
                    if (server != null) {
                        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                            if (player.connection != null) {
                                player.connection.send(packet);
                            }
                        }
                    }
                },
                (uuid, packet) -> {
                    MinecraftServer server = serverSupplier.get();
                    if (server != null) {
                        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                        if (player != null && player.connection != null) {
                            player.connection.send(packet);
                        }
                    }
                }
        );
    }

    public FabricTabPresenter(
            @NotNull Supplier<MinecraftServer> serverSupplier,
            @NotNull Supplier<LifeConfig> configSupplier,
            @NotNull FabricScoreboardManager scoreboardManager,
            @NotNull Consumer<Packet<?>> packetBroadcaster,
            @NotNull BiConsumer<UUID, Packet<?>> playerPacketSender
    ) {
        this.serverSupplier = Objects.requireNonNull(serverSupplier, "serverSupplier cannot be null");
        this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier cannot be null");
        this.scoreboardManager = Objects.requireNonNull(scoreboardManager, "scoreboardManager cannot be null");
        this.packetBroadcaster = Objects.requireNonNull(packetBroadcaster, "packetBroadcaster cannot be null");
        this.playerPacketSender = Objects.requireNonNull(playerPacketSender, "playerPacketSender cannot be null");
    }

    @Override
    public void showDead(@NotNull DeadEntry entry) {
        Objects.requireNonNull(entry, "entry cannot be null");
        deadPlayerNames.put(entry.uuid(), entry.name());

        dispatchToMain(() -> {
            LifeConfig config = configSupplier.get();
            if (!config.isDeadTabEnabled()) {
                return;
            }

            if (config.isDeadTabSortLast()) {
                scoreboardManager.addToDeadTeam(entry.name());
            }

            GameProfile profile = new GameProfile(entry.uuid(), entry.name());
            Component displayName = createDisplayName(entry, config);

            ClientboundPlayerInfoUpdatePacket.Entry packetEntry = new ClientboundPlayerInfoUpdatePacket.Entry(
                    entry.uuid(),
                    profile,
                    true,
                    0,
                    GameType.SURVIVAL,
                    displayName,
                    null
            );

            EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions = EnumSet.of(
                    ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME
            );
            ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(actions, List.of());
            setPacketEntries(packet, List.of(packetEntry));

            packetBroadcaster.accept(packet);
        });
    }

    @Override
    public void hideDead(@NotNull UUID uuid) {
        Objects.requireNonNull(uuid, "uuid cannot be null");
        String name = deadPlayerNames.remove(uuid);

        dispatchToMain(() -> {
            if (name != null) {
                scoreboardManager.removeFromDeadTeam(name);
            }

            ClientboundPlayerInfoRemovePacket packet = new ClientboundPlayerInfoRemovePacket(List.of(uuid));
            packetBroadcaster.accept(packet);
        });
    }

    @Override
    public void showAllTo(@NotNull UUID viewerUuid, @NotNull Collection<DeadEntry> entries) {
        Objects.requireNonNull(viewerUuid, "viewerUuid cannot be null");
        Objects.requireNonNull(entries, "entries cannot be null");

        if (entries.isEmpty()) {
            return;
        }

        dispatchToMain(() -> {
            LifeConfig config = configSupplier.get();
            if (!config.isDeadTabEnabled()) {
                return;
            }

            List<ClientboundPlayerInfoUpdatePacket.Entry> packetEntries = new ArrayList<>(entries.size());
            for (DeadEntry entry : entries) {
                deadPlayerNames.put(entry.uuid(), entry.name());
                if (config.isDeadTabSortLast()) {
                    scoreboardManager.addToDeadTeam(entry.name());
                }

                GameProfile profile = new GameProfile(entry.uuid(), entry.name());
                Component displayName = createDisplayName(entry, config);

                packetEntries.add(new ClientboundPlayerInfoUpdatePacket.Entry(
                        entry.uuid(),
                        profile,
                        true,
                        0,
                        GameType.SURVIVAL,
                        displayName,
                        null
                ));
            }

            EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions = EnumSet.of(
                    ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME
            );
            ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(actions, List.of());
            setPacketEntries(packet, packetEntries);

            playerPacketSender.accept(viewerUuid, packet);
        });
    }

    @Override
    public void refreshAll(@NotNull Collection<DeadEntry> entries) {
        Objects.requireNonNull(entries, "entries cannot be null");

        if (entries.isEmpty()) {
            return;
        }

        dispatchToMain(() -> {
            LifeConfig config = configSupplier.get();
            if (!config.isDeadTabEnabled()) {
                return;
            }

            List<ClientboundPlayerInfoUpdatePacket.Entry> packetEntries = new ArrayList<>(entries.size());
            for (DeadEntry entry : entries) {
                GameProfile profile = new GameProfile(entry.uuid(), entry.name());
                Component displayName = createDisplayName(entry, config);

                packetEntries.add(new ClientboundPlayerInfoUpdatePacket.Entry(
                        entry.uuid(),
                        profile,
                        true,
                        0,
                        GameType.SURVIVAL,
                        displayName,
                        null
                ));
            }

            EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions = EnumSet.of(
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME
            );
            ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(actions, List.of());
            setPacketEntries(packet, packetEntries);

            packetBroadcaster.accept(packet);
        });
    }

    private static void setPacketEntries(ClientboundPlayerInfoUpdatePacket packet, List<ClientboundPlayerInfoUpdatePacket.Entry> entries) {
        if (packet instanceof ClientboundPlayerInfoUpdatePacketAccessor accessor) {
            accessor.setEntries(entries);
        } else if (UNSAFE != null && ENTRIES_FIELD_OFFSET != -1L) {
            UNSAFE.putObject(packet, ENTRIES_FIELD_OFFSET, entries);
        }
    }

    private Component createDisplayName(DeadEntry entry, LifeConfig config) {
        String template = config.getDeadTabFormat();
        String formatted = template.replace("{player}", entry.name())
                                   .replace("<player>", entry.name());

        if (formatted.contains("{time}") || formatted.contains("<time>")) {
            Duration remaining = Duration.between(Instant.now(), entry.bannedUntil());
            if (remaining.isNegative()) {
                remaining = Duration.ZERO;
            }
            String timeStr = config.formatDurationHoursMinutes(remaining);
            formatted = formatted.replace("{time}", timeStr)
                                 .replace("<time>", timeStr);
        }

        return FabricMessageFormatter.parse(formatted);
    }

    private void dispatchToMain(Runnable task) {
        MinecraftServer server = serverSupplier.get();
        if (server == null) {
            task.run();
            return;
        }
        if (server.isSameThread()) {
            task.run();
        } else {
            server.execute(task);
        }
    }
}
