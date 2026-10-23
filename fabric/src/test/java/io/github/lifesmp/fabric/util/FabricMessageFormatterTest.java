// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) <FILL IN: year> <FILL IN: author>

package io.github.lifesmp.fabric.util;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FabricMessageFormatterTest {

    @Test
    @DisplayName("Parse plain text returns literal component")
    void parsePlainText() {
        Component comp = FabricMessageFormatter.parse("Hello, world!");
        assertThat(comp.getString()).isEqualTo("Hello, world!");
    }

    @Test
    @DisplayName("Parse tags extracts clean text without raw tags in output")
    void parseTags() {
        Component comp = FabricMessageFormatter.parse("<green>Hello</green> <gold>Life</gold> <red>SMP</red>");
        assertThat(comp.getString()).isEqualTo("Hello Life SMP");
    }

    @Test
    @DisplayName("Parse hex color and nested formatting tags")
    void parseHexAndStyle() {
        Component comp = FabricMessageFormatter.parse("<#55FF55><bold>BOLD GREEN</bold></#55FF55>");
        assertThat(comp.getString()).isEqualTo("BOLD GREEN");
    }

    @Test
    @DisplayName("Handle null and empty input safely")
    void handleNullAndEmpty() {
        assertThat(FabricMessageFormatter.parse(null).getString()).isEmpty();
        assertThat(FabricMessageFormatter.parse("").getString()).isEmpty();
    }
}
