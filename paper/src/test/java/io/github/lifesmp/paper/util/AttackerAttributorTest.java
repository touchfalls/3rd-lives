// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.paper.util;

import org.bukkit.entity.*;
import org.bukkit.projectiles.ProjectileSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

class AttackerAttributorTest {

    private AttackerAttributor attributor;

    @BeforeEach
    void setUp() {
        attributor = new AttackerAttributor();
    }

    @SuppressWarnings("unchecked")
    private <T> T createProxy(Class<T> iface, java.util.Map<String, Object> returnValues) {
        return (T) Proxy.newProxyInstance(
                iface.getClassLoader(),
                new Class<?>[]{iface},
                (proxy, method, args) -> {
                    if (returnValues.containsKey(method.getName())) {
                        return returnValues.get(method.getName());
                    }
                    if (method.getName().equals("equals")) {
                        return proxy == (args != null && args.length > 0 ? args[0] : null);
                    }
                    if (method.getName().equals("hashCode")) {
                        return System.identityHashCode(proxy);
                    }
                    if (method.getName().equals("toString")) {
                        return "Proxy<" + iface.getSimpleName() + ">";
                    }
                    Class<?> returnType = method.getReturnType();
                    if (returnType == boolean.class) return false;
                    if (returnType == int.class) return 0;
                    if (returnType == long.class) return 0L;
                    if (returnType == double.class) return 0.0;
                    return null;
                }
        );
    }

    @Test
    @DisplayName("Direct Player attacker resolves immediately")
    void directPlayerResolves() {
        Player player = createProxy(Player.class, java.util.Map.of());
        Player resolved = attributor.resolveAttacker(player);
        assertThat(resolved).isSameAs(player);
    }

    @Test
    @DisplayName("Projectile shot by Player resolves to shooting Player")
    void projectileAttackerResolves() {
        Player shooter = createProxy(Player.class, java.util.Map.of());
        Arrow arrow = createProxy(Arrow.class, java.util.Map.of("getShooter", (ProjectileSource) shooter));

        Player resolved = attributor.resolveAttacker(arrow);
        assertThat(resolved).isSameAs(shooter);
    }

    @Test
    @DisplayName("Tamed pet owned by Player resolves to owner Player")
    void tamedPetResolvesToOwner() {
        Player owner = createProxy(Player.class, java.util.Map.of());
        Wolf wolf = createProxy(Wolf.class, java.util.Map.of(
                "isTamed", true,
                "getOwner", (AnimalTamer) owner
        ));

        Player resolved = attributor.resolveAttacker(wolf);
        assertThat(resolved).isSameAs(owner);
    }

    @Test
    @DisplayName("Primed TNT primed by Player resolves to player source")
    void primedTntResolvesToSource() {
        Player source = createProxy(Player.class, java.util.Map.of());
        TNTPrimed tnt = createProxy(TNTPrimed.class, java.util.Map.of("getSource", (Entity) source));

        Player resolved = attributor.resolveAttacker(tnt);
        assertThat(resolved).isSameAs(source);
    }

    @Test
    @DisplayName("AreaEffectCloud created by Player resolves to player source")
    void areaEffectCloudResolvesToSource() {
        Player source = createProxy(Player.class, java.util.Map.of());
        AreaEffectCloud cloud = createProxy(AreaEffectCloud.class, java.util.Map.of("getSource", (ProjectileSource) source));

        Player resolved = attributor.resolveAttacker(cloud);
        assertThat(resolved).isSameAs(source);
    }
}
