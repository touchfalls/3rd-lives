// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.fabric.config;

import io.github.lifesmp.core.config.LifeConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class FabricConfigAdapterTest {

    @Test
    @DisplayName("FabricConfigAdapter parses bundled config.json with exact defaults matching config.yml")
    void parsesBundledConfigJson() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("config.json");
        assertThat(stream).isNotNull();

        LifeConfig config = FabricConfigAdapter.parse(
                new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8)
        );

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

        // Check messages
        assertThat(config.getRawMessage("pvp.red-cannot-attack-red"))
                .contains("You cannot attack while you have 1 life");
        assertThat(config.getRawMessage("pvp.cannot-attack-red"))
                .contains("You cannot attack a player who has 1 life");
    }

    @Test
    @DisplayName("FabricConfigAdapter selects the language bundle and applies message overrides on top of it")
    void appliesLanguageAndOverrides() {
        String json = """
                {
                  "language": "es",
                  "messages": { "gift.no-pending": "custom text" }
                }
                """;

        LifeConfig config = FabricConfigAdapter.parse(new StringReader(json));

        assertThat(config.getRawMessage("gift.no-pending")).isEqualTo("custom text");
        assertThat(config.getRawMessage("gift.expired")).contains("La solicitud de regalo de vida ha caducado");
        assertThat(config.formatDuration(java.time.Duration.ofMinutes(90))).isEqualTo("1h 30m");
    }
}
