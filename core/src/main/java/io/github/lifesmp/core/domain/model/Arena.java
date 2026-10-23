// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.core.domain.model;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Domain entity representing an arena region where normal Life SMP death penalties
 * and PvP restrictions are overridden.
 * <p>
 * In an active arena:
 * <ul>
 *     <li>Player deaths do not consume lives</li>
 *     <li>Red player PvP protection is disabled</li>
 *     <li>Red-on-red combat is permitted</li>
 * </ul>
 */
public final class Arena {

    private final String name;
    private final String world;
    private final double minX;
    private final double minY;
    private final double minZ;
    private final double maxX;
    private final double maxY;
    private final double maxZ;
    private volatile boolean enabled;

    /**
     * Constructs a new Arena with normalized boundary coordinates.
     *
     * @param name    unique name of the arena
     * @param world   world/dimension identifier
     * @param x1      first corner X
     * @param y1      first corner Y
     * @param z1      first corner Z
     * @param x2      second corner X
     * @param y2      second corner Y
     * @param z2      second corner Z
     * @param enabled whether the arena is currently active
     */
    public Arena(
            @NotNull String name,
            @NotNull String world,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            boolean enabled
    ) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.world = Objects.requireNonNull(world, "world cannot be null");
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
        this.enabled = enabled;
    }

    @NotNull
    public String getName() {
        return name;
    }

    @NotNull
    public String getWorld() {
        return world;
    }

    public double getMinX() {
        return minX;
    }

    public double getMinY() {
        return minY;
    }

    public double getMinZ() {
        return minZ;
    }

    public double getMaxX() {
        return maxX;
    }

    public double getMaxY() {
        return maxY;
    }

    public double getMaxZ() {
        return maxZ;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Determines whether the given spatial coordinate lies within this arena's bounds.
     *
     * @param worldName target world identifier
     * @param x         X coordinate
     * @param y         Y coordinate
     * @param z         Z coordinate
     * @return true if the coordinate falls within the bounds of this arena
     */
    public boolean contains(@NotNull String worldName, double x, double y, double z) {
        if (!this.world.equalsIgnoreCase(worldName)) {
            return false;
        }
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Arena arena = (Arena) o;
        return name.equalsIgnoreCase(arena.name);
    }

    @Override
    public int hashCode() {
        return name.toLowerCase().hashCode();
    }

    @Override
    public String toString() {
        return "Arena{" +
                "name='" + name + '\'' +
                ", world='" + world + '\'' +
                ", min=[" + minX + ", " + minY + ", " + minZ + "]" +
                ", max=[" + maxX + ", " + maxY + ", " + maxZ + "]" +
                ", enabled=" + enabled +
                '}';
    }
}
