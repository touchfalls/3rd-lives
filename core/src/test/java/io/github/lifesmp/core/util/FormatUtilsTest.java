// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormatUtilsTest {

    @Test
    @DisplayName("Format duration produces English abbreviations for days, hours, minutes, and seconds by default")
    void testFormatDuration() {
        // Zero and negative seconds
        assertThat(FormatUtils.formatDuration(Duration.ZERO)).isEqualTo("0s");
        assertThat(FormatUtils.formatDuration(Duration.ofSeconds(-10))).isEqualTo("0s");
        assertThat(FormatUtils.formatDuration(0L)).isEqualTo("0s");
        assertThat(FormatUtils.formatDuration(-10L)).isEqualTo("0s");

        // Seconds only
        assertThat(FormatUtils.formatDuration(Duration.ofSeconds(45))).isEqualTo("45s");
        assertThat(FormatUtils.formatDuration(45L)).isEqualTo("45s");
        assertThat(FormatUtils.formatDuration(Duration.ofSeconds(1))).isEqualTo("1s");
        assertThat(FormatUtils.formatDuration(1L)).isEqualTo("1s");

        // Minutes and seconds
        assertThat(FormatUtils.formatDuration(Duration.ofSeconds(65))).isEqualTo("1m 5s");
        assertThat(FormatUtils.formatDuration(65L)).isEqualTo("1m 5s");
        assertThat(FormatUtils.formatDuration(Duration.ofMinutes(5))).isEqualTo("5m");
        assertThat(FormatUtils.formatDuration(300L)).isEqualTo("5m");

        // Hours and minutes
        assertThat(FormatUtils.formatDuration(Duration.ofHours(2).plusMinutes(30))).isEqualTo("2h 30m");
        assertThat(FormatUtils.formatDuration(9000L)).isEqualTo("2h 30m");
        assertThat(FormatUtils.formatDuration(Duration.ofHours(10))).isEqualTo("10h");
        assertThat(FormatUtils.formatDuration(36000L)).isEqualTo("10h");

        // Days, hours, and minutes
        Duration d1 = Duration.ofDays(1).plusHours(2).plusMinutes(5);
        assertThat(FormatUtils.formatDuration(d1)).isEqualTo("1d 2h 5m");
        assertThat(FormatUtils.formatDuration(86400L + 7200L + 300L)).isEqualTo("1d 2h 5m");

        // Days only
        assertThat(FormatUtils.formatDuration(Duration.ofDays(3))).isEqualTo("3d");
        assertThat(FormatUtils.formatDuration(3L * 86400L)).isEqualTo("3d");

        // Null duration throws NullPointerException
        assertThatThrownBy(() -> FormatUtils.formatDuration(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Format duration in hours and minutes produces Xh Ym format by default")
    void testFormatDurationHoursMinutes() {
        assertThat(FormatUtils.formatDurationHoursMinutes(Duration.ZERO)).isEqualTo("0h 0m");
        assertThat(FormatUtils.formatDurationHoursMinutes(0L)).isEqualTo("0h 0m");
        assertThat(FormatUtils.formatDurationHoursMinutes(-15L)).isEqualTo("0h 0m");
        assertThat(FormatUtils.formatDurationHoursMinutes(Duration.ofMinutes(15))).isEqualTo("0h 15m");
        assertThat(FormatUtils.formatDurationHoursMinutes(900L)).isEqualTo("0h 15m");
        assertThat(FormatUtils.formatDurationHoursMinutes(Duration.ofHours(23).plusMinutes(45))).isEqualTo("23h 45m");
        assertThat(FormatUtils.formatDurationHoursMinutes(23 * 3600L + 45 * 60L)).isEqualTo("23h 45m");
        assertThat(FormatUtils.formatDurationHoursMinutes(Duration.ofHours(24))).isEqualTo("24h 0m");
        assertThat(FormatUtils.formatDurationHoursMinutes(24 * 3600L)).isEqualTo("24h 0m");

        assertThatThrownBy(() -> FormatUtils.formatDurationHoursMinutes(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Format hearts produces colored filled hearts and dark gray empty hearts")
    void testFormatHearts() {
        // 3 out of 3 lives with hex color
        String h3 = FormatUtils.formatHearts(3, 3, "#55FF55");
        assertThat(h3).isEqualTo("<color:#55FF55>❤❤❤</color>");

        // 2 out of 3 lives with hex color
        String h2 = FormatUtils.formatHearts(2, 3, "#FFAA00");
        assertThat(h2).isEqualTo("<color:#FFAA00>❤❤</color><dark_gray>❤</dark_gray>");

        // 1 out of 3 lives with tag
        String h1 = FormatUtils.formatHearts(1, 3, "<red>");
        assertThat(h1).isEqualTo("<red>❤</red><dark_gray>❤❤</dark_gray>");

        // 0 out of 3 lives
        String h0 = FormatUtils.formatHearts(0, 3, "#AAAAAA");
        assertThat(h0).isEqualTo("<dark_gray>❤❤❤</dark_gray>");

        // Over-filled lives capped at max
        String hOver = FormatUtils.formatHearts(5, 3, "green");
        assertThat(hOver).isEqualTo("<green>❤❤❤</green>");

        // Caching returns identical string
        String hCached = FormatUtils.formatHearts(2, 3, "#FFAA00");
        assertThat(hCached).isSameAs(h2);

        assertThatThrownBy(() -> FormatUtils.formatHearts(1, 3, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Format duration uses the given unit suffixes")
    void formatsWithCustomUnits() {
        FormatUtils.TimeUnits russian = new FormatUtils.TimeUnits("д", "ч", "м", "с");
        assertThat(FormatUtils.formatDuration(86400L + 7200L + 300L, russian)).isEqualTo("1д 2ч 5м");
        assertThat(FormatUtils.formatDuration(0L, russian)).isEqualTo("0с");
        assertThat(FormatUtils.formatDurationHoursMinutes(9000L, russian)).isEqualTo("2ч 30м");
    }
}
