// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.fabric.mixin;

import io.github.lifesmp.core.domain.model.LifeProfile;
import io.github.lifesmp.core.domain.model.PlayerColor;
import io.github.lifesmp.fabric.LifeSmpFabricServer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin targeting {@link Player} to decorate getDisplayName() with Life SMP colors.
 * This directly colors player names in chat broadcasts, death messages, and entity hover text.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void onGetDisplayName(CallbackInfoReturnable<Component> cir) {
        LifeSmpFabricServer server = LifeSmpFabricServer.getInstance();
        if (server == null) return;

        Player self = (Player) (Object) this;
        LifeProfile profile = server.getLifeEngine().getProfileCache()
                .getCachedProfile(self.getUUID()).orElse(null);
        int lives = profile != null ? profile.getLives() : server.getLifeConfig().getMaxLives();
        PlayerColor color = PlayerColor.fromLives(lives);

        Component original = cir.getReturnValue();
        if (original instanceof MutableComponent mutable) {
            cir.setReturnValue(mutable.withStyle(style -> style.withColor(TextColor.fromRgb(color.getRgb()))));
        } else if (original != null) {
            cir.setReturnValue(original.copy().withStyle(style -> style.withColor(TextColor.fromRgb(color.getRgb()))));
        }
    }
}
