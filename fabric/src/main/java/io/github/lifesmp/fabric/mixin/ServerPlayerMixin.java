// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) <FILL IN: year> <FILL IN: author>

package io.github.lifesmp.fabric.mixin;

import io.github.lifesmp.core.domain.model.LifeProfile;
import io.github.lifesmp.core.domain.model.PlayerColor;
import io.github.lifesmp.fabric.LifeSmpFabricServer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.time.Instant;
import java.util.UUID;

/**
 * Mixin targeting {@link ServerPlayer} to customize tab-list display name
 * and hook player death into Life SMP mechanics.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    /**
     * Intercepts getTabListDisplayName() to render player names with life colors in the Tab list.
     * Called directly by ClientboundPlayerInfoUpdatePacket when preparing tab entries.
     */
    @Inject(method = "getTabListDisplayName", at = @At("RETURN"), cancellable = true)
    private void onGetTabListDisplayName(CallbackInfoReturnable<Component> cir) {
        LifeSmpFabricServer server = LifeSmpFabricServer.getInstance();
        if (server == null) return;

        ServerPlayer self = (ServerPlayer) (Object) this;
        LifeProfile profile = server.getLifeEngine().getProfileCache()
                .getCachedProfile(self.getUUID()).orElse(null);
        int lives = profile != null ? profile.getLives() : server.getLifeConfig().getMaxLives();
        PlayerColor color = PlayerColor.fromLives(lives);

        Component tabComponent = Component.literal(self.getScoreboardName())
                .withStyle(style -> style.withColor(TextColor.fromRgb(color.getRgb())));
        cir.setReturnValue(tabComponent);
    }

    /**
     * Intercepts player death to execute Life SMP death processing,
     * deducting lives, enforcing bans on 0 lives, and checking arena protection.
     */
    @Inject(method = "die", at = @At("HEAD"))
    private void onPlayerDie(DamageSource damageSource, CallbackInfo ci) {
        LifeSmpFabricServer server = LifeSmpFabricServer.getInstance();
        if (server == null) return;

        ServerPlayer self = (ServerPlayer) (Object) this;
        String worldName = self.level().dimension().location().toString();
        UUID killerUuid = damageSource.getEntity() instanceof ServerPlayer killer
                ? killer.getUUID()
                : null;

        server.getLifeEngine().handlePlayerDeath(
                self.getUUID(),
                self.getGameProfile().getName(),
                worldName,
                self.getX(),
                self.getY(),
                self.getZ(),
                killerUuid,
                Instant.now(),
                server.getLifeConfig()
        );
    }
}
