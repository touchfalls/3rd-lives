// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) <FILL IN: year> <FILL IN: author>

package io.github.lifesmp.paper.config;

import io.github.lifesmp.core.config.LifeConfig;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class PaperConfigAdapterTest {

    @Test
    @DisplayName("PaperConfigAdapter loads bundled config.yml with exact default values and messages")
    void loadsBundledConfigYml() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("config.yml");
        assertThat(stream).isNotNull();

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8)
        );

        LifeConfig config = PaperConfigAdapter.load(yaml);

        assertThat(config.getPrefix()).isEqualTo("<dark_gray>[<gradient:#55FF55:#FFAA00>Life</gradient>]</dark_gray> ");
        assertThat(config.getMaxLives()).isEqualTo(3);
        assertThat(config.getBanHours()).isEqualTo(24.0);
        assertThat(config.getBanSeconds()).isEqualTo(86400L);
        assertThat(config.getRestoredLivesAfterBan()).isEqualTo(3);
        assertThat(config.getRegenHoursPerLife()).isEqualTo(10.0);
        assertThat(config.getRegenSecondsPerLife()).isEqualTo(36000L);
        assertThat(config.getGiftRegenHoursPerLife()).isEqualTo(2.5);
        assertThat(config.getGiftRegenSecondsPerLife()).isEqualTo(9000L);
        assertThat(config.getAfkMinutes()).isEqualTo(180.0);
        assertThat(config.getAfkSeconds()).isEqualTo(10800L);
        assertThat(config.getRetaliationWindowSeconds()).isEqualTo(30L);
        assertThat(config.getGiftRequestTimeoutSeconds()).isEqualTo(60L);
        assertThat(config.getAutosaveMinutes()).isEqualTo(5.0);
        assertThat(config.getAutosaveSeconds()).isEqualTo(300L);

        // Check dead-tab configuration
        assertThat(config.isDeadTabEnabled()).isTrue();
        assertThat(config.getDeadTabFormat()).isEqualTo("<gray>☠ {player}</gray> <dark_gray>[DEAD]</dark_gray>");
        assertThat(config.getDeadTabColor()).isEqualTo("#AAAAAA");
        assertThat(config.isDeadTabShowTime()).isFalse();
        assertThat(config.getDeadTabMaxEntries()).isEqualTo(50);
        assertThat(config.isDeadTabSortLast()).isTrue();

        // Check colors
        assertThat(config.getColorHex(3)).isEqualTo("#55FF55");
        assertThat(config.getColorHex(2)).isEqualTo("#FFAA00");
        assertThat(config.getColorHex(1)).isEqualTo("#FF5555");
        assertThat(config.getColorHex(0)).isEqualTo("#AAAAAA");

        // Check key messages
        assertThat(config.getRawMessage("pvp.red-cannot-attack-red"))
                .contains("Вы не можете нападать, пока у вас 1 жизнь");
        assertThat(config.getRawMessage("pvp.cannot-attack-red"))
                .contains("Вы не можете нападать на игрока с 1 жизнью");
    }
}
