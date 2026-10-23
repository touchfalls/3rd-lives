// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.paper.scoreboard;

import io.github.lifesmp.core.domain.model.PlayerColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;

/**
 * Manages scoreboard teams for coloring nametags above player heads,
 * tab-list entries, and sorting dead entries to the bottom ("zz_dead").
 */
public final class PaperScoreboardManager {

    private static final String TEAM_GREEN = "life_3_green";
    private static final String TEAM_GOLD = "life_2_gold";
    private static final String TEAM_RED = "life_1_red";
    private static final String TEAM_GRAY = "life_0_gray";
    public static final String TEAM_DEAD = "zz_dead";

    private final Scoreboard scoreboard;

    public PaperScoreboardManager() {
        this.scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        ensureTeams();
    }

    private void ensureTeams() {
        registerOrUpdateTeam(TEAM_GREEN, NamedTextColor.GREEN, Component.text("● ", NamedTextColor.GREEN));
        registerOrUpdateTeam(TEAM_GOLD, NamedTextColor.GOLD, Component.text("● ", NamedTextColor.GOLD));
        registerOrUpdateTeam(TEAM_RED, NamedTextColor.RED, Component.text("● ", NamedTextColor.RED));
        registerOrUpdateTeam(TEAM_GRAY, NamedTextColor.GRAY, Component.text("● ", NamedTextColor.GRAY));
        ensureDeadTeam();
    }

    private void registerOrUpdateTeam(String name, NamedTextColor color, Component prefix) {
        Team team = scoreboard.getTeam(name);
        if (team == null) {
            team = scoreboard.registerNewTeam(name);
        }
        team.color(color);
        team.prefix(prefix);
    }

    public void ensureDeadTeam() {
        Team team = scoreboard.getTeam(TEAM_DEAD);
        if (team == null) {
            team = scoreboard.registerNewTeam(TEAM_DEAD);
        }
        team.color(NamedTextColor.GRAY);
    }

    public void addToDeadTeam(@NotNull String entry) {
        removeFromAllTeams(entry);
        Team team = scoreboard.getTeam(TEAM_DEAD);
        if (team != null && !team.hasEntry(entry)) {
            team.addEntry(entry);
        }
    }

    public void removeFromDeadTeam(@NotNull String entry) {
        Team team = scoreboard.getTeam(TEAM_DEAD);
        if (team != null) {
            team.removeEntry(entry);
        }
    }

    public void clearAndRemoveDeadTeam() {
        Team team = scoreboard.getTeam(TEAM_DEAD);
        if (team != null) {
            for (String entry : new HashSet<>(team.getEntries())) {
                team.removeEntry(entry);
            }
            team.unregister();
        }
    }

    /**
     * Updates player nametag and tab-list visuals according to life count.
     *
     * @param player online Bukkit player
     * @param lives  current lives
     */
    public void updatePlayerVisuals(@NotNull Player player, int lives) {
        PlayerColor playerColor = PlayerColor.fromLives(lives);
        NamedTextColor namedColor = switch (playerColor) {
            case GREEN -> NamedTextColor.GREEN;
            case GOLD -> NamedTextColor.GOLD;
            case RED -> NamedTextColor.RED;
            case GRAY -> NamedTextColor.GRAY;
        };

        String targetTeamName = switch (playerColor) {
            case GREEN -> TEAM_GREEN;
            case GOLD -> TEAM_GOLD;
            case RED -> TEAM_RED;
            case GRAY -> TEAM_GRAY;
        };

        Team team = scoreboard.getTeam(targetTeamName);
        if (team != null && !team.hasEntry(player.getName())) {
            removeFromAllTeams(player.getName());
            team.addEntry(player.getName());
        }

        // Apply Tab-list display name
        Component tabName = Component.text("● ", namedColor)
                .append(Component.text(player.getName(), namedColor));
        player.playerListName(tabName);
    }

    public void removeFromAllTeams(@NotNull String entry) {
        Team g = scoreboard.getTeam(TEAM_GREEN);
        Team o = scoreboard.getTeam(TEAM_GOLD);
        Team r = scoreboard.getTeam(TEAM_RED);
        Team gr = scoreboard.getTeam(TEAM_GRAY);
        Team dead = scoreboard.getTeam(TEAM_DEAD);

        if (g != null) g.removeEntry(entry);
        if (o != null) o.removeEntry(entry);
        if (r != null) r.removeEntry(entry);
        if (gr != null) gr.removeEntry(entry);
        if (dead != null) dead.removeEntry(entry);
    }
}
