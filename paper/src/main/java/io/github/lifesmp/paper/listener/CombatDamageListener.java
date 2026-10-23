// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.paper.listener;

import io.github.lifesmp.core.config.LifeConfig;
import io.github.lifesmp.core.domain.model.LifeProfile;
import io.github.lifesmp.core.domain.model.PvPDecision;
import io.github.lifesmp.core.domain.service.LifeEngine;
import io.github.lifesmp.paper.util.AttackerAttributor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Event listener intercepting and enforcing PvP rules across direct and indirect damage channels.
 */
public final class CombatDamageListener implements Listener {

    private final LifeEngine lifeEngine;
    private final AttackerAttributor attackerAttributor;
    private final Supplier<LifeConfig> configSupplier;

    public CombatDamageListener(
            @NotNull LifeEngine lifeEngine,
            @NotNull AttackerAttributor attackerAttributor,
            @NotNull Supplier<LifeConfig> configSupplier
    ) {
        this.lifeEngine = Objects.requireNonNull(lifeEngine, "lifeEngine cannot be null");
        this.attackerAttributor = Objects.requireNonNull(attackerAttributor, "attackerAttributor cannot be null");
        this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier cannot be null");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCrystalDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof EnderCrystal crystal) {
            Player striker = attackerAttributor.resolveAttacker(event.getDamager());
            if (striker != null) {
                attackerAttributor.recordCrystalTrigger(crystal.getUniqueId(), striker.getUniqueId());
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCombatDamage(EntityDamageByEntityEvent event) {
        Entity victimEntity = event.getEntity();
        Player victimPlayer = resolveVictimPlayer(victimEntity);
        if (victimPlayer == null) {
            return;
        }

        Player attackerPlayer = attackerAttributor.resolveAttacker(event.getDamager());
        if (attackerPlayer == null) {
            return;
        }

        // Ignore self damage
        if (victimPlayer.getUniqueId().equals(attackerPlayer.getUniqueId())) {
            return;
        }

        Instant now = Instant.now();
        LifeConfig config = configSupplier.get();
        Location loc = victimPlayer.getLocation();

        boolean inActiveArena = lifeEngine.getArenaService().isInsideActiveArena(
                loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ()
        );
        boolean globalOverride = lifeEngine.getArenaService().isGlobalPvpOverrideActive(now);

        LifeProfile attackerProfile = lifeEngine.getProfileCache()
                .getCachedProfile(attackerPlayer.getUniqueId()).orElse(null);
        LifeProfile victimProfile = lifeEngine.getProfileCache()
                .getCachedProfile(victimPlayer.getUniqueId()).orElse(null);

        PvPDecision decision = lifeEngine.getPvpPolicyEngine().evaluate(
                attackerPlayer.getUniqueId(),
                attackerProfile,
                victimPlayer.getUniqueId(),
                victimProfile,
                inActiveArena,
                globalOverride,
                now,
                config
        );

        if (!decision.allowed()) {
            event.setCancelled(true);
            if (decision.message() != null && !decision.message().isBlank()) {
                String formatted = decision.message().replace("{prefix}", config.getPrefix());
                attackerPlayer.sendActionBar(MiniMessage.miniMessage().deserialize(formatted));
            }
            return;
        }

        // Attack allowed: track recent combat strike for kill attribution
        attackerAttributor.recordRecentCombatStrike(victimPlayer.getUniqueId(), attackerPlayer.getUniqueId());
    }

    @Nullable
    private Player resolveVictimPlayer(@NotNull Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        // Friendly fire on tamed pets also obeys player PvP rules
        if (entity instanceof Tameable tameable && tameable.isTamed()) {
            AnimalTamer owner = tameable.getOwner();
            if (owner instanceof Player playerOwner) {
                return playerOwner;
            }
        }
        return null;
    }
}
