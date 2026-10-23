// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) <FILL IN: year> <FILL IN: author>

package io.github.lifesmp.core.domain.model;

import org.jetbrains.annotations.NotNull;

/**
 * Standard colors assigned to players based on their remaining lives.
 * <ul>
 *     <li>3 lives: Green (#55FF55)</li>
 *     <li>2 lives: Gold (#FFAA00)</li>
 *     <li>1 life: Red (#FF5555)</li>
 *     <li>0 or less: Gray (#AAAAAA)</li>
 * </ul>
 */
public enum PlayerColor {

    GREEN(3, "#55FF55", 0x55FF55, "green"),
    GOLD(2, "#FFAA00", 0xFFAA00, "gold"),
    RED(1, "#FF5555", 0xFF5555, "red"),
    GRAY(0, "#AAAAAA", 0xAAAAAA, "gray");

    private final int lives;
    private final String hex;
    private final int rgb;
    private final String tagName;

    PlayerColor(int lives, @NotNull String hex, int rgb, @NotNull String tagName) {
        this.lives = lives;
        this.hex = hex;
        this.rgb = rgb;
        this.tagName = tagName;
    }

    public int getLives() {
        return lives;
    }

    @NotNull
    public String getHex() {
        return hex;
    }

    public int getRgb() {
        return rgb;
    }

    @NotNull
    public String getTagName() {
        return tagName;
    }

    /**
     * Resolves the PlayerColor for a specific life count.
     *
     * @param lives count of remaining lives
     * @return corresponding PlayerColor
     */
    @NotNull
    public static PlayerColor fromLives(int lives) {
        if (lives >= 3) {
            return GREEN;
        } else if (lives == 2) {
            return GOLD;
        } else if (lives == 1) {
            return RED;
        } else {
            return GRAY;
        }
    }
}
