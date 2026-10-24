// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.fabric.scoreboard;

import io.github.lifesmp.core.domain.model.PlayerColor;
import io.github.lifesmp.fabric.util.McCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.scores.PlayerTeam;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Manages scoreboard team identifiers, formatting prefixes, and color mappings
 * for Fabric player visuals. Also manages the zz_dead team to ensure banned players
 * are sorted to the bottom of the client tab-list with gray formatting.
 */
public final class FabricScoreboardManager {

    public static final String TEAM_GREEN = "life_3_green";
    public static final String TEAM_GOLD = "life_2_gold";
    public static final String TEAM_RED = "life_1_red";
    public static final String TEAM_GRAY = "life_0_gray";
    public static final String TEAM_DEAD = "zz_dead";

    private final Supplier<MinecraftServer> serverSupplier;

    public FabricScoreboardManager(@NotNull Supplier<MinecraftServer> serverSupplier) {
        this.serverSupplier = Objects.requireNonNull(serverSupplier, "serverSupplier cannot be null");
    }

    public FabricScoreboardManager() {
        this(() -> null);
    }

    /**
     * Obtains the team name corresponding to a player's remaining life count.
     *
     * @param lives life count
     * @return team name string
     */
    @NotNull
    public String getTeamName(int lives) {
        PlayerColor color = PlayerColor.fromLives(lives);
        return switch (color) {
            case GREEN -> TEAM_GREEN;
            case GOLD -> TEAM_GOLD;
            case RED -> TEAM_RED;
            case GRAY -> TEAM_GRAY;
        };
    }

    /**
     * Returns a colored dot prefix formatted with MiniMessage tags.
     *
     * @param lives life count
     * @return MiniMessage formatted prefix (e.g. "<green>● </green>")
     */
    @NotNull
    public String getFormattedPrefix(int lives) {
        PlayerColor color = PlayerColor.fromLives(lives);
        return switch (color) {
            case GREEN -> "<green>● </green>";
            case GOLD -> "<gold>● </gold>";
            case RED -> "<red>● </red>";
            case GRAY -> "<gray>● </gray>";
        };
    }

    /**
     * Adds an offline or online player name to the zz_dead scoreboard team.
     * Vanilla clients sort the tab list alphabetically by team name, placing zz_dead at the bottom.
     *
     * @param playerName player username
     */
    public void addToDeadTeam(@NotNull String playerName) {
        MinecraftServer server = serverSupplier.get();
        if (server == null) return;
        ServerScoreboard scoreboard = server.getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(TEAM_DEAD);
        if (team == null) {
            team = scoreboard.addPlayerTeam(TEAM_DEAD);
            McCompat.setGray(team);
            team.setDisplayName(Component.literal("Dead Players"));
        }
        scoreboard.addPlayerToTeam(playerName, team);
    }

    /**
     * Removes a player name from the zz_dead scoreboard team.
     *
     * @param playerName player username
     */
    public void removeFromDeadTeam(@NotNull String playerName) {
        MinecraftServer server = serverSupplier.get();
        if (server == null) return;
        ServerScoreboard scoreboard = server.getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(TEAM_DEAD);
        if (team != null) {
            scoreboard.removePlayerFromTeam(playerName, team);
        }
    }

    /**
     * Removes all members from the zz_dead team and unregisters the team on server shutdown.
     */
    public void clearAndRemoveDeadTeam() {
        MinecraftServer server = serverSupplier.get();
        if (server == null) return;
        ServerScoreboard scoreboard = server.getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(TEAM_DEAD);
        if (team != null) {
            scoreboard.removePlayerTeam(team);
        }
    }
}
