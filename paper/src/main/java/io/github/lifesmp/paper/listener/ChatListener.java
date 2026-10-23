// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.paper.listener;

import io.github.lifesmp.core.config.LifeConfig;
import io.github.lifesmp.core.domain.model.LifeProfile;
import io.github.lifesmp.core.domain.model.PlayerColor;
import io.github.lifesmp.core.storage.LifeProfileCache;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Event listener applying life-color styling and prefixes to chat messages.
 */
public final class ChatListener implements Listener {

    private final LifeProfileCache profileCache;
    private final Supplier<LifeConfig> configSupplier;

    public ChatListener(
            @NotNull LifeProfileCache profileCache,
            @NotNull Supplier<LifeConfig> configSupplier
    ) {
        this.profileCache = Objects.requireNonNull(profileCache, "profileCache cannot be null");
        this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier cannot be null");
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        LifeProfile profile = profileCache.getCachedProfile(player.getUniqueId()).orElse(null);
        int lives = profile != null ? profile.getLives() : configSupplier.get().getMaxLives();
        PlayerColor playerColor = PlayerColor.fromLives(lives);
        TextColor color = TextColor.fromHexString(playerColor.getHex());

        event.renderer((source, sourceDisplayName, message, viewer) -> {
            Component prefix = Component.text("● ", color);
            Component coloredName = Component.text(source.getName(), color);

            return Component.empty()
                    .append(prefix)
                    .append(coloredName)
                    .append(Component.text(": ", NamedTextColor.GRAY))
                    .append(message);
        });
    }
}
