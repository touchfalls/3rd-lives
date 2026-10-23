// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.fabric.scoreboard;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.scores.PlayerTeam;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FabricScoreboardManagerTest {

    @Test
    @DisplayName("Verify team names and prefix mappings for all life values")
    void teamMappings() {
        FabricScoreboardManager manager = new FabricScoreboardManager();

        assertThat(manager.getTeamName(3)).isEqualTo(FabricScoreboardManager.TEAM_GREEN);
        assertThat(manager.getTeamName(2)).isEqualTo(FabricScoreboardManager.TEAM_GOLD);
        assertThat(manager.getTeamName(1)).isEqualTo(FabricScoreboardManager.TEAM_RED);
        assertThat(manager.getTeamName(0)).isEqualTo(FabricScoreboardManager.TEAM_GRAY);

        assertThat(manager.getFormattedPrefix(3)).contains("<green>");
        assertThat(manager.getFormattedPrefix(2)).contains("<gold>");
        assertThat(manager.getFormattedPrefix(1)).contains("<red>");
        assertThat(manager.getFormattedPrefix(0)).contains("<gray>");
        assertThat(FabricScoreboardManager.TEAM_DEAD).isEqualTo("zz_dead");
    }

    @Test
    @DisplayName("addToDeadTeam creates team if missing and adds player")
    void addToDeadTeam() {
        MinecraftServer server = mock(MinecraftServer.class);
        ServerScoreboard scoreboard = mock(ServerScoreboard.class);
        when(server.getScoreboard()).thenReturn(scoreboard);
        when(scoreboard.getPlayerTeam(FabricScoreboardManager.TEAM_DEAD)).thenReturn(null);

        PlayerTeam team = mock(PlayerTeam.class);
        when(scoreboard.addPlayerTeam(FabricScoreboardManager.TEAM_DEAD)).thenReturn(team);

        FabricScoreboardManager manager = new FabricScoreboardManager(() -> server);
        manager.addToDeadTeam("DeadPlayer");

        verify(scoreboard).addPlayerTeam(FabricScoreboardManager.TEAM_DEAD);
        verify(scoreboard).addPlayerToTeam("DeadPlayer", team);
    }

    @Test
    @DisplayName("removeFromDeadTeam removes player from team")
    void removeFromDeadTeam() {
        MinecraftServer server = mock(MinecraftServer.class);
        ServerScoreboard scoreboard = mock(ServerScoreboard.class);
        when(server.getScoreboard()).thenReturn(scoreboard);

        PlayerTeam team = mock(PlayerTeam.class);
        when(scoreboard.getPlayerTeam(FabricScoreboardManager.TEAM_DEAD)).thenReturn(team);

        FabricScoreboardManager manager = new FabricScoreboardManager(() -> server);
        manager.removeFromDeadTeam("DeadPlayer");

        verify(scoreboard).removePlayerFromTeam("DeadPlayer", team);
    }

    @Test
    @DisplayName("clearAndRemoveDeadTeam deletes team from scoreboard")
    void clearAndRemoveDeadTeam() {
        MinecraftServer server = mock(MinecraftServer.class);
        ServerScoreboard scoreboard = mock(ServerScoreboard.class);
        when(server.getScoreboard()).thenReturn(scoreboard);

        PlayerTeam team = mock(PlayerTeam.class);
        when(scoreboard.getPlayerTeam(FabricScoreboardManager.TEAM_DEAD)).thenReturn(team);

        FabricScoreboardManager manager = new FabricScoreboardManager(() -> server);
        manager.clearAndRemoveDeadTeam();

        verify(scoreboard).removePlayerTeam(team);
    }
}
