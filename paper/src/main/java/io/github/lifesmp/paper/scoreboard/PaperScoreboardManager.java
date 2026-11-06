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
import java.util.UUID;
import java.util.function.Function;

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

    /** Teams of hunted players carry this suffix after the base team name. */
    private static final String HUNTED_SUFFIX = "_b";

    private final Scoreboard scoreboard;
    // Bounty icon of a player, or null when nobody hunts them
    private volatile Function<UUID, String> huntedIcon = uuid -> null;

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

    public void setHuntedIcon(@NotNull Function<UUID, String> huntedIcon) {
        this.huntedIcon = huntedIcon;
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

        String icon = huntedIcon.apply(player.getUniqueId());
        Component suffix = icon == null ? Component.empty() : Component.text(" " + icon, NamedTextColor.DARK_RED);
        Team team;
        if (icon == null) {
            team = scoreboard.getTeam(targetTeamName);
        } else {
            // A separate team per color, because a suffix applies to the whole team
            String huntedName = targetTeamName + HUNTED_SUFFIX;
            registerOrUpdateTeam(huntedName, namedColor, Component.text("● ", namedColor));
            team = scoreboard.getTeam(huntedName);
            team.suffix(suffix);
        }
        if (team != null && !team.hasEntry(player.getName())) {
            removeFromAllTeams(player.getName());
            team.addEntry(player.getName());
        }

        // Apply Tab-list display name
        Component tabName = Component.text("● ", namedColor)
                .append(Component.text(player.getName(), namedColor))
                .append(suffix);
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
        for (String base : new String[]{TEAM_GREEN, TEAM_GOLD, TEAM_RED, TEAM_GRAY}) {
            Team hunted = scoreboard.getTeam(base + HUNTED_SUFFIX);
            if (hunted != null) hunted.removeEntry(entry);
        }
    }
}
