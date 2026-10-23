// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) <FILL IN: year> <FILL IN: author>

package io.github.lifesmp.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormatUtilsTest {

    @Test
    @DisplayName("Format duration produces exact Russian abbreviations for days, hours, minutes, and seconds")
    void testFormatDuration() {
        // Zero and negative seconds
        assertThat(FormatUtils.formatDuration(Duration.ZERO)).isEqualTo("0с");
        assertThat(FormatUtils.formatDuration(Duration.ofSeconds(-10))).isEqualTo("0с");
        assertThat(FormatUtils.formatDuration(0L)).isEqualTo("0с");
        assertThat(FormatUtils.formatDuration(-10L)).isEqualTo("0с");

        // Seconds only
        assertThat(FormatUtils.formatDuration(Duration.ofSeconds(45))).isEqualTo("45с");
        assertThat(FormatUtils.formatDuration(45L)).isEqualTo("45с");
        assertThat(FormatUtils.formatDuration(Duration.ofSeconds(1))).isEqualTo("1с");
        assertThat(FormatUtils.formatDuration(1L)).isEqualTo("1с");

        // Minutes and seconds
        assertThat(FormatUtils.formatDuration(Duration.ofSeconds(65))).isEqualTo("1м 5с");
        assertThat(FormatUtils.formatDuration(65L)).isEqualTo("1м 5с");
        assertThat(FormatUtils.formatDuration(Duration.ofMinutes(5))).isEqualTo("5м");
        assertThat(FormatUtils.formatDuration(300L)).isEqualTo("5м");

        // Hours and minutes
        assertThat(FormatUtils.formatDuration(Duration.ofHours(2).plusMinutes(30))).isEqualTo("2ч 30м");
        assertThat(FormatUtils.formatDuration(9000L)).isEqualTo("2ч 30м");
        assertThat(FormatUtils.formatDuration(Duration.ofHours(10))).isEqualTo("10ч");
        assertThat(FormatUtils.formatDuration(36000L)).isEqualTo("10ч");

        // Days, hours, and minutes
        Duration d1 = Duration.ofDays(1).plusHours(2).plusMinutes(5);
        assertThat(FormatUtils.formatDuration(d1)).isEqualTo("1д 2ч 5м");
        assertThat(FormatUtils.formatDuration(86400L + 7200L + 300L)).isEqualTo("1д 2ч 5м");

        // Days only
        assertThat(FormatUtils.formatDuration(Duration.ofDays(3))).isEqualTo("3д");
        assertThat(FormatUtils.formatDuration(3L * 86400L)).isEqualTo("3д");

        // Null duration throws NullPointerException
        assertThatThrownBy(() -> FormatUtils.formatDuration(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Format duration in hours and minutes produces Xч Yм format")
    void testFormatDurationHoursMinutes() {
        assertThat(FormatUtils.formatDurationHoursMinutes(Duration.ZERO)).isEqualTo("0ч 0м");
        assertThat(FormatUtils.formatDurationHoursMinutes(0L)).isEqualTo("0ч 0м");
        assertThat(FormatUtils.formatDurationHoursMinutes(-15L)).isEqualTo("0ч 0м");
        assertThat(FormatUtils.formatDurationHoursMinutes(Duration.ofMinutes(15))).isEqualTo("0ч 15м");
        assertThat(FormatUtils.formatDurationHoursMinutes(900L)).isEqualTo("0ч 15м");
        assertThat(FormatUtils.formatDurationHoursMinutes(Duration.ofHours(23).plusMinutes(45))).isEqualTo("23ч 45м");
        assertThat(FormatUtils.formatDurationHoursMinutes(23 * 3600L + 45 * 60L)).isEqualTo("23ч 45м");
        assertThat(FormatUtils.formatDurationHoursMinutes(Duration.ofHours(24))).isEqualTo("24ч 0м");
        assertThat(FormatUtils.formatDurationHoursMinutes(24 * 3600L)).isEqualTo("24ч 0м");

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
}
