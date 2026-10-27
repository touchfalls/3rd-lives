// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.paper.platform;

import com.mojang.authlib.GameProfile;
import io.github.lifesmp.core.config.LifeConfig;
import io.github.lifesmp.core.domain.model.DeadEntry;
import io.github.lifesmp.core.ports.TabPresenter;
import io.github.lifesmp.paper.scoreboard.PaperScoreboardManager;
import io.papermc.paper.adventure.PaperAdventure;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.world.level.GameType;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Paper implementation of {@link TabPresenter} using native Mojang-mapped NMS packets
 * (ClientboundPlayerInfoUpdatePacket and ClientboundPlayerInfoRemovePacket).
 * <p>
 * Dispatches all network packet delivery and scoreboard manipulations to the server main thread
 * as single-task batches, guaranteeing thread-safety without any Netty pipeline race conditions.
 */
public final class PaperTabPresenter implements TabPresenter {

    /**
     * Vanilla broadcasts a remove packet for the UUID of a player who disconnects. A banned player's
     * fake TAB entry uses the same UUID, so it is shown only after this grace period.
     */
    private static final long QUIT_GRACE_TICKS = 10L;

    /** Entry gained showHat and listOrder after 1.21.1, so it is built by parameter type to run on 1.21.1 and 26.x. */
    private static final Constructor<?> ENTRY_CONSTRUCTOR = Arrays.stream(ClientboundPlayerInfoUpdatePacket.Entry.class.getConstructors())
            .max(Comparator.comparingInt(Constructor::getParameterCount))
            .orElseThrow();

    private final Plugin plugin;
    private final Supplier<LifeConfig> configSupplier;
    private final PaperScoreboardManager scoreboardManager;

    // Cache UUID -> player name to cleanly remove entries from scoreboard team
    private final Map<UUID, String> deadPlayerNames = new ConcurrentHashMap<>();

    public PaperTabPresenter(
            @NotNull Plugin plugin,
            @NotNull Supplier<LifeConfig> configSupplier,
            @NotNull PaperScoreboardManager scoreboardManager
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier cannot be null");
        this.scoreboardManager = Objects.requireNonNull(scoreboardManager, "scoreboardManager cannot be null");
    }

    @Override
    public void showDead(@NotNull DeadEntry entry) {
        Objects.requireNonNull(entry, "entry cannot be null");
        deadPlayerNames.put(entry.uuid(), entry.name());

        afterQuit(entry.uuid(), () -> {
            LifeConfig config = configSupplier.get();
            if (!config.isDeadTabEnabled()) {
                return;
            }

            if (config.isDeadTabSortLast()) {
                scoreboardManager.addToDeadTeam(entry.name());
            }

            GameProfile profile = new GameProfile(entry.uuid(), entry.name());
            net.minecraft.network.chat.Component vanillaDisplayName = createDisplayName(entry, config);

            ClientboundPlayerInfoUpdatePacket.Entry packetEntry = tabEntry(entry.uuid(), profile, vanillaDisplayName);

            EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions = EnumSet.of(
                    ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME
            );
            ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(actions, packetEntry);

            for (Player player : Bukkit.getOnlinePlayers()) {
                ((CraftPlayer) player).getHandle().connection.send(packet);
            }
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
            // A remove packet for a player who is online makes other clients stop showing that player
            if (Bukkit.getPlayer(uuid) != null) {
                return;
            }

            ClientboundPlayerInfoRemovePacket packet = new ClientboundPlayerInfoRemovePacket(List.of(uuid));
            for (Player player : Bukkit.getOnlinePlayers()) {
                ((CraftPlayer) player).getHandle().connection.send(packet);
            }
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
            Player viewer = Bukkit.getPlayer(viewerUuid);
            if (viewer == null || !viewer.isOnline()) {
                return;
            }

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
                net.minecraft.network.chat.Component vanillaDisplayName = createDisplayName(entry, config);

                packetEntries.add(tabEntry(entry.uuid(), profile, vanillaDisplayName));
            }

            EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions = EnumSet.of(
                    ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME
            );
            ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(actions, packetEntries);

            ((CraftPlayer) viewer).getHandle().connection.send(packet);
        });
    }

    @Override
    public void refreshAll(@NotNull Collection<DeadEntry> entries) {
        Objects.requireNonNull(entries, "entries cannot be null");

        if (entries.isEmpty() || Bukkit.getOnlinePlayers().isEmpty()) {
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
                net.minecraft.network.chat.Component vanillaDisplayName = createDisplayName(entry, config);

                packetEntries.add(tabEntry(entry.uuid(), profile, vanillaDisplayName));
            }

            EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions = EnumSet.of(
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME
            );
            ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(actions, packetEntries);

            for (Player player : Bukkit.getOnlinePlayers()) {
                ((CraftPlayer) player).getHandle().connection.send(packet);
            }
        });
    }

    /** A listed survival TAB entry with zero latency. */
    static ClientboundPlayerInfoUpdatePacket.Entry tabEntry(UUID uuid, GameProfile profile, net.minecraft.network.chat.Component displayName) {
        Class<?>[] types = ENTRY_CONSTRUCTOR.getParameterTypes();
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            Class<?> type = types[i];
            if (type == UUID.class) {
                args[i] = uuid;
            } else if (type == GameProfile.class) {
                args[i] = profile;
            } else if (type == boolean.class) {
                args[i] = true; // listed, showHat
            } else if (type == int.class) {
                args[i] = 0; // latency, listOrder
            } else if (type == GameType.class) {
                args[i] = GameType.SURVIVAL;
            } else if (type == net.minecraft.network.chat.Component.class) {
                args[i] = displayName;
            } // the chat session stays null
        }
        try {
            return (ClientboundPlayerInfoUpdatePacket.Entry) ENTRY_CONSTRUCTOR.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot create a TAB entry on this server version", e);
        }
    }

    private net.minecraft.network.chat.Component createDisplayName(DeadEntry entry, LifeConfig config) {
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

        net.kyori.adventure.text.Component adventureComponent = MiniMessage.miniMessage().deserialize(formatted);
        return PaperAdventure.asVanilla(adventureComponent);
    }

    private void afterQuit(UUID uuid, Runnable task) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // hideDead may have run during the grace period (pardon, reset, expiry)
            if (deadPlayerNames.containsKey(uuid) && Bukkit.getPlayer(uuid) == null) {
                task.run();
            }
        }, QUIT_GRACE_TICKS);
    }

    private void dispatchToMain(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
}
